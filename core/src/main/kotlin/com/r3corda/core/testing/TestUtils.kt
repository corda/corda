@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")

package com.r3corda.core.testing

import com.google.common.base.Throwables
import com.google.common.net.HostAndPort
import com.r3corda.core.*
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.*
import com.r3corda.core.serialization.serialize
import com.r3corda.core.node.services.testing.MockIdentityService
import com.r3corda.core.node.services.testing.MockStorageService
import java.net.ServerSocket
import java.security.KeyPair
import java.security.PublicKey
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

/** If an exception is thrown by the body, rethrows the root cause exception. */
inline fun <R> rootCauseExceptions(body: () -> R): R {
    try {
        return body()
    } catch(e: Exception) {
        throw Throwables.getRootCause(e)
    }
}

fun freeLocalHostAndPort(): HostAndPort {
    val freePort = ServerSocket(0).use { it.localPort }
    return HostAndPort.fromParts("localhost", freePort)
}

object TestUtils {
    val keypair = generateKeyPair()
    val keypair2 = generateKeyPair()
    val keypair3 = generateKeyPair()
}

// A dummy time at which we will be pretending test transactions are created.
val TEST_TX_TIME = Instant.parse("2015-04-17T12:00:00.00Z")

// A few dummy values for testing.
val MEGA_CORP_KEY = TestUtils.keypair
val MEGA_CORP_PUBKEY = MEGA_CORP_KEY.public

val MINI_CORP_KEY = TestUtils.keypair2
val MINI_CORP_PUBKEY = MINI_CORP_KEY.public

val ORACLE_KEY = TestUtils.keypair3
val ORACLE_PUBKEY = ORACLE_KEY.public

val DUMMY_PUBKEY_1 = DummyPublicKey("x1")
val DUMMY_PUBKEY_2 = DummyPublicKey("x2")

val ALICE_KEY = generateKeyPair()
val ALICE_PUBKEY = ALICE_KEY.public
val ALICE = Party("Alice", ALICE_PUBKEY)

val BOB_KEY = generateKeyPair()
val BOB_PUBKEY = BOB_KEY.public
val BOB = Party("Bob", BOB_PUBKEY)

val MEGA_CORP = Party("MegaCorp", MEGA_CORP_PUBKEY)
val MINI_CORP = Party("MiniCorp", MINI_CORP_PUBKEY)

val DUMMY_NOTARY_KEY = generateKeyPair()
val DUMMY_NOTARY = Party("Notary Service", DUMMY_NOTARY_KEY.public)

val ALL_TEST_KEYS = listOf(MEGA_CORP_KEY, MINI_CORP_KEY, ALICE_KEY, BOB_KEY, DUMMY_NOTARY_KEY)

val MOCK_IDENTITY_SERVICE = MockIdentityService(listOf(MEGA_CORP, MINI_CORP, DUMMY_NOTARY))

fun generateStateRef() = StateRef(SecureHash.randomSHA256(), 0)

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

class LabeledOutput(val label: String?, val state: ContractState) {
    override fun toString() = state.toString() + (if (label != null) " ($label)" else "")
    override fun equals(other: Any?) = other is LabeledOutput && state.equals(other.state)
    override fun hashCode(): Int = state.hashCode()
}

infix fun ContractState.label(label: String) = LabeledOutput(label, this)

abstract class AbstractTransactionForTest {
    protected val attachments = ArrayList<SecureHash>()
    protected val outStates = ArrayList<LabeledOutput>()
    protected val commands = ArrayList<Command>()

    open fun output(label: String? = null, s: () -> ContractState) = LabeledOutput(label, s()).apply { outStates.add(this) }

    protected fun commandsToAuthenticatedObjects(): List<AuthenticatedObject<CommandData>> {
        return commands.map { AuthenticatedObject(it.signers, it.signers.mapNotNull { MOCK_IDENTITY_SERVICE.partyFromKey(it) }, it.value) }
    }

    fun attachment(attachmentID: SecureHash) {
        attachments.add(attachmentID)
    }

    fun arg(vararg key: PublicKey, c: () -> CommandData) {
        val keys = listOf(*key)
        commands.add(Command(c(), keys))
    }

    fun timestamp(time: Instant) {
        val data = TimestampCommand(time, 30.seconds)
        timestamp(data)
    }

    fun timestamp(data: TimestampCommand) {
        commands.add(Command(data, DUMMY_NOTARY.owningKey))
    }

    // Forbid patterns like:  transaction { ... transaction { ... } }
    @Deprecated("Cannot nest transactions, use tweak", level = DeprecationLevel.ERROR)
    fun transaction(body: TransactionForTest.() -> LastLineShouldTestForAcceptOrFailure) {
    }
}

/** If you jumped here from a compiler error make sure the last line of your test tests for a transaction accept or fail
 *  This is a dummy type that can only be instantiated by functions in this module. This way we can ensure that all tests
 *  will have as the last line either an accept or a failure test. The name is deliberately long to help make sense of
 *  the triggered diagnostic
 */
sealed class LastLineShouldTestForAcceptOrFailure {
    internal object Token: LastLineShouldTestForAcceptOrFailure()
}

// Corresponds to the args to Contract.verify
open class TransactionForTest : AbstractTransactionForTest() {
    private val inStates = arrayListOf<ContractState>()
    fun input(s: () -> ContractState) = inStates.add(s())

    protected fun runCommandsAndVerify(time: Instant) {
        val cmds = commandsToAuthenticatedObjects()
        val tx = TransactionForVerification(inStates, outStates.map { it.state }, emptyList(), cmds, SecureHash.Companion.randomSHA256())
        tx.verify()
    }

    fun accepts(time: Instant = TEST_TX_TIME): LastLineShouldTestForAcceptOrFailure {
        runCommandsAndVerify(time)
        return LastLineShouldTestForAcceptOrFailure.Token
    }
    fun rejects(withMessage: String? = null, time: Instant = TEST_TX_TIME) {
        val r = try {
            runCommandsAndVerify(time)
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

    /**
     * Used to confirm that the test, when (implicitly) run against the .verify() method, fails with the text of the message
     */
    infix fun `fails requirement`(msg: String): LastLineShouldTestForAcceptOrFailure {
        rejects(msg)
        return LastLineShouldTestForAcceptOrFailure.Token
    }

    fun fails_requirement(msg: String) = this.`fails requirement`(msg)

    // Use this to create transactions where the output of this transaction is automatically used as an input of
    // the next.
    fun chain(vararg outputLabels: String, body: TransactionForTest.() -> LastLineShouldTestForAcceptOrFailure): TransactionForTest {
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
    fun tweak(body: TransactionForTest.() -> LastLineShouldTestForAcceptOrFailure): LastLineShouldTestForAcceptOrFailure {
        val tx = TransactionForTest()
        tx.inStates.addAll(inStates)
        tx.outStates.addAll(outStates)
        tx.commands.addAll(commands)
        return tx.body()
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

fun transaction(body: TransactionForTest.() -> LastLineShouldTestForAcceptOrFailure): LastLineShouldTestForAcceptOrFailure {
    return body(TransactionForTest())
}

class TransactionGroupDSL<T : ContractState>(private val stateType: Class<T>) {
    open inner class WireTransactionDSL : AbstractTransactionForTest() {
        private val inStates = ArrayList<StateRef>()

        fun input(label: String) {
            inStates.add(label.outputRef)
        }

        fun toWireTransaction() = WireTransaction(inStates, attachments, outStates.map { it.state }, commands)
    }

    val String.output: T get() = labelToOutputs[this] ?: throw IllegalArgumentException("State with label '$this' was not found")
    val String.outputRef: StateRef get() = labelToRefs[this] ?: throw IllegalArgumentException("Unknown label \"$this\"")

    fun <C : ContractState> lookup(label: String) = StateAndRef(label.output as C, label.outputRef)

    private inner class InternalWireTransactionDSL : WireTransactionDSL() {
        fun finaliseAndInsertLabels(): WireTransaction {
            val wtx = toWireTransaction()
            for ((index, labelledState) in outStates.withIndex()) {
                if (labelledState.label != null) {
                    labelToRefs[labelledState.label] = StateRef(wtx.id, index)
                    if (stateType.isInstance(labelledState.state)) {
                        labelToOutputs[labelledState.label] = labelledState.state as T
                    }
                    outputsToLabels[labelledState.state] = labelledState.label
                }
            }
            return wtx
        }
    }

    private val rootTxns = ArrayList<WireTransaction>()
    private val labelToRefs = HashMap<String, StateRef>()
    private val labelToOutputs = HashMap<String, T>()
    private val outputsToLabels = HashMap<ContractState, String>()

    fun labelForState(state: T): String? = outputsToLabels[state]

    inner class Roots {
        fun transaction(vararg outputStates: LabeledOutput) {
            val outs = outputStates.map { it.state }
            val wtx = WireTransaction(emptyList(), emptyList(), outs, emptyList())
            for ((index, state) in outputStates.withIndex()) {
                val label = state.label!!
                labelToRefs[label] = StateRef(wtx.id, index)
                outputsToLabels[state.state] = label
                labelToOutputs[label] = state.state as T
            }
            rootTxns.add(wtx)
        }

        /**
         * Note: Don't delete, this is intended to trigger compiler diagnostic when the DSL primitive is used in the wrong place
         */
        @Deprecated("Does not nest ", level = DeprecationLevel.ERROR)
        fun roots(body: Roots.() -> Unit) {
        }

        /**
         * Note: Don't delete, this is intended to trigger compiler diagnostic when the DSL primitive is used in the wrong place
         */
        @Deprecated("Use the vararg form of transaction inside roots", level = DeprecationLevel.ERROR)
        fun transaction(body: WireTransactionDSL.() -> Unit) {
        }
    }

    fun roots(body: Roots.() -> Unit) = Roots().apply { body() }

    val txns = ArrayList<WireTransaction>()
    private val txnToLabelMap = HashMap<SecureHash, String>()

    fun transaction(label: String? = null, body: WireTransactionDSL.() -> Unit): WireTransaction {
        val forTest = InternalWireTransactionDSL()
        forTest.body()
        val wtx = forTest.finaliseAndInsertLabels()
        txns.add(wtx)
        if (label != null)
            txnToLabelMap[wtx.id] = label
        return wtx
    }

    fun labelForTransaction(tx: WireTransaction): String? = txnToLabelMap[tx.id]
    fun labelForTransaction(tx: LedgerTransaction): String? = txnToLabelMap[tx.id]

    /**
     * Note: Don't delete, this is intended to trigger compiler diagnostic when the DSL primitive is used in the wrong place
     */
    @Deprecated("Does not nest ", level = DeprecationLevel.ERROR)
    fun transactionGroup(body: TransactionGroupDSL<T>.() -> Unit) {
    }

    fun toTransactionGroup() = TransactionGroup(
            txns.map { it.toLedgerTransaction(MOCK_IDENTITY_SERVICE, MockStorageService().attachments) }.toSet(),
            rootTxns.map { it.toLedgerTransaction(MOCK_IDENTITY_SERVICE, MockStorageService().attachments) }.toSet()
    )

    class Failed(val index: Int, cause: Throwable) : Exception("Transaction $index didn't verify", cause)

    fun verify() {
        val group = toTransactionGroup()
        try {
            group.verify()
        } catch (e: TransactionVerificationException) {
            // Let the developer know the index of the transaction that failed.
            val wtx: WireTransaction = txns.find { it.id == e.tx.origHash }!!
            throw Failed(txns.indexOf(wtx) + 1, e)
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

    fun signAll(txnsToSign: List<WireTransaction> = txns, vararg extraKeys: KeyPair): List<SignedTransaction> {
        return txnsToSign.map { wtx ->
            val allPubKeys = wtx.commands.flatMap { it.signers }.toMutableSet()
            val bits = wtx.serialize()
            require(bits == wtx.serialized)
            val sigs = ArrayList<DigitalSignature.WithKey>()
            for (key in ALL_TEST_KEYS + extraKeys) {
                if (allPubKeys.contains(key.public)) {
                    sigs += key.signWithECDSA(bits)
                    allPubKeys -= key.public
                }
            }
            SignedTransaction(bits, sigs)
        }
    }
}

inline fun <reified T : ContractState> transactionGroupFor(body: TransactionGroupDSL<T>.() -> Unit) = TransactionGroupDSL<T>(T::class.java).apply { this.body() }
fun transactionGroup(body: TransactionGroupDSL<ContractState>.() -> Unit) = TransactionGroupDSL(ContractState::class.java).apply { this.body() }
