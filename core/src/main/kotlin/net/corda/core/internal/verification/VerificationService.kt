package net.corda.core.internal.verification

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.AttachmentTrustCalculator
import net.corda.core.internal.SerializedTransactionState
import net.corda.core.internal.TRUSTED_UPLOADERS
import net.corda.core.internal.entries
import net.corda.core.internal.getRequiredTransaction
import net.corda.core.node.NetworkParameters
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.NetworkParametersService
import net.corda.core.node.services.TransactionStorage
import net.corda.core.node.services.vault.AttachmentQueryCriteria.AttachmentsQueryCriteria
import net.corda.core.node.services.vault.AttachmentSort
import net.corda.core.node.services.vault.AttachmentSort.AttachmentSortAttribute
import net.corda.core.node.services.vault.AttachmentSort.AttachmentSortColumn
import net.corda.core.node.services.vault.Builder
import net.corda.core.node.services.vault.Sort
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.AttachmentsClassLoaderBuilder
import net.corda.core.serialization.serialize
import net.corda.core.transactions.ContractUpgradeLedgerTransaction
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.MissingContractAttachments
import net.corda.core.transactions.NotaryChangeLedgerTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.WireTransaction
import java.security.PublicKey

/**
 * Implements [VerificationSupport] in terms of node-based services.
 */
interface VerificationService : VerificationSupport {
    val transactionStorage: TransactionStorage

    val identityService: IdentityService

    val attachmentStorage: AttachmentStorage

    val networkParametersService: NetworkParametersService

    val attachmentTrustCalculator: AttachmentTrustCalculator

    val attachmentFixups: AttachmentFixups

    // TODO Bulk party lookup?
    override fun getParties(keys: Collection<PublicKey>): List<Party?> = keys.map(identityService::partyFromKey)

    override fun getAttachment(id: SecureHash): Attachment? = attachmentStorage.openAttachment(id)

    override fun getNetworkParameters(id: SecureHash?): NetworkParameters? {
        return networkParametersService.lookup(id ?: networkParametersService.defaultHash)
    }

    /**
     * This is the main logic that knows how to retrieve the binary representation of [StateRef]s.
     *
     * For [ContractUpgradeWireTransaction] or [NotaryChangeWireTransaction] it knows how to recreate the output state in the
     * correct classloader independent of the node's classpath.
     */
    override fun getSerializedState(stateRef: StateRef): SerializedTransactionState {
        val coreTransaction = transactionStorage.getRequiredTransaction(stateRef.txhash).coreTransaction
        return when (coreTransaction) {
            is WireTransaction -> getRegularOutput(coreTransaction, stateRef.index)
            is ContractUpgradeWireTransaction -> getContractUpdateOutput(coreTransaction, stateRef.index)
            is NotaryChangeWireTransaction -> getNotaryChangeOutput(coreTransaction, stateRef.index)
            else -> throw UnsupportedOperationException("Attempting to resolve input ${stateRef.index} of a ${coreTransaction.javaClass} " +
                    "transaction. This is not supported.")
        }
    }

    private fun getRegularOutput(coreTransaction: WireTransaction, outputIndex: Int): SerializedTransactionState {
        @Suppress("UNCHECKED_CAST")
        return coreTransaction.componentGroups
                .first { it.groupIndex == ComponentGroupEnum.OUTPUTS_GROUP.ordinal }
                .components[outputIndex] as SerializedTransactionState
    }

    /**
     * Creates a binary serialized component for a virtual output state serialised and executed with the attachments from the transaction.
     */
    @Suppress("ThrowsCount")
    private fun getContractUpdateOutput(wtx: ContractUpgradeWireTransaction, outputIndex: Int): SerializedTransactionState {
        val binaryInput = getSerializedState(wtx.inputs[outputIndex])
        val legacyContractAttachment = getAttachment(wtx.legacyContractAttachmentId) ?: throw MissingContractAttachments(emptyList())
        val upgradedContractAttachment = getAttachment(wtx.upgradedContractAttachmentId) ?: throw MissingContractAttachments(emptyList())
        val networkParameters = getNetworkParameters(wtx.networkParametersHash) ?: throw TransactionResolutionException(wtx.id)

        return AttachmentsClassLoaderBuilder.withAttachmentsClassloaderContext(
                listOf(legacyContractAttachment, upgradedContractAttachment),
                networkParameters,
                wtx.id,
                ::isAttachmentTrusted,
                attachmentsClassLoaderCache = attachmentsClassLoaderCache
        ) { serializationContext ->
            val upgradedContract = ContractUpgradeLedgerTransaction.loadUpgradedContract(wtx.upgradedContractClassName, wtx.id, serializationContext.deserializationClassLoader)
            val outputState = ContractUpgradeWireTransaction.calculateUpgradedState(binaryInput.deserialize(), upgradedContract, upgradedContractAttachment)
            outputState.serialize()
        }
    }

    /**
     * This should return a serialized virtual output state, that will be used to verify spending transactions.
     * The binary output should not depend on the classpath of the node that is verifying the transaction.
     *
     * Ideally the serialization engine would support partial deserialization so that only the Notary ( and the encumbrance can be replaced
     * from the binary input state)
     */
    // TODO - currently this uses the main classloader.
    private fun getNotaryChangeOutput(wtx: NotaryChangeWireTransaction, outputIndex: Int): SerializedTransactionState {
        val input = getStateAndRef(wtx.inputs[outputIndex])
        val output = NotaryChangeLedgerTransaction.computeOutput(input, wtx.newNotary) { wtx.inputs }
        return output.serialize()
    }

    /**
     * Scans trusted (installed locally) attachments to find all that contain the [className].
     * This is required as a workaround until explicit cordapp dependencies are implemented.
     *
     * @return the attachments with the highest version.
     */
    // TODO Should throw when the class is found in multiple contract attachments (not different versions).
    override fun getTrustedClassAttachment(className: String): Attachment? {
        val allTrusted = attachmentStorage.queryAttachments(
                AttachmentsQueryCriteria().withUploader(Builder.`in`(TRUSTED_UPLOADERS)),
                AttachmentSort(listOf(AttachmentSortColumn(AttachmentSortAttribute.VERSION, Sort.Direction.DESC)))
        )

        // TODO - add caching if performance is affected.
        for (attId in allTrusted) {
            val attch = attachmentStorage.openAttachment(attId)!!
            if (attch.hasFile("$className.class")) return attch
        }
        return null
    }

    private fun Attachment.hasFile(className: String): Boolean = openAsJAR().use { it.entries().any { entry -> entry.name == className } }

    override fun isAttachmentTrusted(attachment: Attachment): Boolean = attachmentTrustCalculator.calculate(attachment)

    override fun fixupAttachmentIds(attachmentIds: Collection<SecureHash>): Set<SecureHash> {
        return attachmentFixups.fixupAttachmentIds(attachmentIds)
    }
}
