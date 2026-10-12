package net.corda.node.services.persistence

import net.corda.core.contracts.Attachment
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.nodeapi.exceptions.DuplicateAttachmentException
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.util.stream.Stream

interface AttachmentStorageInternal : AttachmentStorage {
    /**
     * This is the same as [importAttachment] expect there are no checks done on the uploader field. This API is internal
     * and is only for the node.
     */
    fun privilegedImportAttachment(jar: InputStream, uploader: String, filename: String?): AttachmentId {
        // Default implementation is not privileged
        return importAttachment(jar, uploader, filename)
    }

    /**
     * Similar to above but returns existing [AttachmentId] instead of throwing [DuplicateAttachmentException]
     */
    fun privilegedImportOrGetAttachment(jar: InputStream, uploader: String, filename: String?): AttachmentId {
        return try {
            privilegedImportAttachment(jar, uploader, filename)
        } catch (faee: FileAlreadyExistsException) {
            AttachmentId.create(faee.message!!)
        }
    }

    /**
     * Get all attachments as a [Stream], filtered by the input [AttachmentQueryCriteria],
     * stored within the node paired to their file names.
     *
     * The [Stream] must be closed once used.
     */
    fun getAllAttachmentsByCriteria(
            criteria: AttachmentQueryCriteria = AttachmentQueryCriteria.AttachmentsQueryCriteria()
    ): Stream<Pair<String?, Attachment>> {
        return queryAttachments(criteria).stream().map { null to openAttachment(it)!! }
    }
}

fun AttachmentStorage.toInternal(): AttachmentStorageInternal {
    return when (this) {
        is AttachmentStorageInternal -> this
        else -> object : AttachmentStorageInternal, AttachmentStorage by this {}
    }
}
