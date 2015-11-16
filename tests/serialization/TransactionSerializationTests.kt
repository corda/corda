package serialization

import contracts.Cash
import core.*
import org.junit.Test
import testutils.TestUtils
import java.security.SignatureException
import kotlin.test.assertFailsWith

class TransactionSerializationTests {
    // Simple TX that takes 1000 pounds from me and sends 600 to someone else (with 400 change).
    // It refers to a fake TX/state that we don't bother creating here.
    val depositRef = InstitutionReference(MINI_CORP, OpaqueBytes.of(1))
    val outputState = Cash.State(depositRef, 600.POUNDS, DUMMY_PUBKEY_1)
    val changeState = Cash.State(depositRef, 400.POUNDS, TestUtils.keypair.public)

    val fakeStateRef = ContractStateRef(SecureHash.sha256("fake tx id"), 0)
    val tx = WireTransaction(
            arrayListOf(fakeStateRef),
            arrayListOf(outputState, changeState),
            arrayListOf(WireCommand(Cash.Commands.Move, arrayListOf(TestUtils.keypair.public)))
    )

    @Test
    fun signWireTX() {
        val signedTX: SignedWireTransaction = tx.signWith(listOf(TestUtils.keypair))

        // Now check that the signature we just made verifies.
        signedTX.verify()

        // Corrupt the data and ensure the signature catches the problem.
        signedTX.txBits[5] = 0
        assertFailsWith(SignatureException::class) {
            signedTX.verify()
        }
    }

    @Test
    fun wrongKeys() {
        // Can't sign without the private key.
        assertFailsWith(IllegalArgumentException::class) {
            tx.signWith(listOf())
        }
        // Can't sign with too many keys.
        assertFailsWith(IllegalArgumentException::class) {
            tx.signWith(listOf(TestUtils.keypair, TestUtils.keypair2))
        }

        val signedTX = tx.signWith(listOf(TestUtils.keypair))

        // Cannot construct with an empty sigs list.
        assertFailsWith(IllegalStateException::class) {
            signedTX.copy(sigs = emptyList())
        }

        // If the signature was replaced in transit, we don't like it.
        assertFailsWith(SignatureException::class) {
            val tx2 = tx.copy(args = listOf(WireCommand(Cash.Commands.Move, arrayListOf(TestUtils.keypair2.public)))).signWith(listOf(TestUtils.keypair2))
            signedTX.copy(sigs = tx2.sigs).verify()
        }
    }
}