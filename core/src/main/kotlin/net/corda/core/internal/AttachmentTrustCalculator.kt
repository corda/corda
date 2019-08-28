package net.corda.core.internal

import net.corda.core.contracts.Attachment
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.CordaSerializable

/**
 * Calculates the trust of attachments stored in the node.
 */
interface AttachmentTrustCalculator {

    /**
     * Establishes whether an attachment should be trusted. This logic is required in order to verify transactions, as transaction
     * verification should only be carried out using trusted attachments.
     *
     * Attachments are trusted if one of the following is true:
     *  - They are uploaded by a trusted uploader
     *  - There is another attachment in the attachment store, that is trusted and is signed by at least one key that the input
     *  attachment is also signed with
     */
    fun calculate(attachment: Attachment): Boolean

    /**
     * Calculates the trust of attachments stored within the node. Applies the same logic as
     * [isAttachmentTrusted] when calculating the trust of an attachment.
     */
    fun calculateAllTrustRoots(): List<AttachmentTrustRoot>
}

/**
 * Data class containing information about an attachment's trust root.
 */
@CordaSerializable
data class AttachmentTrustRoot(
    val attachmentId: AttachmentId,
    val fileName: String?,
    val uploader: String?,
    val trustRootId: AttachmentId?,
    val trustRootFileName: String?
) {
    val isTrusted = trustRootId != null
    val isTrustRoot = attachmentId == trustRootId
}