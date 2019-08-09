package net.corda.core.internal.node.services

import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import java.io.InputStream

interface AttachmentStorageInternal : AttachmentStorage {

    val blacklistedAttachmentSigningKeys: List<SecureHash>

    /**
     * This is the same as [importAttachment] expect there are no checks done on the uploader field. This API is internal
     * and is only for the node.
     */
    fun privilegedImportAttachment(jar: InputStream, uploader: String, filename: String?): AttachmentId

    /**
     * Similar to above but returns existing [AttachmentId] instead of throwing [DuplicateAttachmentException]
     */
    fun privilegedImportOrGetAttachment(jar: InputStream, uploader: String, filename: String?): AttachmentId
}
