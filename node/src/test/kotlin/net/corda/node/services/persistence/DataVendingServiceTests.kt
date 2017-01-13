package net.corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.TransactionType
import net.corda.core.contracts.USD
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.unconsumedStates
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.flows.BroadcastTransactionFlow.NotifyTxRequest
import net.corda.node.services.persistence.DataVending.Service.NotifyTransactionHandler
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.MEGA_CORP
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for the data vending service.
 */
class DataVendingServiceTests {
    lateinit var network: MockNetwork

    @Before
    fun setup() {
        network = MockNetwork()
    }

    @Test
    fun `notify of transaction`() {
        val (vaultServiceNode, registerNode) = network.createTwoNodes()
        val beneficiary = vaultServiceNode.info.legalIdentity.owningKey
        val deposit = registerNode.info.legalIdentity.ref(1)
        network.runNetwork()

        // Generate an issuance transaction
        val ptx = TransactionType.General.Builder(null)
        Cash().generateIssue(ptx, Amount(100, Issued(deposit, USD)), beneficiary, DUMMY_NOTARY)

        // Complete the cash transaction, and then manually relay it
        val registerKey = registerNode.services.legalIdentityKey
        ptx.signWith(registerKey)
        val tx = ptx.toSignedTransaction()
        databaseTransaction(vaultServiceNode.database) {
            assertEquals(0, vaultServiceNode.services.vaultService.unconsumedStates<Cash.State>().size)

            registerNode.sendNotifyTx(tx, vaultServiceNode)

            // Check the transaction is in the receiving node
            val actual = vaultServiceNode.services.vaultService.unconsumedStates<Cash.State>().singleOrNull()
            val expected = tx.tx.outRef<Cash.State>(0)

            assertEquals(expected, actual)
        }
    }

    /**
     * Test that invalid transactions are rejected.
     */
    @Test
    fun `notify failure`() {
        val (vaultServiceNode, registerNode) = network.createTwoNodes()
        val beneficiary = vaultServiceNode.info.legalIdentity.owningKey
        val deposit = MEGA_CORP.ref(1)
        network.runNetwork()

        // Generate an issuance transaction
        val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
        Cash().generateIssue(ptx, Amount(100, Issued(deposit, USD)), beneficiary, DUMMY_NOTARY)

        // The transaction tries issuing MEGA_CORP cash, but we aren't the issuer, so it's invalid
        val registerKey = registerNode.services.legalIdentityKey
        ptx.signWith(registerKey)
        val tx = ptx.toSignedTransaction(false)
        databaseTransaction(vaultServiceNode.database) {
            assertEquals(0, vaultServiceNode.services.vaultService.unconsumedStates<Cash.State>().size)

            registerNode.sendNotifyTx(tx, vaultServiceNode)

            // Check the transaction is not in the receiving node
            assertEquals(0, vaultServiceNode.services.vaultService.unconsumedStates<Cash.State>().size)
        }
    }

    private fun MockNode.sendNotifyTx(tx: SignedTransaction, walletServiceNode: MockNode) {
        walletServiceNode.services.registerFlowInitiator(NotifyTxFlow::class, ::NotifyTransactionHandler)
        services.startFlow(NotifyTxFlow(walletServiceNode.info.legalIdentity, tx))
        network.runNetwork()
    }


    private class NotifyTxFlow(val otherParty: Party, val stx: SignedTransaction) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = send(otherParty, NotifyTxRequest(stx))
    }

}
