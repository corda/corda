import java.math.BigInteger
import java.security.PublicKey
import kotlin.test.assertTrue
import kotlin.test.fail

class DummyPublicKey(private val s: String) : PublicKey, Comparable<PublicKey> {
    override fun getAlgorithm() = "DUMMY"
    override fun getEncoded() = s.toByteArray()
    override fun getFormat() = "ASN.1"
    override fun compareTo(other: PublicKey): Int = BigInteger(encoded).compareTo(BigInteger(other.encoded))
}

// A few dummy values for testing.
val MEGA_CORP_KEY = DummyPublicKey("mini")
val MINI_CORP_KEY = DummyPublicKey("mega")
val DUMMY_PUBKEY_1 = DummyPublicKey("x1")
val DUMMY_PUBKEY_2 = DummyPublicKey("x2")
val MEGA_CORP = Institution("MegaCorp", MEGA_CORP_KEY)
val MINI_CORP = Institution("MiniCorp", MINI_CORP_KEY)

val keyToCorpMap: Map<PublicKey, Institution> = mapOf(
        MEGA_CORP_KEY to MEGA_CORP,
        MINI_CORP_KEY to MINI_CORP
)

// DSL for building pseudo-transactions (not the same as the wire protocol) for testing purposes.

// Corresponds to the args to Contract.verify
data class TransactionForTest(
    private val inStates: MutableList<ContractState> = arrayListOf(),
    private val outStates: MutableList<ContractState> = arrayListOf(),
    private val args: MutableList<VerifiedSignedCommand> = arrayListOf()
) {
    fun input(s: () -> ContractState) = inStates.add(s())
    fun output(s: () -> ContractState) = outStates.add(s())
    fun arg(key: PublicKey, c: () -> Command) = args.add(VerifiedSignedCommand(key, keyToCorpMap[key], c()))

    infix fun Contract.`fails requirement`(msg: String) {
        try {
            verify(inStates, outStates, args)
        } catch(e: Exception) {
            val m = e.message
            if (m == null)
                fail("Threw exception without a message")
            else
                assertTrue(m.contains(msg), "Error was actually: $m")
        }
    }

    // which is uglier?? :)
    fun Contract.fails_requirement(msg: String) = this.`fails requirement`(msg)

    fun Contract.accepts() {
        verify(inStates, outStates, args)
    }

    // Allow customisation of partial transactions.
    fun transaction(body: TransactionForTest.() -> Unit): TransactionForTest {
        val tx = TransactionForTest()
        tx.inStates.addAll(inStates)
        tx.outStates.addAll(outStates)
        tx.args.addAll(args)
        tx.body()
        return tx
    }
}

fun transaction(body: TransactionForTest.() -> Unit): TransactionForTest {
    val tx = TransactionForTest()
    tx.body()
    return tx
}
