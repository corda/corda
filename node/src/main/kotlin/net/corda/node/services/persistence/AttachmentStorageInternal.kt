package net.corda.node.services.persistence

import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import java.io.InputStream

interface AttachmentStorageInternal : AttachmentStorage {
    /**
     * This is the same as [importAttachment] expect there are no checks done on the uploader field. This API is internal
     * and is only for the node.
     */
    fun privilegedImportAttachment(jar: InputStream, uploader: String, filename: String?): AttachmentId
}
