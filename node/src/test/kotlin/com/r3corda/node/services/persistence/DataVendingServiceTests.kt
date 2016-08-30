package com.r3corda.node.services.persistence

import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.Issued
import com.r3corda.core.contracts.TransactionType
import com.r3corda.core.contracts.USD
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.node.internal.testing.MockNetwork
import com.r3corda.testing.MEGA_CORP
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
        val (walletServiceNode, registerNode) = network.createTwoNodes()
        val beneficiary = walletServiceNode.services.storageService.myLegalIdentityKey.public
        val deposit = registerNode.services.storageService.myLegalIdentity.ref(1)
        network.runNetwork()

        // Generate an issuance transaction
        val ptx = TransactionType.General.Builder(null)
        Cash().generateIssue(ptx, Amount(100, Issued(deposit, USD)), beneficiary, DUMMY_NOTARY)

        // Complete the cash transaction, and then manually relay it
        ptx.signWith(registerNode.services.storageService.myLegalIdentityKey)
        val tx = ptx.toSignedTransaction()
        assertEquals(0, walletServiceNode.services.walletService.currentWallet.states.toList().size)
        DataVending.Service.notify(registerNode.net, registerNode.services.storageService.myLegalIdentity,
                walletServiceNode.info, tx)
        network.runNetwork()

        // Check the transaction is in the receiving node
        val actual = walletServiceNode.services.walletService.currentWallet.states.singleOrNull()
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
        val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
        Cash().generateIssue(ptx, Amount(100, Issued(deposit, USD)), beneficiary, DUMMY_NOTARY)

        // The transaction tries issuing MEGA_CORP cash, but we aren't the issuer, so it's invalid
        ptx.signWith(registerNode.services.storageService.myLegalIdentityKey)
        val tx = ptx.toSignedTransaction(false)
        assertEquals(0, walletServiceNode.services.walletService.currentWallet.states.toList().size)
        DataVending.Service.notify(registerNode.net, registerNode.services.storageService.myLegalIdentity,
                walletServiceNode.info, tx)
        network.runNetwork()

        // Check the transaction is not in the receiving node
        assertEquals(0, walletServiceNode.services.walletService.currentWallet.states.toList().size)
    }
}