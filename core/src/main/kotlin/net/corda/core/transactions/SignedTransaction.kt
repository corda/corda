package net.corda.core.transactions

import net.corda.core.CordaException
import net.corda.core.CordaInternal
import net.corda.core.CordaThrowable
import net.corda.core.contracts.AttachmentResolutionException
import net.corda.core.contracts.NamedByHash
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.TransactionVerificationException.TransactionNetworkParameterOrderingException
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.sign
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.Party
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.getRequiredSigningKeysInternal
import net.corda.core.internal.toSimpleString
import net.corda.core.internal.verification.NodeVerificationSupport
import net.corda.core.internal.verification.toVerifyingServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.toBase58String
import java.security.KeyPair
import java.security.PublicKey
import java.security.SignatureException
import java.util.function.Predicate

/**
 * SignedTransaction wraps a serialized [CoreTransaction], though it will almost exclusively be a [WireTransaction].
 * It contains one or more signatures, each one for
 * a public key (including composite keys) that is mentioned inside a transaction command. SignedTransaction is the top level transaction type
 * and the type most frequently passed around the network and stored. The identity of a transaction is the hash of Merkle root
 * of a WireTransaction, therefore if you are storing data keyed by WT hash be aware that multiple different STs may
 * map to the same key (and they could be different in important ways, like validity!). The signatures on a
 * SignedTransaction might be invalid or missing: the type does not imply validity.
 * A transaction ID should be the hash of the [WireTransaction] Merkle tree root. Thus adding or removing a signature does not change it.
 *
 * @param sigs a list of signatures from individual (non-composite) public keys. This is passed as a list of signatures
 * when verifying composite key signatures, but may be used as individual signatures where a single key is expected to
 * sign.
 */
// DOCSTART 1
@CordaSerializable
data class SignedTransaction(val txBits: SerializedBytes<CoreTransaction>,
                             override val sigs: List<TransactionSignature>
) : TransactionWithSignatures {
    // DOCEND 1
    constructor(ctx: CoreTransaction, sigs: List<TransactionSignature>) : this(ctx.serialize(), sigs) {
        cachedTransaction = ctx
    }

    init {
        require(sigs.isNotEmpty()) { "Tried to instantiate a ${SignedTransaction::class.java.simpleName} without any signatures " }
    }

    /** Cache the deserialized form of the transaction. This is useful when building a transaction or collecting signatures. */
    @Volatile
    @Transient
    private var cachedTransaction: CoreTransaction? = null

    /** The id of the contained [WireTransaction]. */
    override val id: SecureHash get() = coreTransaction.id

    /** Lazily calculated access to the deserialised/hashed transaction data. */
    val coreTransaction: CoreTransaction
        get() = cachedTransaction ?: txBits.deserialize().apply { cachedTransaction = this }

    /** Returns the contained [WireTransaction], or throws if this is a notary change or contract upgrade transaction. */
    val tx: WireTransaction get() = coreTransaction as WireTransaction

    /**
     * Helper function to directly build a [FilteredTransaction] using provided filtering functions,
     * without first accessing the [WireTransaction] [tx].
     */
    fun buildFilteredTransaction(filtering: Predicate<Any>) = tx.buildFilteredTransaction(filtering)

    /** Helper to access the inputs of the contained transaction. */
    val inputs: List<StateRef> get() = coreTransaction.inputs
    /** Helper to access the unspendable inputs of the contained transaction. */
    val references: List<StateRef> get() = coreTransaction.references
    /** Helper to access the notary of the contained transaction. */
    val notary: Party? get() = coreTransaction.notary
    /** Helper to access the network parameters hash for the contained transaction. */
    val networkParametersHash: SecureHash? get() = coreTransaction.networkParametersHash

    override val requiredSigningKeys: Set<PublicKey> get() = tx.requiredSigningKeys

    override fun getKeyDescriptions(keys: Set<PublicKey>): ArrayList<String> {
        // TODO: We need a much better way of structuring this data.
        val descriptions = ArrayList<String>()
        this.tx.commands.forEach { command ->
            if (command.signers.any { it in keys })
                descriptions.add(command.toString())
        }
        if (this.tx.notary?.owningKey in keys)
            descriptions.add("notary")
        return descriptions
    }

    @VisibleForTesting
    fun withAdditionalSignature(keyPair: KeyPair, signatureMetadata: SignatureMetadata): SignedTransaction {
        val signableData = SignableData(tx.id, signatureMetadata)
        return withAdditionalSignature(keyPair.sign(signableData))
    }

    /** Returns the same transaction but with an additional (unchecked) signature. */
    fun withAdditionalSignature(sig: TransactionSignature) = copyWithCache(listOf(sig))

    /** Returns the same transaction but with an additional (unchecked) signatures. */
    fun withAdditionalSignatures(sigList: Iterable<TransactionSignature>) = copyWithCache(sigList)

    /**
     * Creates a copy of the SignedTransaction that includes the provided [sigList]. Also propagates the [cachedTransaction]
     * so the contained transaction does not need to be deserialized again.
     */
    private fun copyWithCache(sigList: Iterable<TransactionSignature>): SignedTransaction {
        val cached = cachedTransaction
        return copy(sigs = sigs + sigList).apply {
            cachedTransaction = cached
        }
    }

    /** Alias for [withAdditionalSignature] to let you use Kotlin operator overloading. */
    operator fun plus(sig: TransactionSignature) = withAdditionalSignature(sig)

    /** Alias for [withAdditionalSignatures] to let you use Kotlin operator overloading. */
    operator fun plus(sigList: Collection<TransactionSignature>) = withAdditionalSignatures(sigList)

    /**
     * Checks the transaction's signatures are valid, optionally calls [verifyRequiredSignatures] to
     * check all required signatures are present, and then calls [WireTransaction.toLedgerTransaction]
     * with the passed in [ServiceHub] to resolve the dependencies, returning an unverified
     * LedgerTransaction.
     *
     * This allows us to perform validation over the entirety of the transaction's contents.
     * WireTransaction only contains StateRef for the inputs and hashes for the attachments,
     * rather than ContractState instances for the inputs and Attachment instances for the attachments.
     *
     * @throws AttachmentResolutionException if a required attachment was not found in storage.
     * @throws TransactionResolutionException if an input points to a transaction not found in storage.
     * @throws SignatureException if any signatures were invalid or unrecognised
     * @throws SignaturesMissingException if any signatures that should have been present are missing.
     */
    @JvmOverloads
    @Throws(SignatureException::class, AttachmentResolutionException::class, TransactionResolutionException::class)
    fun toLedgerTransaction(services: ServiceHub, checkSufficientSignatures: Boolean = true): LedgerTransaction {
        val verifyingServiceHub = services.toVerifyingServiceHub()
        // We need parameters check here, because finality flow calls stx.toLedgerTransaction() and then verify.
        resolveAndCheckNetworkParameters(verifyingServiceHub)
        // TODO: We could probably optimise the below by
        // a) not throwing if threshold is eventually satisfied, but some of the rest of the signatures are failing.
        // b) omit verifying signatures when threshold requirement is met.
        // c) omit verifying signatures from keys not included in [requiredSigningKeys].
        // For the above to work, [checkSignaturesAreValid] should take the [requiredSigningKeys] as input
        // and probably combine logic from signature validation and key-fulfilment
        // in [TransactionWithSignatures.verifySignaturesExcept].
        verifySignatures(verifyingServiceHub, checkSufficientSignatures)
        return tx.toLedgerTransactionInternal(verifyingServiceHub)
    }

    /**
     * Checks the transaction's signatures are valid, optionally calls [verifyRequiredSignatures] to check
     * all required signatures are present. Resolves inputs and attachments from the local storage and performs full
     * transaction verification, including running the contracts.
     *
     * @throws AttachmentResolutionException if a required attachment was not found in storage.
     * @throws TransactionResolutionException if an input points to a transaction not found in storage.
     * @throws SignatureException if any signatures were invalid or unrecognised
     * @throws SignaturesMissingException if any signatures that should have been present are missing.
     */
    @JvmOverloads
    @Throws(SignatureException::class, AttachmentResolutionException::class, TransactionResolutionException::class, TransactionVerificationException::class)
    fun verify(services: ServiceHub, checkSufficientSignatures: Boolean = true) {
        verifyInternal(services.toVerifyingServiceHub(), checkSufficientSignatures)
    }

    /**
     * Internal version of the public [verify] which takes in a [NodeVerificationSupport] instead of the heavier [ServiceHub].
     *
     * Depending on the contract attachments, this method will either verify this transaction in-process or send it to the external verifier
     * for out-of-process verification.
     *
     * @return The [FullTransaction] that was successfully verified in-process. Returns null if the verification was successfully done externally.
     */
    @CordaInternal
    @JvmSynthetic
    internal fun verifyInternal(verificationSupport: NodeVerificationSupport, checkSufficientSignatures: Boolean = true): LedgerTransaction? {
        resolveAndCheckNetworkParameters(verificationSupport)
        verifySignatures(verificationSupport, checkSufficientSignatures)
        val ctx = coreTransaction
        val verificationResult = when (ctx) {
            // TODO: Verify contract constraints here as well as in LedgerTransaction to ensure that anything being deserialised
            // from the attachment is trusted. This will require some partial serialisation work to not load the ContractState
            // objects from the TransactionState.
            is WireTransaction -> ctx.tryVerify(verificationSupport)
            is ContractUpgradeWireTransaction -> ctx.tryVerify(verificationSupport)
            is NotaryChangeWireTransaction -> ctx.tryVerify(verificationSupport)
            else -> throw IllegalStateException("${ctx.toSimpleString()} cannot be verified")
        }
        return verificationResult.enforceSuccess()
    }

    @Suppress("ThrowsCount")
    private fun resolveAndCheckNetworkParameters(services: NodeVerificationSupport) {
        val hashOrDefault = networkParametersHash ?: services.networkParametersService.defaultHash
        val txNetworkParameters = services.networkParametersService.lookup(hashOrDefault) ?: throw TransactionResolutionException(id)
        val groupedInputsAndRefs = (inputs + references).groupBy { it.txhash }
        for ((txId, stateRefs) in groupedInputsAndRefs) {
            val tx = services.validatedTransactions.getTransaction(txId)?.coreTransaction ?: throw TransactionResolutionException(id)
            val paramHash = tx.networkParametersHash ?: services.networkParametersService.defaultHash
            val params = services.networkParametersService.lookup(paramHash) ?: throw TransactionResolutionException(id)
            if (txNetworkParameters.epoch < params.epoch) {
                throw TransactionNetworkParameterOrderingException(id, stateRefs.first(), txNetworkParameters, params)
            }
        }
    }

    private fun verifySignatures(verificationSupport: NodeVerificationSupport, checkSufficientSignatures: Boolean) {
        if (checkSufficientSignatures) {
            val ctx = coreTransaction
            val tws: TransactionWithSignatures = when (ctx) {
                is WireTransaction -> this  // SignedTransaction implements TransactionWithSignatures in terms of WireTransaction
                else -> CoreTransactionWithSignatures(ctx, sigs, verificationSupport)
            }
            tws.verifyRequiredSignatures()  // Internally checkSignaturesAreValid is invoked
        } else {
            checkSignaturesAreValid()
        }
    }

    /**
     * Resolves the underlying base transaction and then returns it, handling any special case transactions such as
     * [NotaryChangeWireTransaction].
     */
    fun resolveBaseTransaction(servicesForResolution: ServicesForResolution): BaseTransaction {
        return when (coreTransaction) {
            is NotaryChangeWireTransaction -> resolveNotaryChangeTransaction(servicesForResolution)
            is ContractUpgradeWireTransaction -> resolveContractUpgradeTransaction(servicesForResolution)
            is WireTransaction -> this.tx
            is FilteredTransaction -> throw IllegalStateException("Persistence of filtered transactions is not supported.")
            else -> throw IllegalStateException("Unknown transaction type ${coreTransaction::class.qualifiedName}")
        }
    }

    /**
     * Resolves the underlying transaction with signatures and then returns it, handling any special case transactions
     * such as [NotaryChangeWireTransaction].
     */
    fun resolveTransactionWithSignatures(services: ServicesForResolution): TransactionWithSignatures {
        return when (coreTransaction) {
            is NotaryChangeWireTransaction -> resolveNotaryChangeTransaction(services)
            is ContractUpgradeWireTransaction -> resolveContractUpgradeTransaction(services)
            is WireTransaction -> this
            is FilteredTransaction -> throw IllegalStateException("Persistence of filtered transactions is not supported.")
            else -> throw IllegalStateException("Unknown transaction type ${coreTransaction::class.qualifiedName}")
        }
    }

    /**
     * If [coreTransaction] is a [NotaryChangeWireTransaction], loads the input states and resolves it to a
     * [NotaryChangeLedgerTransaction] so the signatures can be verified.
     */
    fun resolveNotaryChangeTransaction(services: ServicesForResolution): NotaryChangeLedgerTransaction {
        val ntx = coreTransaction as? NotaryChangeWireTransaction
                ?: throw IllegalStateException("Expected a ${NotaryChangeWireTransaction::class.simpleName} but found ${coreTransaction::class.simpleName}")
        return ntx.resolve(services, sigs)
    }

    /**
     * If [coreTransaction] is a [NotaryChangeWireTransaction], loads the input states and resolves it to a
     * [NotaryChangeLedgerTransaction] so the signatures can be verified.
     */
    fun resolveNotaryChangeTransaction(services: ServiceHub): NotaryChangeLedgerTransaction {
        return resolveNotaryChangeTransaction(services as ServicesForResolution)
    }

    /**
     * If [coreTransaction] is a [ContractUpgradeWireTransaction], loads the input states and resolves it to a
     * [ContractUpgradeLedgerTransaction] so the signatures can be verified.
     */
    fun resolveContractUpgradeTransaction(services: ServicesForResolution): ContractUpgradeLedgerTransaction {
        val ctx = coreTransaction as? ContractUpgradeWireTransaction
                ?: throw IllegalStateException("Expected a ${ContractUpgradeWireTransaction::class.simpleName} but found ${coreTransaction::class.simpleName}")
        return ctx.resolve(services, sigs)
    }

    override fun toString(): String = toSimpleString()

    private companion object {
        private fun missingSignatureMsg(missing: Set<PublicKey>, descriptions: List<String>, id: SecureHash): String {
            return "Missing signatures on transaction ${id.prefixChars()} for " +
                    "keys: ${missing.joinToString { it.toStringShort() }}, " +
                    "by signers: ${descriptions.joinToString()} "
        }
    }

    class SignaturesMissingException(val missing: Set<PublicKey>, val descriptions: List<String>, override val id: SecureHash)
        : NamedByHash, SignatureException(missingSignatureMsg(missing, descriptions, id)), CordaThrowable by CordaException(missingSignatureMsg(missing, descriptions, id))

    /**
     * A [TransactionWithSignatures] wrapper for [CoreTransaction]s which need to resolve their input states in order to get at the signers
     * list.
     */
    private data class CoreTransactionWithSignatures(
            private val ctx: CoreTransaction,
            override val sigs: List<TransactionSignature>,
            private val verificationSupport: NodeVerificationSupport
    ) : TransactionWithSignatures, NamedByHash by ctx {
        override val requiredSigningKeys: Set<PublicKey>
            get() = getRequiredSigningKeysInternal(ctx.inputs.asSequence().map(verificationSupport::getStateAndRef), ctx.notary)

        override fun getKeyDescriptions(keys: Set<PublicKey>): List<String> = keys.map { it.toBase58String() }

        override fun toString(): String = toSimpleString()
    }

    //region Deprecated
    /** Returns the contained [NotaryChangeWireTransaction], or throws if this is a normal transaction. */
    @Deprecated("No replacement, this should not be used outside of Corda core")
    val notaryChangeTx: NotaryChangeWireTransaction
        get() = coreTransaction as NotaryChangeWireTransaction

    @Deprecated("No replacement, this should not be used outside of Corda core")
    fun isNotaryChangeTransaction() = this.coreTransaction is NotaryChangeWireTransaction
    //endregion
}
