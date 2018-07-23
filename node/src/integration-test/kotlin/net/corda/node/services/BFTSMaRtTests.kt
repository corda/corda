package net.corda.node.services

import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.node.services.BFTNotaryServiceTests.Companion.startBftClusterAndNode
import net.corda.node.services.transactions.minClusterSize
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test

class BFTSMaRtTests {
    private lateinit var mockNet: InternalMockNetwork

    @Before
    fun before() {
        mockNet = InternalMockNetwork(cordappsForAllNodes = cordappsForPackages("net.corda.testing.contracts"))
    }

    @After
    fun stopNodes() {
        mockNet.stopNodes()
    }

    /** Failure mode is the redundant replica gets stuck in startup, so we can't dispose it cleanly at the end. */
    @Test
    fun `all replicas start even if there is a new consensus during startup`() {
        val clusterSize = minClusterSize(1)
        val (notary, node) = startBftClusterAndNode(clusterSize, mockNet, exposeRaces = true) // This true adds a sleep to expose the race.
        val f = node.run {
            val trivialTx = signInitialTransaction(notary) {
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            // Create a new consensus while the redundant replica is sleeping:
            services.startFlow(NotaryFlow.Client(trivialTx)).resultFuture
        }
        mockNet.runNetwork()
        f.getOrThrow()
    }

    private fun StartedNode.signInitialTransaction(notary: Party, block: TransactionBuilder.() -> Any?): SignedTransaction {
        return services.signInitialTransaction(
                TransactionBuilder(notary).apply {
                    addCommand(dummyCommand(services.myInfo.singleIdentity().owningKey))
                    block()
                }
        )
    }
}
