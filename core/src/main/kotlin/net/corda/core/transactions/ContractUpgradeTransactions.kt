package net.corda.core.transactions

import com.google.common.io.ByteStreams
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.sha256
import net.corda.core.identity.Party
import net.corda.core.internal.AttachmentWithContext
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.ContractUpgradeWireTransaction.Component.*
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.toBase58String
import java.security.PublicKey

// TODO: copy across encumbrances when performing contract upgrades
// TODO: check transaction size is within limits

/** A special transaction for upgrading the contract of a state. */
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
    override val inputs: List<StateRef> = serializedComponents[INPUTS.ordinal].deserialize()
    override val notary: Party by lazy { serializedComponents[NOTARY.ordinal].deserialize<Party>() }
    val legacyContractAttachmentId: SecureHash by lazy { serializedComponents[LEGACY_ATTACHMENT.ordinal].deserialize<SecureHash>() }
    val upgradedContractClassName: ContractClassName by lazy { serializedComponents[UPGRADED_CONTRACT.ordinal].deserialize<ContractClassName>() }
    val upgradedContractAttachmentId: SecureHash by lazy { serializedComponents[UPGRADED_ATTACHMENT.ordinal].deserialize<SecureHash>() }

    init {
        check(inputs.isNotEmpty()) { "A contract upgrade transaction must have inputs" }
    }

    /**
     * This transaction does not contain any output states, outputs can be obtained by resolving a
     * [ContractUpgradeLedgerTransaction] – outputs will be calculated on demand by applying the contract
     * upgrade operation to inputs.
     */
    override val outputs: List<TransactionState<ContractState>>
        get() = throw UnsupportedOperationException("ContractUpgradeWireTransaction does not contain output states, " +
                "outputs can only be obtained from a resolved ContractUpgradeLedgerTransaction")

    override val id: SecureHash by lazy {
        (serializedComponents[INPUTS.ordinal].bytes + serializedComponents[NOTARY.ordinal].bytes).sha256()
                .hashConcat(hiddenComponentHash)
    }

    /** Hash of the list of components that are hidden in the [ContractUpgradeFilteredTransaction]. */
    private val hiddenComponentHash: SecureHash
        get() {
            val combinedBytes = ByteStreams.newDataOutput()
            // First two elements are always visible - inputs and notary
            for (i in (2 until serializedComponents.size)) {
                combinedBytes.write(serializedComponents[i].bytes)
            }
            return combinedBytes.toByteArray().sha256().hashConcat(privacySalt.bytes.sha256())
        }

    /** Resolves input states and contract attachments, and builds a ContractUpgradeLedgerTransaction. */
    fun resolve(services: ServicesForResolution, sigs: List<TransactionSignature>): ContractUpgradeLedgerTransaction {
        val resolvedInputs = services.loadStates(inputs.toSet()).toList()
        val legacyContractAttachment = services.attachments.openAttachment(legacyContractAttachmentId)
                ?: throw AttachmentResolutionException(legacyContractAttachmentId)
        val upgradedContractAttachment = services.attachments.openAttachment(upgradedContractAttachmentId)
                ?: throw AttachmentResolutionException(upgradedContractAttachmentId)
        return ContractUpgradeLedgerTransaction(
                resolvedInputs,
                notary,
                legacyContractAttachment,
                upgradedContractClassName,
                upgradedContractAttachment,
                id,
                privacySalt,
                sigs,
                services.networkParameters
        )
    }

    /** Constructs a filtered transaction: the inputs and the notary party are always visible, while the rest are hidden. */
    fun buildFilteredTransaction(): ContractUpgradeFilteredTransaction {
        return ContractUpgradeFilteredTransaction(serializedComponents.take(2), hiddenComponentHash)
    }

    enum class Component {
        INPUTS, NOTARY, LEGACY_ATTACHMENT, UPGRADED_CONTRACT, UPGRADED_ATTACHMENT
    }
}

/**
 * A filtered version of the [ContractUpgradeWireTransaction]. In comparison with a regular [FilteredTransaction], there
 * is no flexibility on what parts of the transaction to reveal – the inputs and notary field are always visible and the
 * rest of the transaction is always hidden. Its only purpose is to hide transaction data when using a non-validating notary.
 *
 * @property inputs The inputs of this transaction.
 * @property notary The notary for this transaction.
 * @property hiddenComponentHash Hash of the hidden components of the [ContractUpgradeWireTransaction].
 */
@CordaSerializable
data class ContractUpgradeFilteredTransaction(
        val serializedComponents: List<OpaqueBytes>,
        val hiddenComponentHash: SecureHash
) : CoreTransaction() {
    override val inputs: List<StateRef> by lazy { serializedComponents[INPUTS.ordinal].deserialize<List<StateRef>>() }
    override val notary: Party by lazy { serializedComponents[NOTARY.ordinal].deserialize<Party>() }
    override val id: SecureHash by lazy {
        (serializedComponents[INPUTS.ordinal].bytes + serializedComponents[NOTARY.ordinal].bytes).sha256().hashConcat(hiddenComponentHash)
    }
    override val outputs: List<TransactionState<ContractState>> get() = emptyList()
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
data class ContractUpgradeLedgerTransaction(
        override val inputs: List<StateAndRef<ContractState>>,
        override val notary: Party,
        val legacyContractAttachment: Attachment,
        val upgradedContractClassName: ContractClassName,
        val upgradedContractAttachment: Attachment,
        override val id: SecureHash,
        val privacySalt: PrivacySalt,
        override val sigs: List<TransactionSignature>,
        private val networkParameters: NetworkParameters
) : FullTransaction(), TransactionWithSignatures {
    /** The legacy contract class name is determined by the first input state. */
    private val legacyContractClassName = inputs.first().state.contract
    private val upgradedContract: UpgradedContract<ContractState, *> = loadUpgradedContract()

    init {
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
                        ?: ContractAttachment(legacyContractAttachment, legacyContractClassName),
                upgradedContract.legacyContract,
                networkParameters.whitelistedContractImplementations
        )

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
    override val outputs: List<TransactionState<ContractState>> = inputs.map { input ->
        // TODO: if there are encumbrance states in the inputs, just copy them across without modifying
        val upgradedState = upgradedContract.upgrade(input.state.data)
        val inputConstraint = input.state.constraint
        val outputConstraint = when (inputConstraint) {
            is HashAttachmentConstraint -> HashAttachmentConstraint(upgradedContractAttachment.id)
            WhitelistedByZoneAttachmentConstraint -> WhitelistedByZoneAttachmentConstraint
            else -> throw IllegalArgumentException("Unsupported input contract constraint $inputConstraint")
        }
        // TODO: re-map encumbrance pointers
        input.state.copy(
                data = upgradedState,
                contract = upgradedContractClassName,
                constraint = outputConstraint
        )
    }

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