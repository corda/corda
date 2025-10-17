package net.corda.core.internal.verification

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ComponentGroupEnum.OUTPUTS_GROUP
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.AttachmentTrustCalculator
import net.corda.core.internal.SerializedTransactionState
import net.corda.core.internal.TRUSTED_UPLOADERS
import net.corda.core.internal.cordapp.CordappProviderInternal
import net.corda.core.internal.entries
import net.corda.core.internal.getRequiredGroup
import net.corda.core.internal.getRequiredTransaction
import net.corda.core.internal.mapToSet
import net.corda.core.internal.sortAttachments
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
interface NodeVerificationSupport : VerificationSupport {

    private companion object {
        val DB_SEARCH_DISABLED = java.lang.Boolean.getBoolean("net.corda.node.transactionbuilder.missingClassDbSearchDisabled")
        val INSTALLED_FIRST_SEARCH_DISABLED = java.lang.Boolean.getBoolean("net.corda.node.transactionbuilder.installedFirstSearchDisabled")
    }

    val networkParameters: NetworkParameters

    val validatedTransactions: TransactionStorage

    val identityService: IdentityService

    val attachments: AttachmentStorage

    val networkParametersService: NetworkParametersService

    val cordappProvider: CordappProviderInternal

    val attachmentTrustCalculator: AttachmentTrustCalculator

    val externalVerifierHandle: ExternalVerifierHandle

    override val appClassLoader: ClassLoader
        get() = cordappProvider.appClassLoader

    // TODO Bulk party lookup?
    override fun getParties(keys: Collection<PublicKey>): List<Party?> = keys.map(identityService::partyFromKey)

    override fun getAttachment(id: SecureHash): Attachment? = attachments.openAttachment(id)

    override fun getNetworkParameters(id: SecureHash?): NetworkParameters? {
        return if (id != null) networkParametersService.lookup(id) else networkParameters
    }

    /**
     * This is the main logic that knows how to retrieve the binary representation of [StateRef]s.
     *
     * For [ContractUpgradeWireTransaction] or [NotaryChangeWireTransaction] it knows how to recreate the output state in the
     * correct classloader independent of the node's classpath.
     */
    override fun getSerializedState(stateRef: StateRef): SerializedTransactionState {
        val coreTransaction = validatedTransactions.getRequiredTransaction(stateRef.txhash).coreTransaction
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
        return coreTransaction.componentGroups.getRequiredGroup(OUTPUTS_GROUP).components[outputIndex] as SerializedTransactionState
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

        return AttachmentsClassLoaderBuilder.withAttachmentsClassLoaderContext(
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
     * Returns a list of `Attachment`s containing [className], prioritising installed CorDapps first.
     * Legacy contracts are only searched in legacy CorDapps. Non-legacy may optionally fall back to searching the attachment
     * store (database) if no matching installed CorDapp is found and fallback is enabled.
     * ### System properties:
     * - `net.corda.node.transactionbuilder.missingClassDbSearchDisabled`:
     *   If `true`, disables database fallback. Only installed CorDapps will be searched.
     * - `net.corda.node.transactionbuilder.installedFirstSearchDisabled`:
     *   If `true`, disables the new “installed-first” lookup logic and reverts to the old database-only behaviour.
     * @param className Fully qualified class name to search for
     * @param isLegacy Whether to search for legacy contract attachments
     * @return list of matching attachments in deterministic order (version descending, then ID ascending)
     */
    override fun getTrustedClassAttachments(className: String, isLegacy: Boolean): List<Attachment> {
        val fileName = "$className.class"

        // 1. Old logic only
        if (INSTALLED_FIRST_SEARCH_DISABLED) {
            val dbAttachmentIds = attachments.queryAttachments(
                    AttachmentsQueryCriteria().withUploader(Builder.`in`(TRUSTED_UPLOADERS)),
                    AttachmentSort(listOf(AttachmentSortColumn(AttachmentSortAttribute.VERSION, Sort.Direction.DESC)))
            )
            val dbAttachments = dbAttachmentIds.mapNotNull { id -> attachments.openAttachment(id)?.takeIf { it.hasFile(fileName) } }
            return if (isLegacy) {
                val legacyContractCordapps = cordappProvider.legacyContractCordapps.mapToSet { it.jarHash }
                dbAttachments.filter { it.id in legacyContractCordapps }.sortAttachments()
            } else {
                dbAttachments.sortAttachments()
            }
        }

        // 2. Get installed CorDapps first
        val installedCordapps = if (isLegacy) {
            cordappProvider.legacyContractCordapps
        } else {
            cordappProvider.cordapps
        }

        val installedAttachments = installedCordapps
                .mapNotNull { attachments.openAttachment(it.jarHash) }
                .filter { it.hasFile(fileName) }

        if (installedAttachments.isNotEmpty()) {
            return installedAttachments.sortAttachments()
        }

        // 3. Optional fall back to DB attachments (non-legacy only)
        if (!isLegacy && !DB_SEARCH_DISABLED) {
            val dbAttachments = attachments.queryAttachments(
                    AttachmentsQueryCriteria().withUploader(Builder.`in`(TRUSTED_UPLOADERS)),
                    AttachmentSort(listOf(AttachmentSortColumn(AttachmentSortAttribute.VERSION, Sort.Direction.DESC)))
            )
            val matchingDbAttachments = dbAttachments.mapNotNull { id -> attachments.openAttachment(id)?.takeIf { it.hasFile(fileName) && it.isJdk17Jar() } }
            if (matchingDbAttachments.isNotEmpty()) {
                return matchingDbAttachments.sortAttachments()
            }
        }
        return emptyList()
    }

    @Suppress("MagicNumber")
    private fun Attachment.isJdk17Jar(): Boolean = openAsJAR().use { jar ->
        val firstClass = jar.entries().firstOrNull { it.name.endsWith(".class") }
        if (firstClass != null) {
            val header = ByteArray(8)
            jar.read(header)
            val major = ((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)
            major == 61
        } else {
            false
        }
    }

    private fun Attachment.hasFile(className: String): Boolean = openAsJAR().use { it.entries().any { entry -> entry.name == className } }

    override fun isAttachmentTrusted(attachment: Attachment): Boolean = attachmentTrustCalculator.calculate(attachment)

    override fun fixupAttachmentIds(attachmentIds: Collection<SecureHash>): Set<SecureHash> {
        return cordappProvider.attachmentFixups.fixupAttachmentIds(attachmentIds)
    }
}
