package net.corda.core.transactions

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.serializedHash
import net.corda.core.identity.Party
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.StateLoader
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.toBase58String
import java.security.PublicKey

// TODO: copy across encumbrances when performing contract upgrades

/** A special transaction for upgrading the contract of a state. */
@CordaSerializable
data class ContractUpgradeWireTransaction(
        override val inputs: List<StateRef>,
        override val notary: Party,
        val legacyContractAttachmentId: SecureHash,
        val upgradeContractClassName: ContractClassName,
        val upgradedContractAttachmentId: SecureHash,
        val privacySalt: PrivacySalt = PrivacySalt()
) : CoreTransaction() {

    init {
        check(inputs.isNotEmpty()) { "A contract upgrade transaction must have inputs" }
    }

    /**
     * This transaction does not contain any output states, outputs can be obtained by resolving a
     * [ContractUpgradeLedgerTransaction] and applying the notary modification to inputs.
     */
    override val outputs: List<TransactionState<ContractState>>
        get() = throw UnsupportedOperationException("ContractUpgradeWireTransaction does not contain output states, " +
                "outputs can only be obtained from a resolved ContractUpgradeLedgerTransaction")

    /** Hash of the list of components that are hidden in the [ContractUpgradeFilteredTransaction]. */
    private val hiddenComponentHash: SecureHash
        get() = serializedHash(listOf(legacyContractAttachmentId, upgradeContractClassName, privacySalt))

    override val id: SecureHash by lazy { serializedHash(inputs + notary).hashConcat(hiddenComponentHash) }

    /** Resolves input states and contract attachments, and builds a ContractUpgradeLedgerTransaction. */
    fun resolve(services: ServicesForResolution, sigs: List<TransactionSignature>) = resolve(services, services.attachments, sigs)

    fun resolve(stateLoader: StateLoader, attachments: AttachmentStorage, sigs: List<TransactionSignature>): ContractUpgradeLedgerTransaction {
        val resolvedInputs = inputs.map { ref ->
            stateLoader.loadState(ref).let { StateAndRef(it, ref) }
        }
        val legacyContractClassName = resolvedInputs.first().state.contract
        val legacyContractAttachment = attachments.openAttachment(legacyContractAttachmentId) ?: throw AttachmentResolutionException(legacyContractAttachmentId)
        val upgradedContractAttachment = attachments.openAttachment(upgradedContractAttachmentId) ?: throw AttachmentResolutionException(upgradedContractAttachmentId)

        return ContractUpgradeLedgerTransaction(
                resolvedInputs,
                notary,
                ContractAttachment(legacyContractAttachment, legacyContractClassName),
                ContractAttachment(upgradedContractAttachment, upgradeContractClassName),
                id,
                privacySalt,
                sigs
        )
    }

    fun buildFilteredTransaction(): ContractUpgradeFilteredTransaction {
        return ContractUpgradeFilteredTransaction(inputs, notary, hiddenComponentHash)
    }
}

/**
 * A filtered version of the [ContractUpgradeWireTransaction]. In comparison with a regular [FilteredTransaction], there
 * is no flexibility on what parts of the transaction to reveal – the inputs and notary field are always visible and the
 * rest of the transaction is always hidden. Its only purpose is to hide transaction data when using a non-validating notary.
 */
@CordaSerializable
data class ContractUpgradeFilteredTransaction(
        val inputs: List<StateRef>,
        val notary: Party,
        /** Hash of the hidden components of the [ContractUpgradeWireTransaction]. */
        val rest: SecureHash
) : NamedByHash {
    override val id: SecureHash get() = serializedHash(inputs + notary).hashConcat(rest)
}

/**
 * A contract upgrade transaction with fully resolved inputs and signatures. In contrast with a regular transaction,
 * signatures are checked against the signers specified by input states' *participants* fields, so full resolution is
 * needed for signature verification.
 */
data class ContractUpgradeLedgerTransaction(
        override val inputs: List<StateAndRef<ContractState>>,
        override val notary: Party,
        val legacyContractAttachment: ContractAttachment,
        val upgradedContractAttachment: ContractAttachment,
        override val id: SecureHash,
        val privacySalt: PrivacySalt,
        override val sigs: List<TransactionSignature>
) : FullTransaction(), TransactionWithSignatures {
    private val upgradedContract: UpgradedContract<ContractState, *> by lazy {
        @Suppress("UNCHECKED_CAST")
        this::class.java.classLoader.loadClass(upgradedContractAttachment.contract).
                asSubclass(Contract::class.java).
                getConstructor().
                newInstance() as UpgradedContract<ContractState, *>
    }

    init {
        check(inputs.all { it.state.contract == legacyContractAttachment.contract }) {
            "All input states must point to the legacy contract"
        }
        check(inputs.all { it.state.constraint.isSatisfiedBy(legacyContractAttachment) }) {
            "Legacy contract constrain does not satisfy the constraint of the input states"
        }
        check(upgradedContract.legacyContract == legacyContractAttachment.contract &&
                upgradedContract.legacyContractConstraint.isSatisfiedBy(legacyContractAttachment)) {
            "Outputs' contract must be an upgraded version of the inputs' contract"
        }
    }

    /**
     * Outputs are computed by running the contract upgrade logic on input states. This is done eagerly so that the
     * transaction is verified during construction.
     */
    override val outputs: List<TransactionState<ContractState>> = inputs.map { input ->
        val upgradedState = upgradedContract.upgrade(input.state.data)
        input.state.copy(
                data = upgradedState,
                constraint = HashAttachmentConstraint(upgradedContractAttachment.id)
        )
    }

    /** The required signers are the set of all input states' participants. */
    override val requiredSigningKeys: Set<PublicKey>
        get() = inputs.flatMap { it.state.data.participants }.map { it.owningKey }.toSet() + notary.owningKey

    override fun getKeyDescriptions(keys: Set<PublicKey>): List<String> {
        return keys.map { it.toBase58String() }
    }
}