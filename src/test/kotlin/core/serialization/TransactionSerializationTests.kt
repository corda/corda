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
import core.crypto.SecureHash
import core.testutils.DUMMY_PUBKEY_1
import core.testutils.MINI_CORP
import core.testutils.TEST_TX_TIME
import core.testutils.TestUtils
import org.junit.Before
import org.junit.Test
import java.security.SignatureException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionSerializationTests {
    // Simple TX that takes 1000 pounds from me and sends 600 to someone else (with 400 change).
    // It refers to a fake TX/state that we don't bother creating here.
    val depositRef = MINI_CORP.ref(1)
    val outputState = Cash.State(depositRef, 600.POUNDS, DUMMY_PUBKEY_1)
    val changeState = Cash.State(depositRef, 400.POUNDS, TestUtils.keypair.public)

    val fakeStateRef = StateRef(SecureHash.sha256("fake tx id"), 0)
    lateinit var tx: TransactionBuilder

    @Before
    fun setup() {
        tx = TransactionBuilder().withItems(
            fakeStateRef, outputState, changeState, Command(Cash.Commands.Move(), arrayListOf(TestUtils.keypair.public))
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
            val tx2 = TransactionBuilder().withItems(fakeStateRef, outputState, changeState,
                    Command(Cash.Commands.Move(), TestUtils.keypair2.public))
            tx2.signWith(TestUtils.keypair2)

            signedTX.copy(sigs = tx2.toSignedTransaction().sigs).verify()
        }
    }

    @Test
    fun timestamp() {
        tx.setTime(TEST_TX_TIME, DUMMY_TIMESTAMPER.identity, 30.seconds)
        tx.timestamp(DUMMY_TIMESTAMPER)
        tx.signWith(TestUtils.keypair)
        val stx = tx.toSignedTransaction()
        val ltx = stx.verifyToLedgerTransaction(MockIdentityService, MockStorageService().attachments)
        assertEquals(tx.commands().map { it.data }, ltx.commands.map { it.value })
        assertEquals(tx.inputStates(), ltx.inputs)
        assertEquals(tx.outputStates(), ltx.outputs)
        assertEquals(TEST_TX_TIME, ltx.commands.getTimestampBy(DUMMY_TIMESTAMPER.identity)!!.midpoint)
    }
}