package net.corda.ext.api.attachment

import net.corda.core.CordaInternal
import net.corda.core.node.services.AttachmentId
import net.corda.nodeapi.exceptions.DuplicateAttachmentException
import java.io.InputStream

@CordaInternal
interface AttachmentImporter {

    /**
     * This is the same as [net.corda.core.node.services.AttachmentStorage.importAttachment] except there are no checks done
     * on the uploader field.
     */
    fun privilegedImportAttachment(jar: InputStream, uploader: String, filename: String?): AttachmentId

    /**
     * Similar to above but returns existing [AttachmentId] instead of throwing [DuplicateAttachmentException]
     */
    fun privilegedImportOrGetAttachment(jar: InputStream, uploader: String, filename: String?): AttachmentId
}