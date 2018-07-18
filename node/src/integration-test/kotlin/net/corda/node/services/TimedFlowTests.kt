package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.TrustedAuthorityNotaryService
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.core.node.AppServiceHub
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.CordaService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.FlowTimeoutConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.LogHelper
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.internal.DriverDSLImpl
import net.corda.testing.node.internal.DriverDSLImpl.Companion.defaultTestCorDappsForAllNodes
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.startFlow
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.slf4j.MDC
import java.security.PublicKey
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertNotEquals

class TimedFlowTests {
    companion object {
        /** The notary nodes don't run any consensus protocol, so 2 nodes are sufficient for the purpose of this test. */
        private const val CLUSTER_SIZE = 2
        /** A shared counter across all notary service nodes. */
        var requestsReceived: AtomicInteger = AtomicInteger(0)

        private lateinit var mockNet: InternalMockNetwork
        private lateinit var notary: Party
        private lateinit var node: StartedNode<InternalMockNetwork.MockNode>

        init {
            LogHelper.setLevel("+net.corda.flow", "+net.corda.testing.node", "+net.corda.node.services.messaging")
        }

        @BeforeClass
        @JvmStatic
        fun setup() {
            mockNet = InternalMockNetwork(
                    cordappsForAllNodes = defaultTestCorDappsForAllNodes(setOf("net.corda.testing.contracts", "net.corda.node.services")),
                    defaultParameters = MockNetworkParameters().withServicePeerAllocationStrategy(InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin()),
                    threadPerNode = true
            )
            val started = startClusterAndNode(mockNet)
            notary = started.first
            node = started.second
        }

        @AfterClass
        @JvmStatic
        fun stopNodes() {
            mockNet.stopNodes()
        }

        private fun startClusterAndNode(mockNet: InternalMockNetwork): Pair<Party, StartedNode<InternalMockNetwork.MockNode>> {
            val replicaIds = (0 until CLUSTER_SIZE)
            val notaryIdentity = DevIdentityGenerator.generateDistributedNotaryCompositeIdentity(
                    replicaIds.map { mockNet.baseDirectory(mockNet.nextNodeId + it) },
                    CordaX500Name("Custom Notary", "Zurich", "CH"))

            val networkParameters = NetworkParametersCopier(testNetworkParameters(listOf(NotaryInfo(notaryIdentity, true))))
            val notaryConfig = mock<NotaryConfig> {
                whenever(it.custom).thenReturn(true)
                whenever(it.isClusterConfig).thenReturn(true)
                whenever(it.validating).thenReturn(true)
            }

            val notaryNodes = (0 until CLUSTER_SIZE).map {
                mockNet.createUnstartedNode(InternalMockNodeParameters(configOverrides = {
                    doReturn(notaryConfig).whenever(it).notary
                }))
            }

            val aliceNode = mockNet.createUnstartedNode(
                    InternalMockNodeParameters(
                            legalName = CordaX500Name("Alice", "AliceCorp", "GB"),
                            configOverrides = { conf: NodeConfiguration ->
                                val retryConfig = FlowTimeoutConfiguration(1.seconds, 3, 1.0)
                                doReturn(retryConfig).whenever(conf).flowTimeout
                            }
                    )
            )

            // MockNetwork doesn't support notary clusters, so we create all the nodes we need unstarted, and then install the
            // network-parameters in their directories before they're started.
            val node = (notaryNodes + aliceNode).map { node ->
                networkParameters.install(mockNet.baseDirectory(node.id))
                node.start()
            }.last()

            return Pair(notaryIdentity, node)
        }
    }

    @Before
    fun resetCounter() {
        requestsReceived = AtomicInteger(0)
    }

    @Test
    fun `timed flows are restarted`() {
        node.run {
            val issueTx = signInitialTransaction(notary) {
                setTimeWindow(services.clock.instant(), 30.seconds)
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            val flow = NotaryFlow.Client(issueTx)
            val progressTracker = flow.progressTracker
            assertNotEquals(ProgressTracker.DONE, progressTracker.currentStep)
            val progressTrackerDone = getDoneFuture(progressTracker)

            val notarySignatures = services.startFlow(flow).resultFuture.get()
            (issueTx + notarySignatures).verifyRequiredSignatures()
            progressTrackerDone.get()
        }
    }

    @Test
    fun `timed sub-flows are restarted`() {
        node.run {
            val issueTx = signInitialTransaction(notary) {
                setTimeWindow(services.clock.instant(), 30.seconds)
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            val flow = FinalityFlow(issueTx)
            val progressTracker = flow.progressTracker
            assertNotEquals(ProgressTracker.DONE, progressTracker.currentStep)
            val progressTrackerDone = getDoneFuture(flow.progressTracker)

            val stx = services.startFlow(flow).resultFuture.get()
            stx.verifyRequiredSignatures()
            progressTrackerDone.get()
        }
    }

    private fun StartedNode<InternalMockNetwork.MockNode>.signInitialTransaction(notary: Party, block: TransactionBuilder.() -> Any?): SignedTransaction {
        return services.signInitialTransaction(
                TransactionBuilder(notary).apply {
                    addCommand(dummyCommand(services.myInfo.singleIdentity().owningKey))
                    block()
                }
        )
    }

    /** Returns a future that completes when the [progressTracker] reaches the [ProgressTracker.DONE] step. */
    private fun getDoneFuture(progressTracker: ProgressTracker): Future<ProgressTracker.Change> {
        return progressTracker.changes.takeFirst {
            it.progressTracker.currentStep == ProgressTracker.DONE
        }.bufferUntilSubscribed().toBlocking().toFuture()
    }

    @CordaService
    private class TestNotaryService(override val services: AppServiceHub, override val notaryIdentityKey: PublicKey) : TrustedAuthorityNotaryService() {
        override val uniquenessProvider = mock<UniquenessProvider>()
        override fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?> = TestNotaryFlow(otherPartySession, this)
        override fun start() {}
        override fun stop() {}
    }

    /** A notary flow that will yield without returning a response on the very first received request. */
    private class TestNotaryFlow(otherSide: FlowSession, service: TestNotaryService) : NotaryServiceFlow(otherSide, service) {
        @Suspendable
        override fun validateRequest(requestPayload: NotarisationPayload): TransactionParts {
            val myIdentity = serviceHub.myInfo.legalIdentities.first()
            MDC.put("name", myIdentity.name.toString())
            logger.info("Received a request from ${otherSideSession.counterparty.name}")
            val stx = requestPayload.signedTransaction
            subFlow(ResolveTransactionsFlow(stx, otherSideSession))

            if (TimedFlowTests.requestsReceived.getAndIncrement() == 0) {
                logger.info("Ignoring")
                // Waiting forever
                stateMachine.suspend(FlowIORequest.WaitForLedgerCommit(SecureHash.randomSHA256()), false)
            } else {
                logger.info("Processing")
            }
            return TransactionParts(stx.id, stx.inputs, stx.tx.timeWindow, stx.notary)
        }
    }
}
