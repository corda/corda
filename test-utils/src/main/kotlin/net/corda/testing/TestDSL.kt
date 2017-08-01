package net.corda.testing

import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.crypto.composite.expandedCompositeKeys
import net.corda.core.crypto.testing.NullSignature
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import java.io.InputStream
import java.security.KeyPair
import java.security.PublicKey
import java.util.*

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Here is a simple DSL for building pseudo-transactions (not the same as the wire protocol) for testing purposes.
//
// Define a transaction like this:
//
// ledger {
//     transaction {
//         input { someExpression }
//         output { someExpression }
//         command { someExpression }
//
//         tweak {
//              ... same thing but works with a copy of the parent, can add inputs/outputs/commands just within this scope.
//         }
//
//         contract.verifies() -> verify() should pass
//         contract `fails with` "some substring of the error message"
//     }
// }
//

/**
 * Here follows implementations of the [LedgerDSLInterpreter] and [TransactionDSLInterpreter] interfaces to be used in
 * tests. Top level primitives [ledger] and [transaction] that bind the interpreter types are also defined here.
 */

@Deprecated(
        message = "ledger doesn't nest, use tweak",
        replaceWith = ReplaceWith("tweak"),
        level = DeprecationLevel.ERROR)
@Suppress("UNUSED_PARAMETER", "unused")
fun TransactionDSLInterpreter.ledger(
        dsl: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.() -> Unit) {
}

@Deprecated(
        message = "transaction doesn't nest, use tweak",
        replaceWith = ReplaceWith("tweak"),
        level = DeprecationLevel.ERROR)
@Suppress("UNUSED_PARAMETER", "unused")
fun TransactionDSLInterpreter.transaction(
        dsl: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) {
}

@Deprecated(
        message = "ledger doesn't nest, use tweak",
        replaceWith = ReplaceWith("tweak"),
        level = DeprecationLevel.ERROR)
@Suppress("UNUSED_PARAMETER", "unused")
fun LedgerDSLInterpreter<TransactionDSLInterpreter>.ledger(
        dsl: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.() -> Unit) {
}

/**
 * If you jumped here from a compiler error make sure the last line of your test tests for a transaction verify or fail.
 * This is a dummy type that can only be instantiated by functions in this module. This way we can ensure that all tests
 * will have as the last line either an accept or a failure test. The name is deliberately long to help make sense of
 * the triggered diagnostic.
 */
sealed class EnforceVerifyOrFail {
    internal object Token : EnforceVerifyOrFail()
}

class DuplicateOutputLabel(label: String) : Exception("Output label '$label' already used")
class DoubleSpentInputs(ids: List<SecureHash>) : Exception("Transactions spend the same input. Conflicting transactions ids: '$ids'")
class AttachmentResolutionException(attachmentId: SecureHash) : Exception("Attachment with id $attachmentId not found")

/**
 * This interpreter builds a transaction, and [TransactionDSL.verifies] that the resolved transaction is correct. Note
 * that transactions corresponding to input states are not verified. Use [LedgerDSL.verifies] for that.
 */
data class TestTransactionDSLInterpreter private constructor(
        override val ledgerInterpreter: TestLedgerDSLInterpreter,
        val transactionBuilder: TransactionBuilder,
        internal val labelToIndexMap: HashMap<String, Int>
) : TransactionDSLInterpreter, OutputStateLookup by ledgerInterpreter {

    constructor(
            ledgerInterpreter: TestLedgerDSLInterpreter,
            transactionBuilder: TransactionBuilder
    ) : this(ledgerInterpreter, transactionBuilder, HashMap())

    val services = object : ServiceHub by ledgerInterpreter.services {
        override fun loadState(stateRef: StateRef) = ledgerInterpreter.resolveStateRef<ContractState>(stateRef)
    }

    private fun copy(): TestTransactionDSLInterpreter =
            TestTransactionDSLInterpreter(
                    ledgerInterpreter = ledgerInterpreter,
                    transactionBuilder = transactionBuilder.copy(),
                    labelToIndexMap = HashMap(labelToIndexMap)
            )

    internal fun toWireTransaction() = transactionBuilder.toWireTransaction()

    override fun input(stateRef: StateRef) {
        val state = ledgerInterpreter.resolveStateRef<ContractState>(stateRef)
        transactionBuilder.addInputState(StateAndRef(state, stateRef))
    }

    override fun _output(label: String?, notary: Party, encumbrance: Int?, contractState: ContractState) {
        transactionBuilder.addOutputState(contractState, notary, encumbrance)
        if (label != null) {
            if (label in labelToIndexMap) {
                throw DuplicateOutputLabel(label)
            } else {
                val outputIndex = transactionBuilder.outputStates().size - 1
                labelToIndexMap[label] = outputIndex
            }
        }
    }

    override fun attachment(attachmentId: SecureHash) {
        transactionBuilder.addAttachment(attachmentId)
    }

    override fun _command(signers: List<PublicKey>, commandData: CommandData) {
        val command = Command(commandData, signers)
        transactionBuilder.addCommand(command)
    }

    override fun verifies(): EnforceVerifyOrFail {
        // Verify on a copy of the transaction builder, so if it's then further modified it doesn't error due to
        // the existing signature
        transactionBuilder.copy().apply {
            toWireTransaction().toLedgerTransaction(services).verify()
        }
        return EnforceVerifyOrFail.Token
    }

    override fun timeWindow(data: TimeWindow) {
        transactionBuilder.setTimeWindow(data)
    }

    override fun tweak(
            dsl: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail
    ) = dsl(TransactionDSL(copy()))
}

data class TestLedgerDSLInterpreter private constructor(
        val services: ServiceHub,
        internal val labelToOutputStateAndRefs: HashMap<String, StateAndRef<ContractState>> = HashMap(),
        private val transactionWithLocations: HashMap<SecureHash, WireTransactionWithLocation> = LinkedHashMap(),
        private val nonVerifiedTransactionWithLocations: HashMap<SecureHash, WireTransactionWithLocation> = HashMap()
) : LedgerDSLInterpreter<TestTransactionDSLInterpreter> {
    val wireTransactions: List<WireTransaction> get() = transactionWithLocations.values.map { it.transaction }

    // We specify [labelToOutputStateAndRefs] just so that Kotlin picks the primary constructor instead of cycling
    constructor(services: ServiceHub) : this(services, labelToOutputStateAndRefs = HashMap())

    companion object {
        private fun getCallerLocation(): String? {
            val stackTrace = Thread.currentThread().stackTrace
            for (i in 1..stackTrace.size) {
                val stackTraceElement = stackTrace[i]
                if (!stackTraceElement.fileName.contains("DSL")) {
                    return stackTraceElement.toString()
                }
            }
            return null
        }
    }

    internal data class WireTransactionWithLocation(
            val label: String?,
            val transaction: WireTransaction,
            val location: String?
    )

    class VerifiesFailed(transactionName: String, cause: Throwable) :
            Exception("Transaction ($transactionName) didn't verify: $cause", cause)

    class TypeMismatch(requested: Class<*>, actual: Class<*>) :
            Exception("Actual type $actual is not a subtype of requested type $requested")

    internal fun copy(): TestLedgerDSLInterpreter =
            TestLedgerDSLInterpreter(
                    services,
                    labelToOutputStateAndRefs = HashMap(labelToOutputStateAndRefs),
                    transactionWithLocations = HashMap(transactionWithLocations),
                    nonVerifiedTransactionWithLocations = HashMap(nonVerifiedTransactionWithLocations)
            )

    internal inline fun <reified S : ContractState> resolveStateRef(stateRef: StateRef): TransactionState<S> {
        val transactionWithLocation =
                transactionWithLocations[stateRef.txhash] ?:
                        nonVerifiedTransactionWithLocations[stateRef.txhash] ?:
                        throw TransactionResolutionException(stateRef.txhash)
        val output = transactionWithLocation.transaction.outputs[stateRef.index]
        return if (S::class.java.isAssignableFrom(output.data.javaClass)) @Suppress("UNCHECKED_CAST") {
            output as TransactionState<S>
        } else {
            throw TypeMismatch(requested = S::class.java, actual = output.data.javaClass)
        }
    }

    internal fun resolveAttachment(attachmentId: SecureHash): Attachment {
        return services.attachments.openAttachment(attachmentId) ?: throw AttachmentResolutionException(attachmentId)
    }

    private fun <R> interpretTransactionDsl(
            transactionBuilder: TransactionBuilder,
            dsl: TransactionDSL<TestTransactionDSLInterpreter>.() -> R
    ): TestTransactionDSLInterpreter {
        val transactionInterpreter = TestTransactionDSLInterpreter(this, transactionBuilder)
        dsl(TransactionDSL(transactionInterpreter))
        return transactionInterpreter
    }

    fun transactionName(transactionHash: SecureHash): String? {
        val transactionWithLocation = transactionWithLocations[transactionHash]
        return if (transactionWithLocation != null) {
            transactionWithLocation.label ?: "TX[${transactionWithLocation.location}]"
        } else {
            null
        }
    }

    fun outputToLabel(state: ContractState): String? =
            labelToOutputStateAndRefs.filter { it.value.state.data == state }.keys.firstOrNull()

    private fun <R> recordTransactionWithTransactionMap(
            transactionLabel: String?,
            transactionBuilder: TransactionBuilder,
            dsl: TransactionDSL<TestTransactionDSLInterpreter>.() -> R,
            transactionMap: HashMap<SecureHash, WireTransactionWithLocation> = HashMap()
    ): WireTransaction {
        val transactionLocation = getCallerLocation()
        val transactionInterpreter = interpretTransactionDsl(transactionBuilder, dsl)
        // Create the WireTransaction
        val wireTransaction = transactionInterpreter.toWireTransaction()
        // Record the output states
        transactionInterpreter.labelToIndexMap.forEach { label, index ->
            if (label in labelToOutputStateAndRefs) {
                throw DuplicateOutputLabel(label)
            }
            labelToOutputStateAndRefs[label] = wireTransaction.outRef(index)
        }
        transactionMap[wireTransaction.id] =
                WireTransactionWithLocation(transactionLabel, wireTransaction, transactionLocation)

        return wireTransaction
    }

    override fun _transaction(
            transactionLabel: String?,
            transactionBuilder: TransactionBuilder,
            dsl: TransactionDSL<TestTransactionDSLInterpreter>.() -> EnforceVerifyOrFail
    ) = recordTransactionWithTransactionMap(transactionLabel, transactionBuilder, dsl, transactionWithLocations)

    override fun _unverifiedTransaction(
            transactionLabel: String?,
            transactionBuilder: TransactionBuilder,
            dsl: TransactionDSL<TestTransactionDSLInterpreter>.() -> Unit
    ) = recordTransactionWithTransactionMap(transactionLabel, transactionBuilder, dsl, nonVerifiedTransactionWithLocations)

    override fun tweak(
            dsl: LedgerDSL<TestTransactionDSLInterpreter,
                    LedgerDSLInterpreter<TestTransactionDSLInterpreter>>.() -> Unit) =
            dsl(LedgerDSL(copy()))

    override fun attachment(attachment: InputStream): SecureHash {
        return services.attachments.importAttachment(attachment)
    }

    override fun verifies(): EnforceVerifyOrFail {
        try {
            val usedInputs = mutableSetOf<StateRef>()
            services.recordTransactions(transactionsUnverified.map { SignedTransaction(it.serialized, listOf(NullSignature)) })
            for ((_, value) in transactionWithLocations) {
                val wtx = value.transaction
                val ltx = wtx.toLedgerTransaction(services)
                ltx.verify()
                val doubleSpend = wtx.inputs.intersect(usedInputs)
                if (!doubleSpend.isEmpty()) {
                    val txIds = mutableListOf(wtx.id)
                    doubleSpend.mapTo(txIds) { it.txhash }
                    throw DoubleSpentInputs(txIds)
                }
                usedInputs.addAll(wtx.inputs)
                services.recordTransactions(SignedTransaction(wtx.serialized, listOf(NullSignature)))
            }
            return EnforceVerifyOrFail.Token
        } catch (exception: TransactionVerificationException) {
            val transactionWithLocation = transactionWithLocations[exception.txId]
            val transactionName = transactionWithLocation?.label ?: transactionWithLocation?.location ?: "<unknown>"
            throw VerifiesFailed(transactionName, exception)
        }
    }

    override fun <S : ContractState> retrieveOutputStateAndRef(clazz: Class<S>, label: String): StateAndRef<S> {
        val stateAndRef = labelToOutputStateAndRefs[label]
        if (stateAndRef == null) {
            throw IllegalArgumentException("State with label '$label' was not found")
        } else if (!clazz.isAssignableFrom(stateAndRef.state.data.javaClass)) {
            throw TypeMismatch(requested = clazz, actual = stateAndRef.state.data.javaClass)
        } else {
            @Suppress("UNCHECKED_CAST")
            return stateAndRef as StateAndRef<S>
        }
    }

    val transactionsToVerify: List<WireTransaction> get() = transactionWithLocations.values.map { it.transaction }
    val transactionsUnverified: List<WireTransaction> get() = nonVerifiedTransactionWithLocations.values.map { it.transaction }
}

/**
 * Signs all transactions passed in.
 * @param transactionsToSign Transactions to be signed.
 * @param extraKeys extra keys to sign transactions with.
 * @return List of [SignedTransaction]s.
 */
fun signAll(transactionsToSign: List<WireTransaction>, extraKeys: List<KeyPair>) = transactionsToSign.map { wtx ->
    check(wtx.mustSign.isNotEmpty())
    val bits = wtx.serialize()
    require(bits == wtx.serialized)
    val signatures = ArrayList<DigitalSignature.WithKey>()
    val keyLookup = HashMap<PublicKey, KeyPair>()

    (ALL_TEST_KEYS + extraKeys).forEach {
        keyLookup[it.public] = it
    }
    wtx.mustSign.expandedCompositeKeys.forEach {
        val key = keyLookup[it] ?: throw IllegalArgumentException("Missing required key for ${it.toStringShort()}")
        signatures += key.sign(wtx.id)
    }
    SignedTransaction(bits, signatures)
}

/**
 * Signs all transactions in the ledger.
 * @param extraKeys extra keys to sign transactions with.
 * @return List of [SignedTransaction]s.
 */
fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.signAll(
        vararg extraKeys: KeyPair) = signAll(this.interpreter.wireTransactions, extraKeys.toList())
