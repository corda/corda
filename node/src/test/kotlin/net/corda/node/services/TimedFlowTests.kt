/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
import net.corda.testing.internal.GlobalDatabaseRule
import net.corda.testing.internal.LogHelper
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.startFlow
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.slf4j.MDC
import java.security.PublicKey
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertNotEquals

class TimedFlowTestRule(val clusterSize: Int) : ExternalResource() {

    lateinit var mockNet: InternalMockNetwork
    lateinit var notary: Party
    lateinit var node: StartedNode<InternalMockNetwork.MockNode>

    private fun startClusterAndNode(mockNet: InternalMockNetwork): Pair<Party, StartedNode<InternalMockNetwork.MockNode>> {
        val replicaIds = (0 until clusterSize)
        val notaryIdentity = DevIdentityGenerator.generateDistributedNotaryCompositeIdentity(
                replicaIds.map { mockNet.baseDirectory(mockNet.nextNodeId + it) },
                CordaX500Name("Custom Notary", "Zurich", "CH"))

        val networkParameters = NetworkParametersCopier(testNetworkParameters(listOf(NotaryInfo(notaryIdentity, true))))
        val notaryConfig = mock<NotaryConfig> {
            whenever(it.custom).thenReturn(true)
            whenever(it.isClusterConfig).thenReturn(true)
            whenever(it.validating).thenReturn(true)
        }

        val notaryNodes = (0 until clusterSize).map {
            mockNet.createUnstartedNode(InternalMockNodeParameters(configOverrides = {
                doReturn(notaryConfig).whenever(it).notary
            }))
        }

        val aliceNode = mockNet.createUnstartedNode(
                InternalMockNodeParameters(
                        legalName = CordaX500Name("Alice", "AliceCorp", "GB"),
                        configOverrides = { conf: NodeConfiguration ->
                            val flowTimeoutConfig = FlowTimeoutConfiguration(10.seconds, 3, 1.0)
                            doReturn(flowTimeoutConfig).whenever(conf).flowTimeout
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


    override fun before() {
        mockNet = InternalMockNetwork(
                listOf("net.corda.testing.contracts", "net.corda.node.services"),
                MockNetworkParameters().withServicePeerAllocationStrategy(InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin()),
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

class TimedFlowTests {
    companion object {
        /** A shared counter across all notary service nodes. */
        var requestsReceived: AtomicInteger = AtomicInteger(0)

        private val notary by lazy { globalRule.notary }
        private val node by lazy { globalRule.node }

        init {
            LogHelper.setLevel("+net.corda.flow", "+net.corda.testing.node", "+net.corda.node.services.messaging")
        }

        /** node_0 for default notary created by mock network + alice + cluster size = 5 */
        private val globalDatabaseRule = GlobalDatabaseRule(listOf("node_0", "node_1", "node_2", "node_3"))

        /** The notary nodes don't run any consensus protocol, so 2 nodes are sufficient for the purpose of this test. */
        private val globalRule = TimedFlowTestRule(2)

        @ClassRule @JvmField
        val ruleChain = RuleChain.outerRule(globalDatabaseRule).around(globalRule)

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

            if (requestsReceived.getAndIncrement() == 0) {
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
