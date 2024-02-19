package net.corda.core.transactions

import net.corda.core.CordaException
import net.corda.core.CordaInternal
import net.corda.core.CordaThrowable
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.AttachmentResolutionException
import net.corda.core.contracts.NamedByHash
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.sign
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.Party
import net.corda.core.internal.TransactionDeserialisationException
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.equivalent
import net.corda.core.internal.isUploaderTrusted
import net.corda.core.internal.verification.NodeVerificationSupport
import net.corda.core.internal.verification.VerificationSupport
import net.corda.core.internal.verification.toVerifyingServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.MissingSerializerException
import net.corda.core.serialization.serialize
import net.corda.core.utilities.Try
import net.corda.core.utilities.Try.Failure
import net.corda.core.utilities.Try.Success
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import java.io.NotSerializableException
import java.security.KeyPair
import java.security.PublicKey
import java.security.SignatureException
import java.util.function.Predicate

/**
 * SignedTransaction wraps a serialized WireTransaction. It contains one or more signatures, each one for
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
        return toLedgerTransactionInternal(verifyingServiceHub, checkSufficientSignatures)
    }

    @JvmSynthetic
    @CordaInternal
    fun toLedgerTransactionInternal(verificationSupport: VerificationSupport, checkSufficientSignatures: Boolean): LedgerTransaction {
        // TODO: We could probably optimise the below by
        // a) not throwing if threshold is eventually satisfied, but some of the rest of the signatures are failing.
        // b) omit verifying signatures when threshold requirement is met.
        // c) omit verifying signatures from keys not included in [requiredSigningKeys].
        // For the above to work, [checkSignaturesAreValid] should take the [requiredSigningKeys] as input
        // and probably combine logic from signature validation and key-fulfilment
        // in [TransactionWithSignatures.verifySignaturesExcept].
        if (checkSufficientSignatures) {
            verifyRequiredSignatures() // It internally invokes checkSignaturesAreValid().
        } else {
            checkSignaturesAreValid()
        }
        return tx.toLedgerTransactionInternal(verificationSupport)
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
    internal fun verifyInternal(verificationSupport: NodeVerificationSupport, checkSufficientSignatures: Boolean = true): FullTransaction? {
        resolveAndCheckNetworkParameters(verificationSupport)
        val verificationType = determineVerificationType()
        log.debug { "Transaction $id has verification type $verificationType" }
        return when (verificationType) {
            VerificationType.IN_PROCESS -> verifyInProcess(verificationSupport, checkSufficientSignatures)
            VerificationType.BOTH -> {
                val inProcessResult = Try.on { verifyInProcess(verificationSupport, checkSufficientSignatures) }
                val externalResult = Try.on { verificationSupport.externalVerifierHandle.verifyTransaction(this, checkSufficientSignatures) }
                ensureSameResult(inProcessResult, externalResult)
            }
            VerificationType.EXTERNAL -> {
                verificationSupport.externalVerifierHandle.verifyTransaction(this, checkSufficientSignatures)
                // We could create a LedgerTransaction here, and except for calling `verify()`, it would be valid to use. However, it's best
                // we let the caller deal with that, since we can't control what they will do with it.
                null
            }
        }
    }

    private fun determineVerificationType(): VerificationType {
        val ctx = coreTransaction
        return when (ctx) {
            is WireTransaction -> {
                when {
                    ctx.legacyAttachments.isEmpty() -> VerificationType.IN_PROCESS
                    ctx.nonLegacyAttachments.isEmpty() -> VerificationType.EXTERNAL
                    else -> VerificationType.BOTH
                }
            }
            // Contract upgrades only work on 4.11 and earlier
            is ContractUpgradeWireTransaction -> VerificationType.EXTERNAL
            else -> VerificationType.IN_PROCESS  // The default is always in-process
        }
    }

    private fun ensureSameResult(inProcessResult: Try<FullTransaction>, externalResult: Try<*>): FullTransaction {
        return when (externalResult) {
            is Success -> when (inProcessResult) {
                is Success -> inProcessResult.value
                is Failure -> throw IllegalStateException("In-process verification of $id failed, but it succeeded in external verifier")
                        .apply { addSuppressed(inProcessResult.exception) }
            }
            is Failure -> throw when (inProcessResult) {
                is Success -> IllegalStateException("In-process verification of $id succeeded, but it failed in external verifier")
                is Failure -> inProcessResult.exception  // Throw the in-process exception, with the external exception suppressed
            }.apply { addSuppressed(externalResult.exception) }
        }
    }

    private enum class VerificationType {
        IN_PROCESS, EXTERNAL, BOTH
    }

    /**
     * Verifies this transaction in-process. This assumes the current process has the correct classpath for all the contracts.
     *
     * @return The [FullTransaction] that was successfully verified
     */
    @CordaInternal
    @JvmSynthetic
    internal fun verifyInProcess(verificationSupport: VerificationSupport, checkSufficientSignatures: Boolean): FullTransaction {
        return when (coreTransaction) {
            is NotaryChangeWireTransaction -> verifyNotaryChangeTransaction(verificationSupport, checkSufficientSignatures)
            is ContractUpgradeWireTransaction -> verifyContractUpgradeTransaction(verificationSupport, checkSufficientSignatures)
            else -> verifyRegularTransaction(verificationSupport, checkSufficientSignatures)
        }
    }

    @Suppress("ThrowsCount")
    private fun resolveAndCheckNetworkParameters(services: NodeVerificationSupport) {
        val hashOrDefault = networkParametersHash ?: services.networkParametersService.defaultHash
        val txNetworkParameters = services.networkParametersService.lookup(hashOrDefault)
                ?: throw TransactionResolutionException(id)
        val groupedInputsAndRefs = (inputs + references).groupBy { it.txhash }
        groupedInputsAndRefs.map { entry ->
            val tx = services.validatedTransactions.getTransaction(entry.key)?.coreTransaction
                    ?: throw TransactionResolutionException(id)
            val paramHash = tx.networkParametersHash ?: services.networkParametersService.defaultHash
            val params = services.networkParametersService.lookup(paramHash) ?: throw TransactionResolutionException(id)
            if (txNetworkParameters.epoch < params.epoch)
                throw TransactionVerificationException.TransactionNetworkParameterOrderingException(id, entry.value.first(), txNetworkParameters, params)
        }
    }

    /** No contract code is run when verifying notary change transactions, it is sufficient to check invariants during initialisation. */
    private fun verifyNotaryChangeTransaction(verificationSupport: VerificationSupport, checkSufficientSignatures: Boolean): NotaryChangeLedgerTransaction {
        val ntx = NotaryChangeLedgerTransaction.resolve(verificationSupport, coreTransaction as NotaryChangeWireTransaction, sigs)
        if (checkSufficientSignatures) ntx.verifyRequiredSignatures()
        else checkSignaturesAreValid()
        return ntx
    }

    /** No contract code is run when verifying contract upgrade transactions, it is sufficient to check invariants during initialisation. */
    private fun verifyContractUpgradeTransaction(verificationSupport: VerificationSupport, checkSufficientSignatures: Boolean): ContractUpgradeLedgerTransaction {
        val ctx = ContractUpgradeLedgerTransaction.resolve(verificationSupport, coreTransaction as ContractUpgradeWireTransaction, sigs)
        if (checkSufficientSignatures) ctx.verifyRequiredSignatures()
        else checkSignaturesAreValid()
        return ctx
    }

    // TODO: Verify contract constraints here as well as in LedgerTransaction to ensure that anything being deserialised
    // from the attachment is trusted. This will require some partial serialisation work to not load the ContractState
    // objects from the TransactionState.
    private fun verifyRegularTransaction(verificationSupport: VerificationSupport, checkSufficientSignatures: Boolean): LedgerTransaction {
        val ltx = toLedgerTransactionInternal(verificationSupport, checkSufficientSignatures)
        try {
            ltx.verify()
        } catch (e: NoClassDefFoundError) {
            checkReverifyAllowed(e)
            val missingClass = e.message ?: throw e
            log.warn("Transaction {} has missing class: {}", ltx.id, missingClass)
            reverifyWithFixups(ltx, verificationSupport, missingClass)
        } catch (e: NotSerializableException) {
            checkReverifyAllowed(e)
            retryVerification(e, e, ltx, verificationSupport)
        } catch (e: TransactionDeserialisationException) {
            checkReverifyAllowed(e)
            retryVerification(e.cause, e, ltx, verificationSupport)
        }
        return ltx
    }

    private fun checkReverifyAllowed(ex: Throwable) {
        // If that transaction was created with and after Corda 4 then just fail.
        // The lenient dependency verification is only supported for Corda 3 transactions.
        // To detect if the transaction was created before Corda 4 we check if the transaction has the NetworkParameters component group.
        if (networkParametersHash != null) {
            log.warn("TRANSACTION VERIFY FAILED - No attempt to auto-repair as TX is Corda 4+")
            throw ex
        }
    }

    @Suppress("ThrowsCount")
    private fun retryVerification(cause: Throwable?, ex: Throwable, ltx: LedgerTransaction, verificationSupport: VerificationSupport) {
        when (cause) {
            is MissingSerializerException -> {
                log.warn("Missing serializers: typeDescriptor={}, typeNames={}", cause.typeDescriptor ?: "<unknown>", cause.typeNames)
                reverifyWithFixups(ltx, verificationSupport, null)
            }
            is NotSerializableException -> {
                val underlying = cause.cause
                if (underlying is ClassNotFoundException) {
                    val missingClass = underlying.message?.replace('.', '/') ?: throw ex
                    log.warn("Transaction {} has missing class: {}", ltx.id, missingClass)
                    reverifyWithFixups(ltx, verificationSupport, missingClass)
                } else {
                    throw ex
                }
            }
            else -> throw ex
        }
    }

    // Transactions created before Corda 4 can be missing dependencies on other CorDapps.
    // This code has detected a missing custom serializer - probably located inside a workflow CorDapp.
    // We need to extract this CorDapp from AttachmentStorage and try verifying this transaction again.
    private fun reverifyWithFixups(ltx: LedgerTransaction, verificationSupport: VerificationSupport, missingClass: String?) {
        log.warn("""Detected that transaction $id does not contain all cordapp dependencies.
                    |This may be the result of a bug in a previous version of Corda.
                    |Attempting to re-verify having applied this node's fix-up rules.
                    |Please check with the originator that this is a valid transaction.""".trimMargin())

        val replacementAttachments = computeReplacementAttachments(ltx, verificationSupport, missingClass)
        log.warn("Reverifying transaction {} with attachments:{}", ltx.id, replacementAttachments)
        ltx.verifyInternal(replacementAttachments.toList())
    }

    private fun computeReplacementAttachments(ltx: LedgerTransaction,
                                              verificationSupport: VerificationSupport,
                                              missingClass: String?): Collection<Attachment> {
        val replacements = fixupAttachments(verificationSupport, ltx.attachments)
        if (!replacements.equivalent(ltx.attachments)) {
            return replacements
        }

        // We cannot continue unless we have some idea which class is missing from the attachments.
        if (missingClass == null) {
            throw TransactionVerificationException.BrokenTransactionException(
                    txId = ltx.id,
                    message = "No fix-up rules provided for broken attachments: $replacements"
            )
        }

        /*
         * The Node's fix-up rules have not been able to adjust the transaction's attachments,
         * so resort to the original mechanism of trying to find an attachment that contains
         * the missing class.
         */
        val extraAttachment = requireNotNull(verificationSupport.getTrustedClassAttachment(missingClass)) {
            """Transaction $ltx is incorrectly formed. Most likely it was created during version 3 of Corda
                |when the verification logic was more lenient. Attempted to find local dependency for class: $missingClass,
                |but could not find one.
                |If you wish to verify this transaction, please contact the originator of the transaction and install the
                |provided missing JAR.
                |You can install it using the RPC command: `uploadAttachment` without restarting the node.
                |""".trimMargin()
        }

        return replacements.toMutableSet().apply {
            /*
             * Check our transaction doesn't already contain this extra attachment.
             * It seems unlikely that we would, but better safe than sorry!
             */
            if (!add(extraAttachment)) {
                throw TransactionVerificationException.BrokenTransactionException(
                        txId = ltx.id,
                        message = "Unlinkable class $missingClass inside broken attachments: $replacements"
                )
            }

            log.warn("""Detected that transaction $ltx does not contain all cordapp dependencies.
                    |This may be the result of a bug in a previous version of Corda.
                    |Attempting to verify using the additional trusted dependency: $extraAttachment for class $missingClass.
                    |Please check with the originator that this is a valid transaction.
                    |YOU ARE ONLY SEEING THIS MESSAGE BECAUSE THE CORDAPPS THAT CREATED THIS TRANSACTION ARE BROKEN!
                    |WE HAVE TRIED TO REPAIR THE TRANSACTION AS BEST WE CAN, BUT CANNOT GUARANTEE WE HAVE SUCCEEDED!
                    |PLEASE FIX THE CORDAPPS AND MIGRATE THESE BROKEN TRANSACTIONS AS SOON AS POSSIBLE!
                    |THIS MESSAGE IS **SUPPOSED** TO BE SCARY!!
                    |""".trimMargin()
            )
        }
    }

    /**
     * Apply this node's attachment fix-up rules to the given attachments.
     *
     * @param attachments A collection of [Attachment] objects, e.g. as provided by a transaction.
     * @return The [attachments] with the node's fix-up rules applied.
     */
    private fun fixupAttachments(verificationSupport: VerificationSupport, attachments: Collection<Attachment>): Collection<Attachment> {
        val attachmentsById = attachments.associateByTo(LinkedHashMap(), Attachment::id)
        val replacementIds = verificationSupport.fixupAttachmentIds(attachmentsById.keys)
        attachmentsById.keys.retainAll(replacementIds)
        val extraIds = replacementIds - attachmentsById.keys
        val extraAttachments = verificationSupport.getAttachments(extraIds)
        for ((index, extraId) in extraIds.withIndex()) {
            val extraAttachment = extraAttachments[index]
            if (extraAttachment == null || !extraAttachment.isUploaderTrusted()) {
                throw MissingAttachmentsException(listOf(extraId))
            }
            attachmentsById[extraId] = extraAttachment
        }
        return attachmentsById.values
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

    override fun toString(): String = "${javaClass.simpleName}(id=$id)"

    private companion object {
        private fun missingSignatureMsg(missing: Set<PublicKey>, descriptions: List<String>, id: SecureHash): String {
            return "Missing signatures on transaction ${id.prefixChars()} for " +
                    "keys: ${missing.joinToString { it.toStringShort() }}, " +
                    "by signers: ${descriptions.joinToString()} "
        }

        private val log = contextLogger()
    }

    class SignaturesMissingException(val missing: Set<PublicKey>, val descriptions: List<String>, override val id: SecureHash)
        : NamedByHash, SignatureException(missingSignatureMsg(missing, descriptions, id)), CordaThrowable by CordaException(missingSignatureMsg(missing, descriptions, id))

    //region Deprecated
    /** Returns the contained [NotaryChangeWireTransaction], or throws if this is a normal transaction. */
    @Deprecated("No replacement, this should not be used outside of Corda core")
    val notaryChangeTx: NotaryChangeWireTransaction
        get() = coreTransaction as NotaryChangeWireTransaction

    @Deprecated("No replacement, this should not be used outside of Corda core")
    fun isNotaryChangeTransaction() = this.coreTransaction is NotaryChangeWireTransaction
    //endregion
}
