package net.corda.node.services.persistence

import net.corda.core.contracts.Attachment
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.ext.api.attachment.AttachmentImporter
import net.corda.nodeapi.exceptions.DuplicateAttachmentException
import java.io.InputStream
import java.util.stream.Stream

interface AttachmentStorageInternal : AttachmentStorage, AttachmentImporter {

    /**
     * Get all attachments as a [Stream], filtered by the input [AttachmentQueryCriteria],
     * stored within the node paired to their file names.
     *
     * The [Stream] must be closed once used.
     */
    fun getAllAttachmentsByCriteria(criteria: AttachmentQueryCriteria = AttachmentQueryCriteria.AttachmentsQueryCriteria()): Stream<Pair<String?, Attachment>>
}