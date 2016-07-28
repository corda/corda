package com.r3corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.*
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.MEGA_CORP
import com.r3corda.core.utilities.BriefLogFormatter
import com.r3corda.node.internal.testing.MockNetwork
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the data vending service.
 */
class DataVendingServiceTests {
    lateinit var network: MockNetwork

    init {
        BriefLogFormatter.init()
    }

    @Before
    fun setup() {
        network = MockNetwork()
    }

    class NotifyPSM(val server: NodeInfo, val tx: SignedTransaction)
    : ProtocolLogic<Boolean>() {
        override val topic: String get() = DataVending.Service.NOTIFY_TX_PROTOCOL_TOPIC
        @Suspendable
        override fun call(): Boolean {
            val sessionID = random63BitValue()
            val req = DataVending.Service.NotifyTxRequestMessage(tx, serviceHub.storageService.myLegalIdentity, sessionID)
            return sendAndReceive<DataVending.Service.NotifyTxResponseMessage>(server.identity, 0, sessionID, req).validate { it.accepted }
        }
    }

    @Test
    fun `notify of transaction`() {
        val (walletServiceNode, registerNode) = network.createTwoNodes()
        val beneficiary = walletServiceNode.services.storageService.myLegalIdentityKey.public
        val deposit = registerNode.services.storageService.myLegalIdentity.ref(1)
        network.runNetwork()

        // Generate an issuance transaction
        val ptx = TransactionType.General.Builder()
        Cash().generateIssue(ptx, Amount(100, Issued(deposit, USD)), beneficiary, DUMMY_NOTARY)

        // Complete the cash transaction, and then manually relay it
        ptx.signWith(registerNode.services.storageService.myLegalIdentityKey)
        val tx = ptx.toSignedTransaction()
        assertEquals(0, walletServiceNode.services.walletService.currentWallet.states.size)
        val notifyPsm = registerNode.smm.add(DataVending.Service.NOTIFY_TX_PROTOCOL_TOPIC, NotifyPSM(walletServiceNode.info, tx))

        // Check it was accepted
        network.runNetwork()
        assertTrue(notifyPsm.get(1, TimeUnit.SECONDS))

        // Check the transaction is in the receiving node
        val actual = walletServiceNode.services.walletService.currentWallet.states.single()
        val expected = tx.tx.outRef<Cash.State>(0)

        assertEquals(expected, actual)
    }

    /**
     * Test that invalid transactions are rejected.
     */
    @Test
    fun `notify failure`() {
        val (walletServiceNode, registerNode) = network.createTwoNodes()
        val beneficiary = walletServiceNode.services.storageService.myLegalIdentityKey.public
        val deposit = MEGA_CORP.ref(1)
        network.runNetwork()

        // Generate an issuance transaction
        val ptx = TransactionType.General.Builder()
        Cash().generateIssue(ptx, Amount(100, Issued(deposit, USD)), beneficiary, DUMMY_NOTARY)

        // The transaction tries issuing MEGA_CORP cash, but we aren't the issuer, so it's invalid
        ptx.signWith(registerNode.services.storageService.myLegalIdentityKey)
        val tx = ptx.toSignedTransaction(false)
        assertEquals(0, walletServiceNode.services.walletService.currentWallet.states.size)
        val notifyPsm = registerNode.smm.add(DataVending.Service.NOTIFY_TX_PROTOCOL_TOPIC, NotifyPSM(walletServiceNode.info, tx))

        // Check it was accepted
        network.runNetwork()
        assertFalse(notifyPsm.get(1, TimeUnit.SECONDS))

        // Check the transaction is not in the receiving node
        assertEquals(0, walletServiceNode.services.walletService.currentWallet.states.size)
    }
}