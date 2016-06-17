package com.r3corda.core.serialization

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.newSecureRandom
import com.r3corda.core.node.services.testing.MockStorageService
import com.r3corda.core.seconds
import com.r3corda.core.testing.*
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import java.security.SecureRandom
import java.security.SignatureException
import java.util.Currency
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val TEST_PROGRAM_ID = TransactionSerializationTests.TestCash()

class TransactionSerializationTests {
    class TestCash : Contract {
        override val legalContractReference = SecureHash.sha256("TestCash")

        override fun verify(tx: TransactionForVerification) {
        }

        data class State(
                val deposit: PartyAndReference,
                val amount: Amount<Currency>,
                override val owner: PublicKey,
                override val notary: Party) : OwnableState {
            override val contract: Contract = TEST_PROGRAM_ID
            override fun withNewOwner(newOwner: PublicKey) = Pair(Commands.Move(), copy(owner = newOwner))
        }
        interface Commands : CommandData {
            class Move() : TypeOnlyCommandData(), Commands
            data class Issue(val nonce: Long = newSecureRandom().nextLong()) : Commands
            data class Exit(val amount: Amount<Currency>) : Commands
        }
    }

        // Simple TX that takes 1000 pounds from me and sends 600 to someone else (with 400 change).
    // It refers to a fake TX/state that we don't bother creating here.
    val depositRef = MINI_CORP.ref(1)
    val outputState = TestCash.State(depositRef, 600.POUNDS, DUMMY_PUBKEY_1, DUMMY_NOTARY)
    val changeState = TestCash.State(depositRef, 400.POUNDS, TestUtils.keypair.public, DUMMY_NOTARY)

    val fakeStateRef = generateStateRef()
    lateinit var tx: TransactionBuilder

    @Before
    fun setup() {
        tx = TransactionBuilder().withItems(
                fakeStateRef, outputState, changeState, Command(TestCash.Commands.Move(), arrayListOf(TestUtils.keypair.public))
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
                    Command(TestCash.Commands.Move(), TestUtils.keypair2.public))
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
        val ltx = stx.verifyToLedgerTransaction(MOCK_IDENTITY_SERVICE, MockStorageService().attachments)
        assertEquals(tx.commands().map { it.value }, ltx.commands.map { it.value })
        assertEquals(tx.inputStates(), ltx.inputs)
        assertEquals(tx.outputStates(), ltx.outputs)
        assertEquals(TEST_TX_TIME, ltx.commands.getTimestampBy(DUMMY_NOTARY)!!.midpoint)
    }
}
