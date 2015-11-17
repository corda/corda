package testutils

import contracts.*
import core.*
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Instant
import kotlin.test.fail

object TestUtils {
    val keypair = KeyPairGenerator.getInstance("EC").genKeyPair()
    val keypair2 = KeyPairGenerator.getInstance("EC").genKeyPair()
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
        CP_PROGRAM_ID to CommercialPaper,
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
class TransactionForTest() {
    private val inStates = arrayListOf<ContractState>()

    class LabeledOutput(val label: String?, val state: ContractState) {
        override fun toString() = state.toString() + (if (label != null) " ($label)" else "")
        override fun equals(other: Any?) = other is LabeledOutput && state.equals(other.state)
        override fun hashCode(): Int = state.hashCode()
    }
    private val outStates = arrayListOf<LabeledOutput>()
    private val commands: MutableList<AuthenticatedObject<Command>> = arrayListOf()

    constructor(inStates: List<ContractState>, outStates: List<ContractState>, commands: List<AuthenticatedObject<Command>>) : this() {
        this.inStates.addAll(inStates)
        this.outStates.addAll(outStates.map { LabeledOutput(null, it) })
        this.commands.addAll(commands)
    }

    fun input(s: () -> ContractState) = inStates.add(s())
    fun output(label: String? = null, s: () -> ContractState) = outStates.add(LabeledOutput(label, s()))
    fun arg(vararg key: PublicKey, c: () -> Command) {
        val keys = listOf(*key)
        commands.add(AuthenticatedObject(keys, keys.mapNotNull { TEST_KEYS_TO_CORP_MAP[it] }, c()))
    }

    private fun run(time: Instant) = TransactionForVerification(inStates, outStates.map { it.state }, commands, time).verify(TEST_PROGRAM_MAP)

    infix fun `fails requirement`(msg: String) = rejects(msg)
    // which is uglier?? :)
    fun fails_requirement(msg: String) = this.`fails requirement`(msg)

    fun accepts(time: Instant = TEST_TX_TIME) = run(time)
    fun rejects(withMessage: String? = null, time: Instant = TEST_TX_TIME) {
        val r = try {
            run(time)
            false
        } catch (e: Exception) {
            val m = e.message
            if (m == null)
                fail("Threw exception without a message")
            else
                if (withMessage != null && !m.toLowerCase().contains(withMessage.toLowerCase())) throw AssertionError("Error was actually: $m", e)
            true
        }
        if (!r) throw AssertionError("Expected exception but didn't get one")
    }

    // Allow customisation of partial transactions.
    fun transaction(body: TransactionForTest.() -> Unit): TransactionForTest {
        val tx = TransactionForTest()
        tx.inStates.addAll(inStates)
        tx.outStates.addAll(outStates)
        tx.commands.addAll(commands)
        tx.body()
        return tx
    }

    // Use this to create transactions where the output of this transaction is automatically used as an input of
    // the next.
    fun chain(vararg outputLabels: String, body: TransactionForTest.() -> Unit) {
        val states = outStates.mapNotNull {
            val l = it.label
            if (l != null && outputLabels.contains(l))
                it.state
            else
                null
        }
        val tx = TransactionForTest()
        tx.inStates.addAll(states)
        tx.body()
    }

    override fun toString(): String {
        return """transaction {
            inputs:   $inStates
            outputs:  $outStates
            commands  $commands
        }"""
    }

    override fun equals(other: Any?) = this === other || (other is TransactionForTest && inStates == other.inStates && outStates == other.outStates && commands == other.commands)

    override fun hashCode(): Int {
        var result = inStates.hashCode()
        result += 31 * result + outStates.hashCode()
        result += 31 * result + commands.hashCode()
        return result
    }
}

fun transaction(body: TransactionForTest.() -> Unit): TransactionForTest {
    val tx = TransactionForTest()
    tx.body()
    return tx
}
