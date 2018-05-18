package net.corda.nodeapi.exceptions

import net.corda.core.flows.ClientRelevantError

/**
 * Thrown to indicate that an attachment was already uploaded to a Corda node.
 */
class DuplicateAttachmentException(attachmentHash: String) : java.nio.file.FileAlreadyExistsException("Attachment was already uploaded. Hash: $attachmentHash"), ClientRelevantError