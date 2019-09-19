package net.corda.notaryhealthcheck.cordapp

import co.paralleluniverse.fibers.Suspendable
import com.codahale.metrics.MetricRegistry
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.core.node.NotaryInfo
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.config.FlowTimeoutConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.transactions.NonValidatingNotaryFlow
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.notaryhealthcheck.utils.Monitorable
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.GlobalDatabaseRule
import net.corda.testing.internal.LogHelper
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.internal.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.security.PublicKey
import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BackpressureHandlingTestRule(val clusterSize: Int) : ExternalResource() {

    lateinit var mockNet: InternalMockNetwork
    lateinit var notary: Party
    lateinit var node: TestStartedNode

    private fun startClusterAndNode(mockNet: InternalMockNetwork): Pair<Party, TestStartedNode> {
        val replicaIds = (0 until clusterSize)
        val serviceLegalName = CordaX500Name("Custom Notary", "Zurich", "CH")
        val notaryIdentity = DevIdentityGenerator.generateDistributedNotaryCompositeIdentity(
                replicaIds.map { mockNet.baseDirectory(mockNet.nextNodeId + it) },
                serviceLegalName)

        val networkParameters = NetworkParametersCopier(testNetworkParameters(listOf(NotaryInfo(notaryIdentity, false))))
        val notaryConfig = NotaryConfig(
                serviceLegalName = serviceLegalName,
                validating = false,
                className = BackpressureHandlingTest.TestNotaryService::class.java.name
        )

        val notaryNodes = (0 until clusterSize).map {
            mockNet.createUnstartedNode(InternalMockNodeParameters(configOverrides = {
                doReturn(notaryConfig).whenever(it).notary
            }))
        }

        val aliceNode = mockNet.createUnstartedNode(
                InternalMockNodeParameters(
                        legalName = CordaX500Name("Alice", "AliceCorp", "GB"),
                        configOverrides = { conf: NodeConfiguration ->
                            val retryConfig = FlowTimeoutConfiguration(BackpressureHandlingTest.defaultFlowTimeout, 3, 1.0)
                            doReturn(retryConfig).whenever(conf).flowTimeout
                        }
                )
        )

        // MockNetwork doesn't support notary clusters, so we create all the nodes we need unstarted, and then install the
        // network-parameters in their directories before they're started.
        val nodes = (notaryNodes + aliceNode).map { node ->
            networkParameters.install(mockNet.baseDirectory(node.id))
            node.start()
        }

        return Pair(notaryIdentity, nodes.last())
    }

    override fun before() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(
                        findCordapp("net.corda.notaryhealthcheck.contract"),
                        findCordapp("net.corda.notaryhealthcheck.cordapp"),
                        cordappForClasses(BackpressureHandlingTest.TestNotaryService::class.java)
                ),
                servicePeerAllocationStrategy = RoundRobin(),
                threadPerNode = true
        )
        val started = startClusterAndNode(mockNet)
        notary = started.first
        node = started.second
    }

    override fun after() {
        mockNet.stopNodes()
    }
}

class BackpressureHandlingTest {
    companion object {
        /** A shared counter across all notary service nodes. */
        var requestsReceived: AtomicInteger = AtomicInteger(0)

        private val waitEtaThreshold: Duration = NotaryServiceFlow.defaultEstimatedWaitTime
        private var waitETA: Duration = waitEtaThreshold

        val defaultFlowTimeout = 5.seconds

        private val notary by lazy { globalRule.notary }
        private val node by lazy { globalRule.node }

        init {
            LogHelper.setLevel("+net.corda.flow", "+net.corda.testing.node", "+net.corda.node.services.messaging")
        }

        /** node_0 for default notary created by mock network + alice + cluster size = 5 */
        private val globalDatabaseRule = GlobalDatabaseRule(listOf("node_0", "node_1", "node_2", "node_3"))

        /** The notary nodes don't run any consensus protocol, so 2 nodes are sufficient for the purpose of this test. */
        private val globalRule = BackpressureHandlingTestRule(2)

        @ClassRule
        @JvmField
        val ruleChain = RuleChain.outerRule(globalDatabaseRule).around(globalRule)
    }

    @Before
    fun resetCounter() {
        requestsReceived = AtomicInteger(0)
    }

    @Test
    fun `health check flows don't get restarted`() {
        node.run {
            val flow = HealthCheckFlow(Monitorable(notary, notary))
            val progressTracker = flow.progressTracker
            assertNotEquals(ProgressTracker.DONE, progressTracker.currentStep)
            getDoneFuture(progressTracker)

            val resultFuture = services.startFlow(flow).resultFuture
            var exceptionThrown = false
            try {
                resultFuture.get(7, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                exceptionThrown = true
            }
            assertTrue(exceptionThrown, "Health check flow timed out unexpectedly")
        }
    }

    @Test
    fun `health check flows can update their ETA and report on it`() {
        try {
            waitETA = 10.minutes
            node.run {
                val flow = HealthCheckFlow(Monitorable(notary, notary))
                val progressTracker = flow.progressTracker
                assertNotEquals(ProgressTracker.DONE, progressTracker.currentStep)
                val progressTrackerDone = getDoneFuture(progressTracker)

                val resultFuture = services.startFlow(flow).resultFuture
                var exceptionThrown = false
                try {
                    resultFuture.get(7, TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    exceptionThrown = true
                }
                assertTrue(exceptionThrown)
                flow.stateMachine.updateTimedFlowTimeout(5)
                resultFuture.get(20, TimeUnit.SECONDS)
                progressTrackerDone.get()

                val metricName = MetricRegistry.name(Metrics.reportedWaitTimeSeconds(notary.metricPrefix()))
                val metricsRegistry = (flow.serviceHub as ServiceHubInternal).monitoringService.metrics
                val gauge = metricsRegistry.gauges[metricName]
                assertNotNull(gauge)
                assertEquals(waitETA.seconds, (gauge as HealthCheckFlow.NotaryClientFlow.WaitTimeLatchedGauge).currentWaitTime.get())
            }
        } finally {
            waitETA = waitEtaThreshold
        }
    }

    /** Returns a future that completes when the [progressTracker] reaches the [ProgressTracker.DONE] step. */
    private fun getDoneFuture(progressTracker: ProgressTracker): Future<ProgressTracker.Change> {
        return progressTracker.changes.takeFirst {
            it.progressTracker.currentStep == ProgressTracker.DONE
        }.bufferUntilSubscribed().toBlocking().toFuture()
    }

    /**
     * A test notary service that will just stop forever the first time you invoke its commitInputStates method and will succeed the
     * second time around.
     */
    class TestNotaryService(override val services: ServiceHubInternal, override val notaryIdentityKey: PublicKey) : SinglePartyNotaryService() {
        override val uniquenessProvider = object : UniquenessProvider {
            /** A dummy commit method that immediately returns a success message. */
            override fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party, requestSignature: NotarisationRequestSignature, timeWindow: TimeWindow?, references: List<StateRef>): CordaFuture<UniquenessProvider.Result> {
                return openFuture<UniquenessProvider.Result>().apply {
                    set(UniquenessProvider.Result.Success)
                }
            }

            override fun getEta(numStates: Int): Duration = waitETA
        }

        @Suspendable
        override fun commitInputStates(inputs: List<StateRef>, txId: SecureHash, caller: Party, requestSignature: NotarisationRequestSignature, timeWindow: TimeWindow?, references: List<StateRef>) {
            val callingFlow = FlowLogic.currentTopLevel
                    ?: throw IllegalStateException("This method should be invoked in a flow context.")

            if (requestsReceived.getAndIncrement() == 0) {
                log.info("Ignoring")
                // Waiting forever
                callingFlow.stateMachine.suspend(FlowIORequest.WaitForLedgerCommit(SecureHash.randomSHA256()), false)
            } else {
                log.info("Processing")
                super.commitInputStates(inputs, txId, caller, requestSignature, timeWindow, references)
            }
        }

        override fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?> = NonValidatingNotaryFlow(otherPartySession, this, waitEtaThreshold)
        override fun start() {}
        override fun stop() {}
    }
}
