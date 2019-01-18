package net.corda.core.transactions

import net.corda.core.CordaInternal
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.componentHash
import net.corda.core.crypto.computeNonce
import net.corda.core.identity.Party
import net.corda.core.internal.AttachmentWithContext
import net.corda.core.internal.combinedHash
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.*
import net.corda.core.serialization.internal.AttachmentsClassLoaderBuilder
import net.corda.core.transactions.ContractUpgradeFilteredTransaction.FilteredComponent
import net.corda.core.transactions.ContractUpgradeWireTransaction.Companion.calculateUpgradedState
import net.corda.core.transactions.ContractUpgradeWireTransaction.Component.*
import net.corda.core.transactions.WireTransaction.Companion.resolveStateRefBinaryComponent
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.toBase58String
import java.security.PublicKey

// TODO: copy across encumbrances when performing contract upgrades
// TODO: check transaction size is within limits

/** A special transaction for upgrading the contract of a state. */
@KeepForDJVM
@CordaSerializable
data class ContractUpgradeWireTransaction(
        /**
         * Contains all of the transaction components in serialized form.
         * This is used for calculating the transaction id in a deterministic fashion, since re-serializing properties
         * may result in a different byte sequence depending on the serialization context.
         */
        val serializedComponents: List<OpaqueBytes>,
        /** Required for hiding components in [ContractUpgradeFilteredTransaction]. */
        val privacySalt: PrivacySalt = PrivacySalt()
) : CoreTransaction() {

    companion object {
        /**
         * Runs the explicit upgrade logic.
         */
        @CordaInternal
        internal fun <T : ContractState, S : ContractState> calculateUpgradedState(state: TransactionState<T>, upgradedContract: UpgradedContract<T, S>, upgradedContractAttachment: Attachment): TransactionState<S> {
            // TODO: if there are encumbrance states in the inputs, just copy them across without modifying
            val upgradedState: S = upgradedContract.upgrade(state.data)
            val inputConstraint = state.constraint
            val outputConstraint = when (inputConstraint) {
                is HashAttachmentConstraint -> HashAttachmentConstraint(upgradedContractAttachment.id)
                WhitelistedByZoneAttachmentConstraint -> WhitelistedByZoneAttachmentConstraint
                else -> throw IllegalArgumentException("Unsupported input contract constraint $inputConstraint")
            }
            // TODO: re-map encumbrance pointers
            return TransactionState(
                    data = upgradedState,
                    contract = upgradedContract::class.java.name,
                    constraint = outputConstraint,
                    notary = state.notary,
                    encumbrance = state.encumbrance
            )
        }
    }

    override val inputs: List<StateRef> = serializedComponents[INPUTS.ordinal].deserialize()
    override val notary: Party by lazy { serializedComponents[NOTARY.ordinal].deserialize<Party>() }
    val legacyContractAttachmentId: SecureHash by lazy { serializedComponents[LEGACY_ATTACHMENT.ordinal].deserialize<SecureHash>() }
    val upgradedContractClassName: ContractClassName by lazy { serializedComponents[UPGRADED_CONTRACT.ordinal].deserialize<ContractClassName>() }
    val upgradedContractAttachmentId: SecureHash by lazy { serializedComponents[UPGRADED_ATTACHMENT.ordinal].deserialize<SecureHash>() }
    override val networkParametersHash: SecureHash? by lazy {
        if (serializedComponents.size >= PARAMETERS_HASH.ordinal + 1) {
            serializedComponents[PARAMETERS_HASH.ordinal].deserialize<SecureHash>()
        } else null
    }

    init {
        check(inputs.isNotEmpty()) { "A contract upgrade transaction must have inputs" }
        checkBaseInvariants()
    }

    /**
     * This transaction does not contain any output states, outputs can be obtained by resolving a
     * [ContractUpgradeLedgerTransaction] – outputs will be calculated on demand by applying the contract
     * upgrade operation to inputs.
     */
    override val outputs: List<TransactionState<ContractState>>
        get() = throw UnsupportedOperationException("ContractUpgradeWireTransaction does not contain output states, " +
                "outputs can only be obtained from a resolved ContractUpgradeLedgerTransaction")

    /** ContractUpgradeWireTransactions should not contain reference input states. */
    override val references: List<StateRef> get() = emptyList()

    override val id: SecureHash by lazy {
        val componentHashes = serializedComponents.mapIndexed { index, component ->
            componentHash(nonces[index], component)
        }
        combinedHash(componentHashes)
    }

    /** Required for filtering transaction components. */
    private val nonces = (0 until serializedComponents.size).map {
        computeNonce(privacySalt, it, 0)
    }

    /** Resolves input states and contract attachments, and builds a ContractUpgradeLedgerTransaction. */
    fun resolve(services: ServicesForResolution, sigs: List<TransactionSignature>): ContractUpgradeLedgerTransaction {
        val resolvedInputs = services.loadStates(inputs.toSet()).toList()
        val legacyContractAttachment = services.attachments.openAttachment(legacyContractAttachmentId)
                ?: throw AttachmentResolutionException(legacyContractAttachmentId)
        val upgradedContractAttachment = services.attachments.openAttachment(upgradedContractAttachmentId)
                ?: throw AttachmentResolutionException(upgradedContractAttachmentId)
        val hashToResolve = networkParametersHash ?: services.networkParametersService.defaultHash
        val resolvedNetworkParameters = services.networkParametersService.lookup(hashToResolve) ?: throw TransactionResolutionException(id)
        return ContractUpgradeLedgerTransaction(
                resolvedInputs,
                notary,
                legacyContractAttachment,
                upgradedContractClassName,
                upgradedContractAttachment,
                id,
                privacySalt,
                sigs,
                resolvedNetworkParameters
        )
    }

    private fun upgradedContract(className: ContractClassName, classLoader: ClassLoader): UpgradedContract<ContractState, ContractState> = try {
        classLoader.loadClass(className).asSubclass(UpgradedContract::class.java as Class<UpgradedContract<ContractState, ContractState>>)
                .newInstance()
    } catch (e: Exception) {
        throw TransactionVerificationException.ContractCreationError(id, className, e)
    }

    /**
     * Creates a binary serialized component for a virtual output state serialised and executed with the attachments from the transaction.
     */
    @CordaInternal
    internal fun resolveOutputComponent(services: ServicesForResolution, stateRef: StateRef): SerializedBytes<TransactionState<ContractState>> {
        val binaryInput = resolveStateRefBinaryComponent(inputs[stateRef.index], services)!!
        val legacyAttachment = services.attachments.openAttachment(legacyContractAttachmentId)
                ?: throw MissingContractAttachments(emptyList())
        val upgradedAttachment = services.attachments.openAttachment(upgradedContractAttachmentId)
                ?: throw MissingContractAttachments(emptyList())

        return AttachmentsClassLoaderBuilder.withAttachmentsClassloaderContext(listOf(legacyAttachment, upgradedAttachment)) { transactionClassLoader ->
            val resolvedInput = binaryInput.deserialize<TransactionState<ContractState>>()
            val upgradedContract = upgradedContract(upgradedContractClassName, transactionClassLoader)
            val outputState = calculateUpgradedState(resolvedInput, upgradedContract, upgradedAttachment)
            outputState.serialize()
        }
    }

    /** Constructs a filtered transaction: the inputs, the notary party and network parameters hash are always visible, while the rest are hidden. */
    fun buildFilteredTransaction(): ContractUpgradeFilteredTransaction {
        val totalComponents = (0 until serializedComponents.size).toSet()
        val visibleComponents = mapOf(
                INPUTS.ordinal to FilteredComponent(serializedComponents[INPUTS.ordinal], nonces[INPUTS.ordinal]),
                NOTARY.ordinal to FilteredComponent(serializedComponents[NOTARY.ordinal], nonces[NOTARY.ordinal]),
                PARAMETERS_HASH.ordinal to FilteredComponent(serializedComponents[PARAMETERS_HASH.ordinal], nonces[PARAMETERS_HASH.ordinal])
        )
        val hiddenComponents = (totalComponents - visibleComponents.keys).map { index ->
            val hash = componentHash(nonces[index], serializedComponents[index])
            index to hash
        }.toMap()

        return ContractUpgradeFilteredTransaction(visibleComponents, hiddenComponents)
    }

    enum class Component {
        INPUTS, NOTARY, LEGACY_ATTACHMENT, UPGRADED_CONTRACT, UPGRADED_ATTACHMENT, PARAMETERS_HASH
    }
}

/**
 * A filtered version of the [ContractUpgradeWireTransaction]. In comparison with a regular [FilteredTransaction], there
 * is no flexibility on what parts of the transaction to reveal – the inputs, notary and network parameters hash fields are always visible and the
 * rest of the transaction is always hidden. Its only purpose is to hide transaction data when using a non-validating notary.
 */
@KeepForDJVM
@CordaSerializable
data class ContractUpgradeFilteredTransaction(
        /** Transaction components that are exposed. */
        val visibleComponents: Map<Int, FilteredComponent>,
        /**
         * Hashes of the transaction components that are not revealed in this transaction.
         * Required for computing the transaction id.
         */
        val hiddenComponents: Map<Int, SecureHash>
) : CoreTransaction() {
    override val inputs: List<StateRef> by lazy {
        visibleComponents[INPUTS.ordinal]?.component?.deserialize<List<StateRef>>()
                ?: throw IllegalArgumentException("Inputs not specified")
    }
    override val notary: Party by lazy {
        visibleComponents[NOTARY.ordinal]?.component?.deserialize<Party>()
                ?: throw IllegalArgumentException("Notary not specified")
    }
    override val networkParametersHash: SecureHash? by lazy {
        visibleComponents[PARAMETERS_HASH.ordinal]?.component?.deserialize<SecureHash>()
    }
    override val id: SecureHash by lazy {
        val totalComponents = visibleComponents.size + hiddenComponents.size
        val hashList = (0 until totalComponents).map { i ->
            when {
                visibleComponents.containsKey(i) -> {
                    componentHash(visibleComponents[i]!!.nonce, visibleComponents[i]!!.component)
                }
                hiddenComponents.containsKey(i) -> hiddenComponents[i]!!
                else -> throw IllegalStateException("Missing component hashes")
            }
        }
        combinedHash(hashList)
    }
    override val outputs: List<TransactionState<ContractState>> get() = emptyList()
    override val references: List<StateRef> get() = emptyList()

    /** Contains the serialized component and the associated nonce for computing the transaction id. */
    @CordaSerializable
    class FilteredComponent(val component: OpaqueBytes, val nonce: SecureHash)
}

/**
 * A contract upgrade transaction with fully resolved inputs and signatures. Contract upgrade transactions are separate
 * to regular transactions because their validation logic is specialised; the original contract by definition cannot be
 * aware of the upgraded contract (it was written after the original contract was developed), so its validation logic
 * cannot succeed. Instead alternative verification logic is used which verifies that the outputs correspond to the
 * inputs after upgrading.
 *
 * In contrast with a regular transaction, signatures are checked against the signers specified by input states'
 * *participants* fields, so full resolution is needed for signature verification.
 */
@KeepForDJVM
data class ContractUpgradeLedgerTransaction(
        override val inputs: List<StateAndRef<ContractState>>,
        override val notary: Party,
        val legacyContractAttachment: Attachment,
        val upgradedContractClassName: ContractClassName,
        val upgradedContractAttachment: Attachment,
        override val id: SecureHash,
        val privacySalt: PrivacySalt,
        override val sigs: List<TransactionSignature>,
        override val networkParameters: NetworkParameters
) : FullTransaction(), TransactionWithSignatures {
    /** ContractUpgradeLedgerTransactions do not contain reference input states. */
    override val references: List<StateAndRef<ContractState>> = emptyList()
    /** The legacy contract class name is determined by the first input state. */
    private val legacyContractClassName = inputs.first().state.contract
    private val upgradedContract: UpgradedContract<ContractState, *> = loadUpgradedContract()

    init {
        checkNotaryWhitelisted()
        // TODO: relax this constraint once upgrading encumbered states is supported.
        check(inputs.all { it.state.contract == legacyContractClassName }) {
            "All input states must point to the legacy contract"
        }
        check(upgradedContract.legacyContract == legacyContractClassName) {
            "Outputs' contract must be an upgraded version of the inputs' contract"
        }
        verifyConstraints()
    }

    private fun verifyConstraints() {
        val attachmentForConstraintVerification = AttachmentWithContext(
                legacyContractAttachment as? ContractAttachment
                        ?: ContractAttachment.create(legacyContractAttachment, legacyContractClassName, signerKeys = legacyContractAttachment.signerKeys),
                upgradedContract.legacyContract,
                networkParameters)

        // TODO: exclude encumbrance states from this check
        check(inputs.all { it.state.constraint.isSatisfiedBy(attachmentForConstraintVerification) }) {
            "Legacy contract constraint does not satisfy the constraint of the input states"
        }

        val constraintCheck = if (upgradedContract is UpgradedContractWithLegacyConstraint) {
            upgradedContract.legacyContractConstraint.isSatisfiedBy(attachmentForConstraintVerification)
        } else {
            // If legacy constraint not specified, defaulting to WhitelistedByZoneAttachmentConstraint
            WhitelistedByZoneAttachmentConstraint.isSatisfiedBy(attachmentForConstraintVerification)
        }
        check(constraintCheck) {
            "Legacy contract does not satisfy the upgraded contract's constraint"
        }
    }

    /**
     * Outputs are computed by running the contract upgrade logic on input states. This is done eagerly so that the
     * transaction is verified during construction.
     */
    override val outputs: List<TransactionState<ContractState>> = inputs.map { calculateUpgradedState(it.state, upgradedContract, upgradedContractAttachment) }

    /** The required signers are the set of all input states' participants. */
    override val requiredSigningKeys: Set<PublicKey>
        get() = inputs.flatMap { it.state.data.participants }.map { it.owningKey }.toSet() + notary.owningKey

    override fun getKeyDescriptions(keys: Set<PublicKey>): List<String> {
        return keys.map { it.toBase58String() }
    }

    // TODO: load contract from the CorDapp classloader
    private fun loadUpgradedContract(): UpgradedContract<ContractState, *> {
        @Suppress("UNCHECKED_CAST")
        return this::class.java.classLoader
                .loadClass(upgradedContractClassName)
                .asSubclass(Contract::class.java)
                .getConstructor()
                .newInstance() as UpgradedContract<ContractState, *>
    }
}