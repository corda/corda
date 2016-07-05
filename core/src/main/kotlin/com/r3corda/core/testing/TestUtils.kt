@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")

package com.r3corda.core.testing

import com.google.common.base.Throwables
import com.google.common.net.HostAndPort
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.*
import com.r3corda.core.node.services.IdentityService
import com.r3corda.core.node.services.StorageService
import com.r3corda.core.node.services.testing.MockIdentityService
import com.r3corda.core.node.services.testing.MockStorageService
import com.r3corda.core.seconds
import com.r3corda.core.serialization.serialize
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

/**
 *  JAVA INTEROP. Please keep the following points in mind when extending the Kotlin DSL
 *
 *   - Annotate functions with Kotlin defaults with @JvmOverloads. This produces the relevant overloads for Java.
 *   - Void closures in arguments are inconvenient in Java, use overloading to define non-closure variants as well.
 *   - Top-level funs should be defined in a [JavaTestHelpers] object and annotated with @JvmStatic first and should be
 *     referred to from the global fun. This allows static importing of [JavaTestHelpers] in Java tests, which mimicks
 *     top-level funs.
 *   - Top-level vals are trickier. *DO NOT USE @JvmField INSIDE [JavaTestHelpers]*. It's surprisingly easy to
 *     introduce a static init cycle because of the way Kotlin compiles top-level things, which can cause
 *     non-deterministic behaviour, including your field not being initialized at all! Instead opt for a proper Kotlin
 *     val either with a custom @JvmStatic get() or a lazy delegate if the initialiser has side-effects See examples below.
 *   - Infix functions work as regular ones from Java, but symbols with spaces in them don't! Define a camelCase variant
 *     as well.
 *   - varargs are exposed as array types in Java. Define overloads for common cases.
 *   - The Int.DOLLARS syntax doesn't work from Java. To remedy add a @JvmStatic DOLLARS(Int) function to
 *     [JavaTestHelpers]
 */
object JavaTestHelpers {
    // A dummy time at which we will be pretending test transactions are created.
    @JvmStatic val TEST_TX_TIME: Instant get() = Instant.parse("2015-04-17T12:00:00.00Z")

    // A few dummy values for testing.
    @JvmStatic val MEGA_CORP_KEY: KeyPair get() = TestUtils.keypair
    @JvmStatic val MEGA_CORP_PUBKEY: PublicKey get() = MEGA_CORP_KEY.public

    @JvmStatic val MINI_CORP_KEY: KeyPair get() = TestUtils.keypair2
    @JvmStatic val MINI_CORP_PUBKEY: PublicKey get() = MINI_CORP_KEY.public

    @JvmStatic val ORACLE_KEY: KeyPair get() = TestUtils.keypair3
    @JvmStatic val ORACLE_PUBKEY: PublicKey get() = ORACLE_KEY.public

    @JvmStatic val DUMMY_PUBKEY_1: PublicKey get() = DummyPublicKey("x1")
    @JvmStatic val DUMMY_PUBKEY_2: PublicKey get() = DummyPublicKey("x2")

    @JvmStatic val ALICE_KEY: KeyPair by lazy { generateKeyPair() }
    @JvmStatic val ALICE_PUBKEY: PublicKey get() = ALICE_KEY.public
    @JvmStatic val ALICE: Party get() = Party("Alice", ALICE_PUBKEY)

    @JvmStatic val BOB_KEY: KeyPair by lazy { generateKeyPair() }
    @JvmStatic val BOB_PUBKEY: PublicKey get() = BOB_KEY.public
    @JvmStatic val BOB: Party get() = Party("Bob", BOB_PUBKEY)

    @JvmStatic val MEGA_CORP: Party get() = Party("MegaCorp", MEGA_CORP_PUBKEY)
    @JvmStatic val MINI_CORP: Party get() = Party("MiniCorp", MINI_CORP_PUBKEY)

    @JvmStatic val DUMMY_NOTARY_KEY: KeyPair by lazy { generateKeyPair() }
    @JvmStatic val DUMMY_NOTARY: Party get() = Party("Notary Service", DUMMY_NOTARY_KEY.public)

    @JvmStatic val ALL_TEST_KEYS: List<KeyPair> get() = listOf(MEGA_CORP_KEY, MINI_CORP_KEY, ALICE_KEY, BOB_KEY, DUMMY_NOTARY_KEY)

    @JvmStatic val MOCK_IDENTITY_SERVICE: MockIdentityService get() = MockIdentityService(listOf(MEGA_CORP, MINI_CORP, DUMMY_NOTARY))

    @JvmStatic fun generateStateRef() = StateRef(SecureHash.randomSHA256(), 0)

    @JvmStatic @JvmOverloads fun ledger(
            identityService: IdentityService = MOCK_IDENTITY_SERVICE,
            storageService: StorageService = MockStorageService(),
            dsl: LedgerDsl<LastLineShouldTestForVerifiesOrFails, TestTransactionDslInterpreter, TestLedgerDslInterpreter>.() -> Unit
    ): LedgerDsl<LastLineShouldTestForVerifiesOrFails, TestTransactionDslInterpreter, TestLedgerDslInterpreter> {
        val ledgerDsl = LedgerDsl(TestLedgerDslInterpreter(identityService, storageService))
        dsl(ledgerDsl)
        return ledgerDsl
    }

}

val TEST_TX_TIME = JavaTestHelpers.TEST_TX_TIME
val MEGA_CORP_KEY = JavaTestHelpers.MEGA_CORP_KEY
val MEGA_CORP_PUBKEY = JavaTestHelpers.MEGA_CORP_PUBKEY
val MINI_CORP_KEY = JavaTestHelpers.MINI_CORP_KEY
val MINI_CORP_PUBKEY = JavaTestHelpers.MINI_CORP_PUBKEY
val ORACLE_KEY = JavaTestHelpers.ORACLE_KEY
val ORACLE_PUBKEY = JavaTestHelpers.ORACLE_PUBKEY
val DUMMY_PUBKEY_1 = JavaTestHelpers.DUMMY_PUBKEY_1
val DUMMY_PUBKEY_2 = JavaTestHelpers.DUMMY_PUBKEY_2
val ALICE_KEY = JavaTestHelpers.ALICE_KEY
val ALICE_PUBKEY = JavaTestHelpers.ALICE_PUBKEY
val ALICE = JavaTestHelpers.ALICE
val BOB_KEY = JavaTestHelpers.BOB_KEY
val BOB_PUBKEY = JavaTestHelpers.BOB_PUBKEY
val BOB = JavaTestHelpers.BOB
val MEGA_CORP = JavaTestHelpers.MEGA_CORP
val MINI_CORP = JavaTestHelpers.MINI_CORP
val DUMMY_NOTARY_KEY = JavaTestHelpers.DUMMY_NOTARY_KEY
val DUMMY_NOTARY = JavaTestHelpers.DUMMY_NOTARY
val ALL_TEST_KEYS = JavaTestHelpers.ALL_TEST_KEYS
val MOCK_IDENTITY_SERVICE = JavaTestHelpers.MOCK_IDENTITY_SERVICE

fun generateStateRef() = JavaTestHelpers.generateStateRef()

fun transaction(body: TransactionForTest.() -> LastLineShouldTestForAcceptOrFailure) = JavaTestHelpers.transaction(body)

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
class LabeledOutput(val label: String?, val state: TransactionState<*>) {
    override fun toString() = state.toString() + (if (label != null) " ($label)" else "")
    override fun equals(other: Any?) = other is LabeledOutput && state.equals(other.state)
    override fun hashCode(): Int = state.hashCode()
}

infix fun TransactionState<*>.label(label: String) = LabeledOutput(label, this)

abstract class AbstractTransactionForTest {
    protected val attachments = ArrayList<SecureHash>()
    protected val outStates = ArrayList<LabeledOutput>()
    protected val commands = ArrayList<Command>()
    protected val signers = LinkedHashSet<PublicKey>()
    protected val type = TransactionType.General()

    @JvmOverloads
    open fun output(label: String? = null, s: () -> ContractState) = LabeledOutput(label, TransactionState(s(), DUMMY_NOTARY)).apply { outStates.add(this) }
    @JvmOverloads
    open fun output(label: String? = null, s: ContractState) = output(label) { s }

    protected fun commandsToAuthenticatedObjects(): List<AuthenticatedObject<CommandData>> {
        return commands.map { AuthenticatedObject(it.signers, it.signers.mapNotNull { MOCK_IDENTITY_SERVICE.partyFromKey(it) }, it.value) }
    }

    fun attachment(attachmentID: SecureHash) {
        attachments.add(attachmentID)
    }

    fun arg(vararg keys: PublicKey, c: () -> CommandData) {
        val keysList = listOf(*keys)
        addCommand(Command(c(), keysList))
    }
    fun arg(key: PublicKey, c: CommandData) = arg(key) { c }

    fun timestamp(time: Instant) {
        val data = TimestampCommand(time, 30.seconds)
        timestamp(data)
    }

    fun timestamp(data: TimestampCommand) {
        addCommand(Command(data, DUMMY_NOTARY.owningKey))
    }

    fun addCommand(cmd: Command) {
        signers.addAll(cmd.signers)
        commands.add(cmd)
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
// Note on defaults: try to avoid Kotlin defaults as they don't work from Java. Instead define overloads
open class TransactionForTest : AbstractTransactionForTest() {
    private val inStates = arrayListOf<TransactionState<ContractState>>()

    fun input(s: () -> ContractState) {
        signers.add(DUMMY_NOTARY.owningKey)
        inStates.add(TransactionState(s(), DUMMY_NOTARY))
    }
    fun input(s: ContractState) = input { s }

    protected fun runCommandsAndVerify() {
        val cmds = commandsToAuthenticatedObjects()
        val tx = TransactionForVerification(inStates, outStates.map { it.state }, emptyList(), cmds, SecureHash.Companion.randomSHA256(), signers.toList(), type)
        tx.verify()
    }

    fun accepts(): LastLineShouldTestForAcceptOrFailure {
        runCommandsAndVerify()
        return LastLineShouldTestForAcceptOrFailure.Token
    }

    @JvmOverloads
    fun rejects(withMessage: String? = null): LastLineShouldTestForAcceptOrFailure {
        val r = try {
            runCommandsAndVerify()
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
        return LastLineShouldTestForAcceptOrFailure.Token
    }

    /**
     * Used to confirm that the test, when (implicitly) run against the .verify() method, fails with the text of the message
     */
    infix fun `fails requirement`(msg: String): LastLineShouldTestForAcceptOrFailure = rejects(msg)
    fun failsRequirement(msg: String) = this.`fails requirement`(msg)

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

        tx.signers.addAll(tx.inStates.map { it.notary.owningKey })
        tx.signers.addAll(commands.flatMap { it.signers })
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
