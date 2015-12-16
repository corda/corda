/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.serialization

import contracts.Cash
import core.*
import core.testutils.*
import org.junit.Before
import org.junit.Test
import java.security.SignatureException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TransactionSerializationTests {
    // Simple TX that takes 1000 pounds from me and sends 600 to someone else (with 400 change).
    // It refers to a fake TX/state that we don't bother creating here.
    val depositRef = MINI_CORP.ref(1)
    val outputState = Cash.State(depositRef, 600.POUNDS, DUMMY_PUBKEY_1)
    val changeState = Cash.State(depositRef, 400.POUNDS, TestUtils.keypair.public)

    val fakeStateRef = ContractStateRef(SecureHash.sha256("fake tx id"), 0)
    lateinit var tx: PartialTransaction

    @Before
    fun setup() {
        tx = PartialTransaction().withItems(
            fakeStateRef, outputState, changeState, WireCommand(Cash.Commands.Move(), arrayListOf(TestUtils.keypair.public))
        )
    }

    @Test
    fun signWireTX() {
        tx.signWith(TestUtils.keypair)
        val signedTX = tx.toSignedTransaction()

        // Now check that the signature we just made verifies.
        signedTX.verify()

        // Corrupt the data and ensure the signature catches the problem.
        signedTX.txBits.bits[5] = 0
        assertFailsWith(SignatureException::class) {
            signedTX.verify()
        }
    }

    @Test
    fun tooManyKeys() {
        assertFailsWith(IllegalStateException::class) {
            tx.signWith(TestUtils.keypair)
            tx.signWith(TestUtils.keypair2)
            tx.toSignedTransaction()
        }
    }

    @Test
    fun wrongKeys() {
        // Can't convert if we don't have enough signatures.
        assertFailsWith(IllegalStateException::class) {
            tx.toSignedTransaction()
        }

        tx.signWith(TestUtils.keypair)
        val signedTX = tx.toSignedTransaction()

        // Cannot construct with an empty sigs list.
        assertFailsWith(IllegalStateException::class) {
            signedTX.copy(sigs = emptyList())
        }

        // If the signature was replaced in transit, we don't like it.
        assertFailsWith(SignatureException::class) {
            val tx2 = PartialTransaction().withItems(fakeStateRef, outputState, changeState,
                    WireCommand(Cash.Commands.Move(), arrayListOf(TestUtils.keypair2.public)))
            tx2.signWith(TestUtils.keypair2)

            signedTX.copy(sigs = tx2.toSignedTransaction().sigs).verify()
        }
    }

    @Test
    fun timestamp() {
        tx.signWith(TestUtils.keypair)
        val ttx = tx.toSignedTransaction().toTimestampedTransactionWithoutTime()
        val ltx = ttx.verifyToLedgerTransaction(DUMMY_TIMESTAMPER, MockIdentityService)
        assertEquals(tx.commands().map { it.command }, ltx.commands.map { it.value })
        assertEquals(tx.inputStates(), ltx.inStateRefs)
        assertEquals(tx.outputStates(), ltx.outStates)
        assertNull(ltx.time)

        val ltx2: LedgerTransaction = tx.
                toSignedTransaction().
                toTimestampedTransaction(DUMMY_TIMESTAMPER).
                verifyToLedgerTransaction(DUMMY_TIMESTAMPER, MockIdentityService)
        assertEquals(TEST_TX_TIME, ltx2.time)
    }
}