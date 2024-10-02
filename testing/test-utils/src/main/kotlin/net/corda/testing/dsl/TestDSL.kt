package net.corda.testing.dsl

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.core.DoNotImplement
import net.corda.core.contracts.*
import net.corda.core.crypto.NullKeys.NULL_SIGNATURE
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowException
import net.corda.core.flows.TransactionMetadata
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.internal.cordapp.CordappProviderInternal
import net.corda.core.internal.notary.NotaryService
import net.corda.core.internal.verification.ExternalVerifierHandle
import net.corda.core.internal.verification.toVerifyingServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.TransactionStorage
import net.corda.core.serialization.internal.AttachmentsClassLoaderCache
import net.corda.core.serialization.internal.AttachmentsClassLoaderCacheImpl
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.node.services.DbTransactionsResolver
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.attachments.NodeAttachmentTrustCalculator
import net.corda.node.services.persistence.toInternal
import net.corda.testing.core.dummyCommand
import net.corda.testing.internal.MockCordappProvider
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.services.MockAttachmentStorage
import java.io.InputStream
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

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
 * If you jumped here from a compiler error make sure the last line of your test tests for a transaction verify or fail.
 * This is a dummy type that can only be instantiated by functions in this module. This way we can ensure that all tests
 * will have as the last line either an accept or a failure test. The name is deliberately long to help make sense of
 * the triggered diagnostic.
 */
@DoNotImplement
sealed class EnforceVerifyOrFail {
    internal object Token : EnforceVerifyOrFail()
}

class DuplicateOutputLabel(label: String) : FlowException("Output label '$label' already used")
class DoubleSpentInputs(ids: List<SecureHash>) : FlowException("Transactions spend the same input. Conflicting transactions ids: '$ids'")
class AttachmentResolutionException(attachmentId: SecureHash) : FlowException("Attachment with id $attachmentId not found")

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

    // Implementing [ServiceHubCoreInternal] allows better use in internal Corda tests
    val services: ServicesForResolution = object : ServiceHubCoreInternal, ServiceHub by ledgerInterpreter.services {
        // [validatedTransactions.getTransaction] needs overriding as there are no calls to
        // [ServiceHub.recordTransactions] in the test dsl
        override val validatedTransactions: TransactionStorage =
            object : TransactionStorage by ledgerInterpreter.services.validatedTransactions {
                override fun getTransaction(id: SecureHash): SignedTransaction? =
                    ledgerInterpreter.getTransaction(id)
            }

        override val externalOperationExecutor: ExecutorService = Executors.newFixedThreadPool(
            2,
            ThreadFactoryBuilder().setNameFormat("flow-external-operation-thread").build()
        )

        override val rotatedKeys: RotatedKeys = ledgerInterpreter.services.toVerifyingServiceHub().rotatedKeys

        override val attachmentTrustCalculator: AttachmentTrustCalculator =
            ledgerInterpreter.services.attachments.let {
                // Wrapping to a [InternalMockAttachmentStorage] is needed to prevent leaking internal api
                // while still allowing the tests to work
                NodeAttachmentTrustCalculator(attachmentStorage = it.toInternal(), cacheFactory = TestingNamedCacheFactory(), rotatedKeys = rotatedKeys)
            }

        override fun createTransactionsResolver(flow: ResolveTransactionsFlow): TransactionsResolver =
            DbTransactionsResolver(flow)

        override fun loadState(stateRef: StateRef) =
            ledgerInterpreter.resolveStateRef<ContractState>(stateRef)

        override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> {
            return ledgerInterpreter.services.loadStates(stateRefs)
        }

        override val cordappProvider: CordappProviderInternal
            get() = ledgerInterpreter.services.cordappProvider as CordappProviderInternal

        override val notaryService: NotaryService? = null

        override val attachmentsClassLoaderCache: AttachmentsClassLoaderCache = AttachmentsClassLoaderCacheImpl(TestingNamedCacheFactory())

        override fun loadContractAttachment(stateRef: StateRef): Attachment {
            return ledgerInterpreter.services.loadContractAttachment(stateRef)
        }

        override val externalVerifierHandle: ExternalVerifierHandle
            get() = throw UnsupportedOperationException("Verification of legacy transactions is not supported by TestTransactionDSLInterpreter")

        override fun recordUnnotarisedTransaction(txn: SignedTransaction) {}

        override fun removeUnnotarisedTransaction(id: SecureHash) {}

        override fun finalizeTransactionWithExtraSignatures(txn: SignedTransaction, sigs: Collection<TransactionSignature>, statesToRecord: StatesToRecord) {}

        override fun finalizeTransaction(txn: SignedTransaction, statesToRecord: StatesToRecord) {}

        override fun recordSenderTransactionRecoveryMetadata(txnId: SecureHash, txnMetadata: TransactionMetadata): ByteArray? { return null }

        override fun recordReceiverTransactionRecoveryMetadata(txnId: SecureHash, sender: CordaX500Name, txnMetadata: TransactionMetadata) {}
    }

    private fun copy(): TestTransactionDSLInterpreter =
            TestTransactionDSLInterpreter(
                    ledgerInterpreter = ledgerInterpreter,
                    transactionBuilder = transactionBuilder.copy(),
                    labelToIndexMap = HashMap(labelToIndexMap)
            )

    internal fun toWireTransaction() = transactionBuilder.toWireTransaction(services)

    override fun input(stateRef: StateRef) {
        val state = ledgerInterpreter.resolveStateRef<ContractState>(stateRef)
        transactionBuilder.addInputState(StateAndRef(state, stateRef))
    }

    override fun reference(stateRef: StateRef) {
        val state = ledgerInterpreter.resolveStateRef<ContractState>(stateRef)
        transactionBuilder.addReferenceState(StateAndRef(state, stateRef).referenced())
    }

    override fun output(contractClassName: ContractClassName,
                         label: String?,
                         notary: Party,
                         encumbrance: Int?,
                         attachmentConstraint: AttachmentConstraint,
                         contractState: ContractState
    ) {
        transactionBuilder.addOutputState(contractState, contractClassName, notary, encumbrance, attachmentConstraint)
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

    override fun command(signers: List<PublicKey>, commandData: CommandData) {
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

    override fun _tweak(dsl: TransactionDSLInterpreter.() -> EnforceVerifyOrFail) = copy().dsl()

    override fun _attachment(contractClassName: ContractClassName) {
        attachment((services.cordappProvider as MockCordappProvider).addMockCordapp(contractClassName, services.attachments as MockAttachmentStorage))
    }

    override fun _attachment(contractClassName: ContractClassName, attachmentId: AttachmentId, signers: List<PublicKey>){
        attachment((services.cordappProvider as MockCordappProvider).addMockCordapp(contractClassName, services.attachments as MockAttachmentStorage, attachmentId, signers))
    }

    override fun _attachment(contractClassName: ContractClassName, attachmentId: AttachmentId, signers: List<PublicKey>, jarManifestAttributes: Map<String,String>){
        attachment((services.cordappProvider as MockCordappProvider).addMockCordapp(contractClassName, services.attachments as MockAttachmentStorage, attachmentId, signers, jarManifestAttributes))
    }

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

    internal fun getTransaction(id: SecureHash): SignedTransaction? {
        val tx = transactionWithLocations[id] ?: nonVerifiedTransactionWithLocations[id]
        return tx?.let { SignedTransaction(it.transaction, listOf(NULL_SIGNATURE)) }
    }

    internal inline fun <reified S : ContractState> resolveStateRef(stateRef: StateRef): TransactionState<S> {
        val transactionWithLocation =
                transactionWithLocations[stateRef.txhash] ?:
                        nonVerifiedTransactionWithLocations[stateRef.txhash] ?:
                        throw TransactionResolutionException(stateRef.txhash)
        val output = transactionWithLocation.transaction.outputs[stateRef.index]
        return if (S::class.java.isAssignableFrom(output.data.javaClass)) {
            uncheckedCast(output)
        } else {
            throw TypeMismatch(requested = S::class.java, actual = output.data.javaClass)
        }
    }

    internal fun resolveAttachment(attachmentId: SecureHash): Attachment {
        return services.attachments.openAttachment(attachmentId) ?: throw AttachmentResolutionException(attachmentId)
    }

    private fun <R> interpretTransactionDsl(
            transactionBuilder: TransactionBuilder,
            dsl: TestTransactionDSLInterpreter.() -> R
    ): TestTransactionDSLInterpreter {
        return TestTransactionDSLInterpreter(this, transactionBuilder).apply { dsl() }
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
            dsl: TestTransactionDSLInterpreter.() -> R,
            transactionMap: HashMap<SecureHash, WireTransactionWithLocation> = HashMap(),
            /** If set to true, will add dummy components to [transactionBuilder] to make it valid. */
            fillTransaction: Boolean
    ): WireTransaction {
        val transactionLocation = getCallerLocation()
        val transactionInterpreter = interpretTransactionDsl(transactionBuilder, dsl)
        if (fillTransaction) fillTransaction(transactionBuilder)
        // Create the WireTransaction
        val wireTransaction = try {
            transactionInterpreter.toWireTransaction()
        } catch (e: IllegalStateException) {
            throw IllegalStateException("A transaction-DSL block that is part of a test ledger must return a valid transaction.", e)
        }
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

    /**
     * This method fills the transaction builder with dummy components to satisfy the base transaction validity rules.
     *
     * A common pattern in our tests is using a base transaction and expressing the test cases using [tweak]s.
     * The base transaction may not be valid, but it still gets recorded to the ledger. This causes a test failure,
     * even though is not being used for anything afterwards.
     */
    private fun fillTransaction(transactionBuilder: TransactionBuilder) {
        if (transactionBuilder.commands().isEmpty()) transactionBuilder.addCommand(dummyCommand())
    }

    override fun _transaction(
            transactionLabel: String?,
            transactionBuilder: TransactionBuilder,
            dsl: TestTransactionDSLInterpreter.() -> EnforceVerifyOrFail
    ) = recordTransactionWithTransactionMap(transactionLabel, transactionBuilder, dsl, transactionWithLocations, false)

    override fun _unverifiedTransaction(
            transactionLabel: String?,
            transactionBuilder: TransactionBuilder,
            dsl: TestTransactionDSLInterpreter.() -> Unit
    ) = recordTransactionWithTransactionMap(transactionLabel, transactionBuilder, dsl, nonVerifiedTransactionWithLocations, true)

    override fun _tweak(dsl: LedgerDSLInterpreter<TestTransactionDSLInterpreter>.() -> Unit) =
            copy().dsl()

    override fun attachment(attachment: InputStream): SecureHash {
        return services.attachments.importAttachment(attachment, "TestDSL", null)
    }

    override fun verifies(): EnforceVerifyOrFail {
        try {
            val usedInputs = mutableSetOf<StateRef>()
            transactionsUnverified.map {
                (services.validatedTransactions as WritableTransactionStorage).addTransaction(SignedTransaction(it, listOf(NULL_SIGNATURE)))
            }

            for ((_, value) in transactionWithLocations) {
                val wtx = value.transaction
                val ltx = wtx.toLedgerTransaction(services)
                ltx.verify()
                val allInputs = wtx.inputs union wtx.references
                val doubleSpend = allInputs intersect usedInputs
                if (!doubleSpend.isEmpty()) {
                    val txIds = mutableListOf(wtx.id)
                    doubleSpend.mapTo(txIds) { it.txhash }
                    throw DoubleSpentInputs(txIds)
                }
                usedInputs.addAll(wtx.inputs)
                (services.validatedTransactions as WritableTransactionStorage).addTransaction(SignedTransaction(wtx, listOf(NULL_SIGNATURE)))
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
            return uncheckedCast(stateAndRef)
        }
    }

    val transactionsToVerify: List<WireTransaction> get() = transactionWithLocations.values.map { it.transaction }
    val transactionsUnverified: List<WireTransaction> get() = nonVerifiedTransactionWithLocations.values.map { it.transaction }
}
