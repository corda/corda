package net.corda.node.internal.cordapp

import com.google.common.collect.HashBiMap
import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappContext
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.DEPLOYED_CORDAPP_UPLOADER
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.node.services.persistence.AttachmentStorageInternal
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Cordapp provider and store. For querying CorDapps for their attachment and vice versa.
 */
open class CordappProviderImpl(val cordappLoader: CordappLoader,
                               private val cordappConfigProvider: CordappConfigProvider,
                               private val attachmentStorage: AttachmentStorage) : SingletonSerializeAsToken(), CordappProviderInternal {
    companion object {
        private val log = contextLogger()
    }

    private val contextCache = ConcurrentHashMap<Cordapp, CordappContext>()
    private val cordappAttachments = HashBiMap.create<SecureHash, URL>()

    /**
     * Current known CorDapps loaded on this node
     */
    override val cordapps: List<CordappImpl> get() = cordappLoader.cordapps

    fun start() {
        cordappAttachments.putAll(loadContractsIntoAttachmentStore())
        verifyInstalledCordapps()
    }

    private fun verifyInstalledCordapps() {
        // This will invoke the lazy flowCordappMap property, thus triggering the MultipleCordappsForFlow check.
        cordappLoader.flowCordappMap
    }

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
     * Gets the attachment ID of this CorDapp. Only CorDapps with contracts have an attachment ID
     *
     * @param cordapp The cordapp to get the attachment ID
     * @return An attachment ID if it exists, otherwise nothing
     */
    fun getCordappAttachmentId(cordapp: Cordapp): SecureHash? = cordappAttachments.inverse()[cordapp.jarPath]

    private fun loadContractsIntoAttachmentStore(): Map<SecureHash, URL> =
            cordapps.filter { !it.contractClassNames.isEmpty() }.map {
                it.jarPath.openStream().use { stream ->
                    try {
                        // We can't make attachmentStorage a AttachmentStorageInternal as that ends up requiring
                        // MockAttachmentStorage to implement it.
                        if (attachmentStorage is AttachmentStorageInternal) {
                            attachmentStorage.privilegedImportAttachment(stream, DEPLOYED_CORDAPP_UPLOADER, null)
                        } else {
                            attachmentStorage.importAttachment(stream, DEPLOYED_CORDAPP_UPLOADER, null)
                        }
                    } catch (faee: java.nio.file.FileAlreadyExistsException) {
                        AttachmentId.parse(faee.message!!)
                    }
                } to it.jarPath
            }.toMap()

    /**
     * Get the current cordapp context for the given CorDapp
     *
     * @param cordapp The cordapp to get the context for
     * @return A cordapp context for the given CorDapp
     */
    fun getAppContext(cordapp: Cordapp): CordappContext {
        return contextCache.computeIfAbsent(cordapp) {
            CordappContext.create(
                    cordapp,
                    getCordappAttachmentId(cordapp),
                    cordappLoader.appClassLoader,
                    TypesafeCordappConfig(cordappConfigProvider.getConfigByName(cordapp.name))
            )
        }
    }

    /**
     * Resolves a cordapp for the provided class or null if there isn't one
     *
     * @param className The class name
     * @return cordapp A cordapp or null if no cordapp has the given class loaded
     */
    fun getCordappForClass(className: String): Cordapp? = cordapps.find { it.cordappClasses.contains(className) }

    override fun getCordappForFlow(flowLogic: FlowLogic<*>) = cordappLoader.flowCordappMap[flowLogic.javaClass]
}
