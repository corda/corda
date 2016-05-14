package core.serialization

import contracts.Cash
import core.*
import core.contracts.*
import core.testutils.*
import org.junit.Before
import org.junit.Test
import java.security.SignatureException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionSerializationTests {
    // Simple TX that takes 1000 pounds from me and sends 600 to someone else (with 400 change).
    // It refers to a fake TX/state that we don't bother creating here.
    val depositRef = MINI_CORP.ref(1)
    val outputState = Cash.State(depositRef, 600.POUNDS, DUMMY_PUBKEY_1, DUMMY_NOTARY)
    val changeState = Cash.State(depositRef, 400.POUNDS, TestUtils.keypair.public, DUMMY_NOTARY)

    val fakeStateRef = generateStateRef()
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
        signedTX.verifySignatures()

        // Corrupt the data and ensure the signature catches the problem.
        signedTX.txBits.bits[5] = 0
        assertFailsWith(SignatureException::class) {
            signedTX.verifySignatures()
        }
    }

    @Test
    fun wrongKeys() {
        // Can't convert if we don't have signatures for all commands
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
        tx.setTime(TEST_TX_TIME, DUMMY_NOTARY, 30.seconds)
        tx.signWith(TestUtils.keypair)
        tx.signWith(DUMMY_NOTARY_KEY)
        val stx = tx.toSignedTransaction()
        val ltx = stx.verifyToLedgerTransaction(MockIdentityService, MockStorageService().attachments)
        assertEquals(tx.commands().map { it.value }, ltx.commands.map { it.value })
        assertEquals(tx.inputStates(), ltx.inputs)
        assertEquals(tx.outputStates(), ltx.outputs)
        assertEquals(TEST_TX_TIME, ltx.commands.getTimestampBy(DUMMY_NOTARY)!!.midpoint)
    }
}