package net.corda.node.internal.cordapp

import com.google.common.collect.HashBiMap
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentStorage
import java.util.*

/**
 * Cordapp provider and store. For querying CorDapps for their attachment and vice versa.
 */
class CordappProvider(private val attachmentStorage: AttachmentStorage, private val cordappLoader: CordappLoader) {
    /**
     * Current known CorDapps loaded on this node
     */
    val cordapps get() = cordappLoader.cordapps
    private lateinit var cordappAttachments: HashBiMap<SecureHash, Cordapp>

    /**
     * Should only be called once from the initialisation routine of the node or tests
     */
    fun start() {
        cordappAttachments = HashBiMap.create(loadContractsIntoAttachmentStore())
    }

    /**
     * Gets the attachment ID of this CorDapp. Only CorDapps with contracts have an attachment ID
     *
     * @param cordapp The cordapp to get the attachment ID
     * @return An attachment ID if it exists, otherwise nothing
     */
    fun getCordappAttachmentId(cordapp: Cordapp): Optional<SecureHash> {
        return if (cordappAttachments.containsValue(cordapp)) {
            Optional.of(cordappAttachments.inverse().get(cordapp)!!)
        } else {
            Optional.empty()
        }
    }

    private fun loadContractsIntoAttachmentStore(): Map<SecureHash, Cordapp> {
        val cordappsWithAttachments = cordapps.filter { !it.contractClassNames.isEmpty() }
        val attachmentIds = cordappsWithAttachments.map { it.jarPath.openStream().use { attachmentStorage.importAttachment(it) } }
        return attachmentIds.zip(cordappsWithAttachments).toMap()
    }
}