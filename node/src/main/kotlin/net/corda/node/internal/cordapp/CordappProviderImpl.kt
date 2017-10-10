package net.corda.node.internal.cordapp

import com.google.common.collect.HashBiMap
import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappContext
import net.corda.core.cordapp.CordappProvider
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.SingletonSerializeAsToken

/**
 * Cordapp provider and store. For querying CorDapps for their attachment and vice versa.
 */
open class CordappProviderImpl(private val cordappLoader: CordappLoader, attachmentStorage: AttachmentStorage) : SingletonSerializeAsToken(), CordappProviderInternal {
    override fun getAppContext(): CordappContext {
        // TODO: Use better supported APIs in Java 9
        Exception().stackTrace.forEach { stackFrame ->
            val cordapp = getCordappForClass(stackFrame.className)
            if (cordapp != null) {
                return getAppContext(cordapp)
            }
        }

        throw IllegalStateException("Not in an app context")
    }

    override fun getContractAttachmentID(contractClassName: ContractClassName): AttachmentId? {
        return getCordappForClass(contractClassName)?.let(this::getCordappAttachmentId)
    }

    /**
     * Current known CorDapps loaded on this node
     */
    override val cordapps get() = cordappLoader.cordapps
    private val cordappAttachments = HashBiMap.create(loadContractsIntoAttachmentStore(attachmentStorage))
    /**
     * Gets the attachment ID of this CorDapp. Only CorDapps with contracts have an attachment ID
     *
     * @param cordapp The cordapp to get the attachment ID
     * @return An attachment ID if it exists, otherwise nothing
     */
    fun getCordappAttachmentId(cordapp: Cordapp): SecureHash? = cordappAttachments.inverse().get(cordapp)

    private fun loadContractsIntoAttachmentStore(attachmentStorage: AttachmentStorage): Map<SecureHash, Cordapp> {
        val cordappsWithAttachments = cordapps.filter { !it.contractClassNames.isEmpty() }
        val attachmentIds = cordappsWithAttachments.map { it.jarPath.openStream().use { attachmentStorage.importAttachment(it) } }
        return attachmentIds.zip(cordappsWithAttachments).toMap()
    }

    /**
     * Get the current cordapp context for the given CorDapp
     *
     * @param cordapp The cordapp to get the context for
     * @return A cordapp context for the given CorDapp
     */
    fun getAppContext(cordapp: Cordapp): CordappContext {
        return CordappContext(cordapp, getCordappAttachmentId(cordapp), cordappLoader.appClassLoader)
    }

    /**
     * Resolves a cordapp for the provided class or null if there isn't one
     *
     * @param className The class name
     * @return cordapp A cordapp or null if no cordapp has the given class loaded
     */
    fun getCordappForClass(className: String): Cordapp? = cordapps.find { it.cordappClasses.contains(className) }
}
