package net.corda.node.internal.cordapp

import com.google.common.collect.HashBiMap
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappContext
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.DEPLOYED_CORDAPP_UPLOADER
import net.corda.core.internal.createCordappContext
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Cordapp provider and store. For querying CorDapps for their attachment and vice versa.
 */
open class CordappProviderImpl(private val cordappLoader: CordappLoader,
                               private val cordappConfigProvider: CordappConfigProvider,
                               attachmentStorage: AttachmentStorage,
                               private val whitelistedContractImplementations: Map<String, List<AttachmentId>>) : SingletonSerializeAsToken(), CordappProviderInternal {

    companion object {
        private val log = loggerFor<CordappProviderImpl>()
    }

    private val contextCache = ConcurrentHashMap<Cordapp, CordappContext>()

    /**
     * Current known CorDapps loaded on this node
     */
    override val cordapps get() = cordappLoader.cordapps
    private val cordappAttachments = HashBiMap.create(loadContractsIntoAttachmentStore(attachmentStorage))

    init {
        verifyInstalledCordapps(attachmentStorage)
    }

    private fun verifyInstalledCordapps(attachmentStorage: AttachmentStorage) {

        if (whitelistedContractImplementations.isEmpty()) {
            log.warn("The network parameters don't specify any whitelisted contract implementations. Please contact your zone operator. See https://docs.corda.net/network-map.html")
            return
        }

        // Verify that the installed contract classes correspond with the whitelist hash
        // And warn if node is not using latest CorDapp
        cordappAttachments.keys.map(attachmentStorage::openAttachment).mapNotNull { it as? ContractAttachment }.forEach { attch ->
            (attch.allContracts intersect whitelistedContractImplementations.keys).forEach { contractClassName ->
                when {
                    attch.id !in whitelistedContractImplementations[contractClassName]!! -> log.error("Contract $contractClassName found in attachment ${attch.id} is not whitelisted in the network parameters. If this is a production node contact your zone operator. See https://docs.corda.net/network-map.html")
                    attch.id != whitelistedContractImplementations[contractClassName]!!.last() -> log.warn("You are not using the latest CorDapp version for contract: $contractClassName. Please contact your zone operator.")
                }
            }
        }
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
    fun getCordappAttachmentId(cordapp: Cordapp): SecureHash? = cordappAttachments.inverse().get(cordapp.jarPath)

    private fun loadContractsIntoAttachmentStore(attachmentStorage: AttachmentStorage): Map<SecureHash, URL> =
            cordapps.filter { !it.contractClassNames.isEmpty() }.map {
                it.jarPath.openStream().use { stream ->
                    try {
                        attachmentStorage.importAttachment(stream, DEPLOYED_CORDAPP_UPLOADER, null)
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
        return contextCache.computeIfAbsent(cordapp, {
            createCordappContext(
                    cordapp,
                    getCordappAttachmentId(cordapp),
                    cordappLoader.appClassLoader,
                    TypesafeCordappConfig(cordappConfigProvider.getConfigByName(cordapp.name))
            )
        })
    }

    /**
     * Resolves a cordapp for the provided class or null if there isn't one
     *
     * @param className The class name
     * @return cordapp A cordapp or null if no cordapp has the given class loaded
     */
    fun getCordappForClass(className: String): Cordapp? = cordapps.find { it.cordappClasses.contains(className) }
}
