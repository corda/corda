package net.corda.docs

import net.corda.core.contracts.*
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.toFuture
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FxTransactionBuildTutorialTest {
    lateinit var net: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var nodeA: MockNetwork.MockNode
    lateinit var nodeB: MockNetwork.MockNode

    @Before
    fun setup() {
        net = MockNetwork(threadPerNode = true)
        val notaryService = ServiceInfo(ValidatingNotaryService.type)
        notaryNode = net.createNode(
                legalName = DUMMY_NOTARY.name,
                overrideServices = mapOf(Pair(notaryService, DUMMY_NOTARY_KEY)),
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), notaryService))
        nodeA = net.createPartyNode(notaryNode.info.address)
        nodeB = net.createPartyNode(notaryNode.info.address)
        FxTransactionDemoTutorial.registerFxProtocols(nodeA.services)
        FxTransactionDemoTutorial.registerFxProtocols(nodeB.services)
        WorkflowTransactionBuildTutorial.registerWorkflowProtocols(nodeA.services)
        WorkflowTransactionBuildTutorial.registerWorkflowProtocols(nodeB.services)
    }

    @After
    fun cleanUp() {
        println("Close DB")
        net.stopNodes()
    }

    @Test
    fun `Run ForeignExchangeFlow to completion`() {
        // Use NodeA as issuer and create some dollars
        val flowHandle1 = nodeA.services.startFlow(CashIssueFlow(DOLLARS(1000),
                OpaqueBytes.of(0x01),
                nodeA.info.legalIdentity,
                notaryNode.info.notaryIdentity))
        // Wait for the flow to stop and print
        flowHandle1.resultFuture.getOrThrow()
        printBalances()

        // Using NodeB as Issuer create some pounds.
        val flowHandle2 = nodeB.services.startFlow(CashIssueFlow(POUNDS(1000),
                OpaqueBytes.of(0x01),
                nodeB.info.legalIdentity,
                notaryNode.info.notaryIdentity))
        // Wait for flow to come to an end and print
        flowHandle2.resultFuture.getOrThrow()
        printBalances()

        // Setup some futures on the vaults to await the arrival of the exchanged funds at both nodes
        val nodeAVaultUpdate = nodeA.services.vaultService.updates.toFuture()
        val nodeBVaultUpdate = nodeB.services.vaultService.updates.toFuture()

        // Now run the actual Fx exchange
        val doIt = nodeA.services.startFlow(ForeignExchangeFlow("trade1",
                POUNDS(100).issuedBy(nodeB.info.legalIdentity.ref(0x01)),
                DOLLARS(200).issuedBy(nodeA.info.legalIdentity.ref(0x01)),
                nodeA.info.legalIdentity,
                nodeB.info.legalIdentity))
        // wait for the flow to finish and the vault updates to be done
        doIt.resultFuture.getOrThrow()
        // Get the balances when the vault updates
        nodeAVaultUpdate.get()
        val balancesA = databaseTransaction(nodeA.database) {
            nodeA.services.vaultService.cashBalances
        }
        nodeBVaultUpdate.get()
        val balancesB = databaseTransaction(nodeB.database) {
            nodeB.services.vaultService.cashBalances
        }
        println("BalanceA\n" + balancesA)
        println("BalanceB\n" + balancesB)
        // Verify the transfers occurred as expected
        assertEquals(POUNDS(100), balancesA[GBP])
        assertEquals(DOLLARS(1000 - 200), balancesA[USD])
        assertEquals(POUNDS(1000 - 100), balancesB[GBP])
        assertEquals(DOLLARS(200), balancesB[USD])
    }

    private fun printBalances() {
        // Print out the balances
        databaseTransaction(nodeA.database) {
            println("BalanceA\n" + nodeA.services.vaultService.cashBalances)
        }
        databaseTransaction(nodeB.database) {
            println("BalanceB\n" + nodeB.services.vaultService.cashBalances)
        }
    }
}
