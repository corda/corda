package com.r3corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.Issued
import com.r3corda.core.contracts.TransactionType
import com.r3corda.core.contracts.USD
import com.r3corda.core.crypto.Party
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.node.services.persistence.DataVending.Service.NotifyTransactionHandler
import com.r3corda.protocols.BroadcastTransactionProtocol.NotifyTxRequest
import com.r3corda.testing.MEGA_CORP
import com.r3corda.testing.node.MockNetwork
import com.r3corda.testing.node.MockNetwork.MockNode
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
        assertEquals(0, vaultServiceNode.services.vaultService.currentVault.states.toList().size)

        registerNode.sendNotifyTx(tx, vaultServiceNode)

        // Check the transaction is in the receiving node
        val actual = vaultServiceNode.services.vaultService.currentVault.states.singleOrNull()
        val expected = tx.tx.outRef<Cash.State>(0)

        assertEquals(expected, actual)
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
        assertEquals(0, vaultServiceNode.services.vaultService.currentVault.states.toList().size)

        registerNode.sendNotifyTx(tx, vaultServiceNode)

        // Check the transaction is not in the receiving node
        assertEquals(0, vaultServiceNode.services.vaultService.currentVault.states.toList().size)
    }

    private fun MockNode.sendNotifyTx(tx: SignedTransaction, walletServiceNode: MockNode) {
        walletServiceNode.services.registerProtocolInitiator(NotifyTxProtocol::class, ::NotifyTransactionHandler)
        services.startProtocol("notify-tx", NotifyTxProtocol(walletServiceNode.info.legalIdentity, tx))
        network.runNetwork()
    }


    private class NotifyTxProtocol(val otherParty: Party, val stx: SignedTransaction) : ProtocolLogic<Unit>() {
        @Suspendable
        override fun call() = send(otherParty, NotifyTxRequest(stx, emptySet()))
    }

}
