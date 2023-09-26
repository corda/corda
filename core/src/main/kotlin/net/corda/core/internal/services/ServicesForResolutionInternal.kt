@file:Suppress("ThrowsCount", "ForbiddenComment")

package net.corda.core.internal.services

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.AttachmentResolutionException
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.SerializedStateAndRef
import net.corda.core.internal.TRUSTED_UPLOADERS
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.AttachmentSort
import net.corda.core.node.services.vault.AttachmentSort.AttachmentSortAttribute
import net.corda.core.node.services.vault.Builder
import net.corda.core.node.services.vault.Sort
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.transactions.WireTransaction.Companion.resolveStateRefBinaryComponent
import java.security.PublicKey
import java.util.LinkedHashSet
import java.util.jar.JarInputStream

interface ServicesForResolutionInternal : ServicesForResolution, VerificationSupport {
    override val cordappProvider: CordappProviderInternal

    override val appClassLoader: ClassLoader
        get() = cordappProvider.cordappLoader.appClassLoader

    override fun getAttachment(id: SecureHash): Attachment? = attachments.openAttachment(id)

    override fun getNetworkParameters(id: SecureHash?): NetworkParameters? {
        return networkParametersService.lookup(id ?: networkParametersService.defaultHash)
    }

    override fun getParty(key: PublicKey): Party? = identityService.partyFromKey(key)

    /**
     * Scans trusted (installed locally) attachments to find all that contain the [className].
     * This is required as a workaround until explicit cordapp dependencies are implemented.
     * DO NOT USE IN CLIENT code.
     *
     * @return the attachments with the highest version.
     *
     * TODO: Should throw when the class is found in multiple contract attachments (not different versions).
     */
    override fun getTrustedClassAttachment(className: String): Attachment? {
        val allTrusted = attachments.queryAttachments(
                AttachmentQueryCriteria.AttachmentsQueryCriteria().withUploader(Builder.`in`(TRUSTED_UPLOADERS)),
                AttachmentSort(listOf(AttachmentSort.AttachmentSortColumn(AttachmentSortAttribute.VERSION, Sort.Direction.DESC)))
        )

        // TODO - add caching if performance is affected.
        for (attId in allTrusted) {
            val attch = attachments.openAttachment(attId)!!
            if (attch.openAsJAR().use { hasFile(it, "$className.class") }) return attch
        }
        return null
    }

    private fun hasFile(jarStream: JarInputStream, className: String): Boolean {
        while (true) {
            val e = jarStream.nextJarEntry ?: return false
            if (e.name == className) {
                return true
            }
        }
    }

    override fun loadContractAttachment(stateRef: StateRef): Attachment {
        // We may need to recursively chase transactions if there are notary changes.
        return loadContractAttachment(stateRef, null)
    }

    private fun loadContractAttachment(stateRef: StateRef, forContractClassName: String?): Attachment {
        val ctx = getRequiredSignedTransaction(stateRef.txhash).coreTransaction
        when (ctx) {
            is WireTransaction -> {
                val transactionState = ctx.outRef<ContractState>(stateRef.index).state
                for (attachmentId in ctx.attachments) {
                    val attachment = attachments.openAttachment(attachmentId)
                    if (attachment is ContractAttachment && (forContractClassName ?: transactionState.contract) in attachment.allContracts) {
                        return attachment
                    }
                }
                throw AttachmentResolutionException(stateRef.txhash)
            }
            is ContractUpgradeWireTransaction -> {
                return attachments.openAttachment(ctx.upgradedContractAttachmentId) ?: throw AttachmentResolutionException(stateRef.txhash)
            }
            is NotaryChangeWireTransaction -> {
                // TODO: check only one (or until one is resolved successfully), max recursive invocations check?
                val input = ctx.inputs.firstOrNull() ?: throw AttachmentResolutionException(stateRef.txhash)
                val stateContract = SerializedStateAndRef(resolveStateRefBinaryComponent(stateRef, this)!!, stateRef)
                        .toStateAndRef()
                        .state
                        .contract
                return loadContractAttachment(input, stateContract)
            }
            else -> throw UnsupportedOperationException("Attempting to resolve attachment for index ${stateRef.index} of a " +
                    "${ctx.javaClass} transaction. This is not supported.")
        }
    }

    override fun loadState(stateRef: StateRef): TransactionState<*> = super.loadState(stateRef)

    override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> = loadStates(stateRefs, LinkedHashSet())
}

fun ServicesForResolution.asInternal(): ServicesForResolutionInternal = this as? ServicesForResolutionInternal ?: mockServicesVerifyingView
