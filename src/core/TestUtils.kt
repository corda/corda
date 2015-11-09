package core

import contracts.*
import java.math.BigInteger
import java.security.PublicKey
import java.time.Instant
import kotlin.test.fail

class DummyPublicKey(private val s: String) : PublicKey, Comparable<PublicKey> {
    override fun getAlgorithm() = "DUMMY"
    override fun getEncoded() = s.toByteArray()
    override fun getFormat() = "ASN.1"
    override fun compareTo(other: PublicKey): Int = BigInteger(encoded).compareTo(BigInteger(other.encoded))
    override fun toString() = "PUBKEY[$s]"
}

// A few dummy values for testing.
val MEGA_CORP_KEY = DummyPublicKey("mini")
val MINI_CORP_KEY = DummyPublicKey("mega")
val DUMMY_PUBKEY_1 = DummyPublicKey("x1")
val DUMMY_PUBKEY_2 = DummyPublicKey("x2")
val MEGA_CORP = Institution("MegaCorp", MEGA_CORP_KEY)
val MINI_CORP = Institution("MiniCorp", MINI_CORP_KEY)

val TEST_KEYS_TO_CORP_MAP: Map<PublicKey, Institution> = mapOf(
        MEGA_CORP_KEY to MEGA_CORP,
        MINI_CORP_KEY to MINI_CORP
)

// A dummy time at which we will be pretending test transactions are created.
val TEST_TX_TIME = Instant.parse("2015-04-17T12:00:00.00Z")

// In a real system this would be a persistent map of hash to bytecode and we'd instantiate the object as needed inside
// a sandbox. For now we just instantiate right at the start of the program.
val TEST_PROGRAM_MAP: Map<SecureHash, Contract> = mapOf(
        CASH_PROGRAM_ID to Cash,
        CP_PROGRAM_ID to ComedyPaper,
        DUMMY_PROGRAM_ID to DummyContract
)

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// DSL for building pseudo-transactions (not the same as the wire protocol) for testing purposes.
//
// Define a transaction like this:
//
// transaction {
//    input { someExpression }
//    output { someExpression }
//    arg { someExpression }
//
//    transaction {
//         ... same thing but works with a copy of the parent, can add inputs/outputs/args just within this scope.
//    }
//
//    contract.accepts() -> should pass
//    contract `fails requirement` "some substring of the error message"
// }
//
// TODO: Make it impossible to forget to test either a failure or an accept for each transaction{} block

// Corresponds to the args to Contract.verify
data class TransactionForTest(
    private val inStates: MutableList<ContractState> = arrayListOf(),
    private val outStates: MutableList<ContractState> = arrayListOf(),
    private val args: MutableList<VerifiedSigned<Command>> = arrayListOf()
) {
    fun input(s: () -> ContractState) = inStates.add(s())
    fun output(s: () -> ContractState) = outStates.add(s())
    fun arg(key: PublicKey, c: () -> Command) = args.add(VerifiedSigned(listOf(key), TEST_KEYS_TO_CORP_MAP[key].let { if (it != null) listOf(it) else emptyList() }, c()))

    private fun run() = TransactionForVerification(inStates, outStates, args, TEST_TX_TIME).verify(TEST_PROGRAM_MAP)

    infix fun `fails requirement`(msg: String) {
        try {
            run()
        } catch(e: Exception) {
            val m = e.message
            if (m == null)
                fail("Threw exception without a message")
            else
                if (!m.toLowerCase().contains(msg.toLowerCase())) throw AssertionError("Error was actually: $m", e)
        }
    }

    // which is uglier?? :)
    fun fails_requirement(msg: String) = this.`fails requirement`(msg)

    fun accepts() = run()
    fun rejects(withMessage: String? = null) {
        val r = try {
            run()
            false
        } catch (e: Exception) {
            val m = e.message
            if (m == null)
                fail("Threw exception without a message")
            else
                if (withMessage != null && !m.contains(withMessage)) throw AssertionError("Error was actually: $m", e)
            true
        }
        if (!r) throw AssertionError("Expected exception but didn't get one")
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

    override fun toString(): String {
        return """transaction {
            inputs:   $inStates
            outputs:  $outStates
            args:     $args
        }"""
    }
}

fun transaction(body: TransactionForTest.() -> Unit): TransactionForTest {
    val tx = TransactionForTest()
    tx.body()
    return tx
}
