package net.corda.node.internal.cordapp

import com.google.common.collect.HashBiMap
import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappContext
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.network.NetworkParameters
import net.corda.nodeapi.internal.network.whitelistAllContractsForTest
import java.net.URL

/**
 * Cordapp provider and store. For querying CorDapps for their attachment and vice versa.
 */
open class CordappProviderImpl(private val cordappLoader: CordappLoader, attachmentStorage: AttachmentStorage, private val networkParameters: NetworkParameters) : SingletonSerializeAsToken(), CordappProviderInternal {

    companion object {
        private val log = loggerFor<CordappProviderImpl>()
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

    override fun getCurrentContractAttachmentID(contractClassName: ContractClassName): AttachmentId? {
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
    fun getCordappAttachmentId(cordapp: Cordapp): SecureHash? = cordappAttachments.inverse().get(cordapp.jarPath)

    private fun loadContractsIntoAttachmentStore(attachmentStorage: AttachmentStorage): Map<SecureHash, URL> {
        val cordappsWithAttachments = cordapps.filter { !it.contractClassNames.isEmpty() }.map { it to it.jarPath }.toMap()
        val attachmentIds = cordappsWithAttachments.map { (cordapp, url) ->
            url.openStream().use { stream -> attachmentStorage.importContractAttachment(cordapp.contractClassNames, stream) }
        }
        return attachmentIds.zip(cordappsWithAttachments.values).toMap()
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

    override fun getZoneWhitelistedContractAttachmentIds(contractClassName: ContractClassName): Set<AttachmentId> =
            if (networkParameters.whitelistedContractImplementations == whitelistAllContractsForTest) {
                whitelistAllContractsForTest.values.first().toSet()
            } else {
                networkParameters.whitelistedContractImplementations.get(contractClassName)?.toSet()
                        ?: throw IllegalStateException("Could not find valid attachment ids for $contractClassName")
            }
}
