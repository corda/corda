/*
 * Copyright 2015, R3 CEV. All rights reserved.
 */

@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")

package core.testutils

import com.google.common.io.BaseEncoding
import contracts.*
import core.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

object TestUtils {
    val keypair = KeyPairGenerator.getInstance("EC").genKeyPair()
    val keypair2 = KeyPairGenerator.getInstance("EC").genKeyPair()
}

// A few dummy values for testing.
val MEGA_CORP_KEY = TestUtils.keypair
val MEGA_CORP_PUBKEY = MEGA_CORP_KEY.public
val MINI_CORP_KEY = TestUtils.keypair2
val MINI_CORP_PUBKEY = MINI_CORP_KEY.public
val DUMMY_PUBKEY_1 = DummyPublicKey("x1")
val DUMMY_PUBKEY_2 = DummyPublicKey("x2")
val ALICE_KEY = KeyPairGenerator.getInstance("EC").genKeyPair()
val ALICE = ALICE_KEY.public
val BOB_KEY = KeyPairGenerator.getInstance("EC").genKeyPair()
val BOB = BOB_KEY.public
val MEGA_CORP = Party("MegaCorp", MEGA_CORP_PUBKEY)
val MINI_CORP = Party("MiniCorp", MINI_CORP_PUBKEY)

val TEST_KEYS_TO_CORP_MAP: Map<PublicKey, Party> = mapOf(
        MEGA_CORP_PUBKEY to MEGA_CORP,
        MINI_CORP_PUBKEY to MINI_CORP
)

// A dummy time at which we will be pretending test transactions are created.
val TEST_TX_TIME = Instant.parse("2015-04-17T12:00:00.00Z")

// In a real system this would be a persistent map of hash to bytecode and we'd instantiate the object as needed inside
// a sandbox. For now we just instantiate right at the start of the program.
val TEST_PROGRAM_MAP: Map<SecureHash, Contract> = mapOf(
        CASH_PROGRAM_ID to Cash(),
        CP_PROGRAM_ID to CommercialPaper(),
        CROWDFUND_PROGRAM_ID to CrowdFund(),
        DUMMY_PROGRAM_ID to DummyContract
)

/**
 * A test/mock timestamping service that doesn't use any signatures or security. It always timestamps with
 * [TEST_TX_TIME], an arbitrary point on the timeline.
 */
class DummyTimestamper(private val time: Instant = TEST_TX_TIME) : TimestamperService {
    override fun timestamp(hash: SecureHash): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use {
            it.writeLong(time.toEpochMilli())
            it.write(hash.bits)
        }
        return bos.toByteArray()
    }

    override fun verifyTimestamp(hash: SecureHash, signedTimestamp: ByteArray): Instant {
        val dis = DataInputStream(ByteArrayInputStream(signedTimestamp))
        val epochMillis = dis.readLong()
        val serHash = ByteArray(32)
        dis.readFully(serHash)
        if (!Arrays.equals(serHash, hash.bits))
            throw IllegalStateException("Hash mismatch: ${BaseEncoding.base16().encode(serHash)} vs ${BaseEncoding.base16().encode(hash.bits)}")
        return Instant.ofEpochMilli(epochMillis)
    }
}

val DUMMY_TIMESTAMPER = DummyTimestamper()

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Defines a simple DSL for building pseudo-transactions (not the same as the wire protocol) for testing purposes.
//
// Define a transaction like this:
//
// transaction {
//    input { someExpression }
//    output { someExpression }
//    arg { someExpression }
//
//    tweak {
//         ... same thing but works with a copy of the parent, can add inputs/outputs/args just within this scope.
//    }
//
//    contract.accepts() -> should pass
//    contract `fails requirement` "some substring of the error message"
// }
//
// TODO: Make it impossible to forget to test either a failure or an accept for each transaction{} block

infix fun Cash.State.`owned by`(owner: PublicKey) = this.copy(owner = owner)
infix fun CommercialPaper.State.`owned by`(owner: PublicKey) = this.copy(owner = owner)
// Allows you to write 100.DOLLARS.CASH
val Amount.CASH: Cash.State get() = Cash.State(MINI_CORP.ref(1,2,3), this, NullPublicKey)

class LabeledOutput(val label: String?, val state: ContractState) {
    override fun toString() = state.toString() + (if (label != null) " ($label)" else "")
    override fun equals(other: Any?) = other is LabeledOutput && state.equals(other.state)
    override fun hashCode(): Int = state.hashCode()
}

infix fun ContractState.label(label: String) = LabeledOutput(label, this)

abstract class AbstractTransactionForTest {
    protected val outStates = ArrayList<LabeledOutput>()
    protected val commands  = ArrayList<AuthenticatedObject<Command>>()

    open fun output(label: String? = null, s: () -> ContractState) = LabeledOutput(label, s()).apply { outStates.add(this) }

    fun arg(vararg key: PublicKey, c: () -> Command) {
        val keys = listOf(*key)
        commands.add(AuthenticatedObject(keys, keys.mapNotNull { TEST_KEYS_TO_CORP_MAP[it] }, c()))
    }

    // Forbid patterns like:  transaction { ... transaction { ... } }
    @Deprecated("Cannot nest transactions, use tweak", level = DeprecationLevel.ERROR)
    fun transaction(body: TransactionForTest.() -> Unit) {}
}

// Corresponds to the args to Contract.verify
open class TransactionForTest : AbstractTransactionForTest() {
    private val inStates = arrayListOf<ContractState>()
    fun input(s: () -> ContractState) = inStates.add(s())

    protected fun run(time: Instant) {
        val tx = TransactionForVerification(inStates, outStates.map { it.state }, commands, time, SecureHash.randomSHA256())
        tx.verify(TEST_PROGRAM_MAP)
    }

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

    // which is uglier?? :)
    infix fun `fails requirement`(msg: String) = rejects(msg)
    fun fails_requirement(msg: String) = this.`fails requirement`(msg)

    // Use this to create transactions where the output of this transaction is automatically used as an input of
    // the next.
    fun chain(vararg outputLabels: String, body: TransactionForTest.() -> Unit): TransactionForTest {
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
        return tx
    }

    // Allow customisation of partial transactions.
    fun tweak(body: TransactionForTest.() -> Unit): TransactionForTest {
        val tx = TransactionForTest()
        tx.inStates.addAll(inStates)
        tx.outStates.addAll(outStates)
        tx.commands.addAll(commands)
        tx.body()
        return tx
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

fun transaction(body: TransactionForTest.() -> Unit) = TransactionForTest().apply { body() }

class TransactionGroupForTest<out T : ContractState>(private val stateType: Class<T>) {
    open inner class LedgerTransactionForTest : AbstractTransactionForTest() {
        private val inStates = ArrayList<ContractStateRef>()

        fun input(label: String) {
            inStates.add(labelToRefs[label] ?: throw IllegalArgumentException("Unknown label \"$label\""))
        }


        /**
         * Converts to a [LedgerTransaction] with the givn time, the test institution map, and just assigns a random
         * hash (i.e. pretend it was signed)
         */
        fun toLedgerTransaction(time: Instant): LedgerTransaction {
            val wireCmds = commands.map { WireCommand(it.value, it.signers) }
            return WireTransaction(inStates, outStates.map { it.state }, wireCmds).toLedgerTransaction(time, TEST_KEYS_TO_CORP_MAP, SecureHash.randomSHA256())
        }
    }

    val String.output: T get() = labelToOutputs[this] ?: throw IllegalArgumentException("State with label '$this' was not found")

    private inner class InternalLedgerTransactionForTest : LedgerTransactionForTest() {
        fun finaliseAndInsertLabels(time: Instant): LedgerTransaction {
            val ltx = toLedgerTransaction(time)
            for ((index, labelledState) in outStates.withIndex()) {
                if (labelledState.label != null) {
                    labelToRefs[labelledState.label] = ContractStateRef(ltx.hash, index)
                    if (stateType.isInstance(labelledState.state)) {
                        labelToOutputs[labelledState.label] = labelledState.state as T
                    }
                }
            }
            return ltx
        }
    }

    private val rootTxns = ArrayList<LedgerTransaction>()
    private val labelToRefs = HashMap<String, ContractStateRef>()
    private val labelToOutputs = HashMap<String, T>()
    inner class Roots {
        fun transaction(vararg outputStates: LabeledOutput) {
            val outs = outputStates.map { it.state }
            val wtx = WireTransaction(emptyList(), outs, emptyList())
            val ltx = wtx.toLedgerTransaction(TEST_TX_TIME, TEST_KEYS_TO_CORP_MAP, SecureHash.randomSHA256())
            outputStates.forEachIndexed { index, labeledOutput -> labelToRefs[labeledOutput.label!!] = ContractStateRef(ltx.hash, index) }
            rootTxns.add(ltx)
        }

        @Deprecated("Does not nest ", level = DeprecationLevel.ERROR)
        fun roots(body: Roots.() -> Unit) {}
        @Deprecated("Use the vararg form of transaction inside roots", level = DeprecationLevel.ERROR)
        fun transaction(time: Instant = TEST_TX_TIME, body: LedgerTransactionForTest.() -> Unit) {}
    }
    fun roots(body: Roots.() -> Unit) = Roots().apply { body() }

    val txns = ArrayList<LedgerTransaction>()

    fun transaction(time: Instant = TEST_TX_TIME, body: LedgerTransactionForTest.() -> Unit): LedgerTransaction {
        val forTest = InternalLedgerTransactionForTest()
        forTest.body()
        val ltx = forTest.finaliseAndInsertLabels(time)
        txns.add(ltx)
        return ltx
    }

    @Deprecated("Does not nest ", level = DeprecationLevel.ERROR)
    fun transactionGroup(body: TransactionGroupForTest<T>.() -> Unit) {}

    fun toTransactionGroup() = TransactionGroup(txns.map { it }.toSet(), rootTxns.toSet())

    class Failed(val index: Int, cause: Throwable) : Exception("Transaction $index didn't verify", cause)

    fun verify() {
        val group = toTransactionGroup()
        try {
            group.verify(TEST_PROGRAM_MAP)
        } catch (e: TransactionVerificationException) {
            // Let the developer know the index of the transaction that failed.
            val ltx: LedgerTransaction = txns.find { it.hash == e.tx.origHash }!!
            throw Failed(txns.indexOf(ltx) + 1, e)
        }
    }

    fun expectFailureOfTx(index: Int, message: String): Exception {
        val e = assertFailsWith(Failed::class) {
            verify()
        }
        assertEquals(index, e.index)
        if (!e.cause!!.message!!.contains(message))
            throw AssertionError("Exception should have said '$message' but was actually: ${e.cause.message}", e.cause)
        return e
    }
}

inline fun <reified T : ContractState> transactionGroupFor(body: TransactionGroupForTest<T>.() -> Unit) = TransactionGroupForTest<T>(T::class.java).apply { this.body() }
fun transactionGroup(body: TransactionGroupForTest<ContractState>.() -> Unit) = TransactionGroupForTest(ContractState::class.java).apply { this.body() }
