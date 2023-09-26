package net.corda.core.internal.services

import net.corda.core.contracts.Attachment
import net.corda.core.identity.Party
import net.corda.core.internal.Verifier
import net.corda.core.internal.isUploaderTrusted
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.core.serialization.internal.AttachmentsClassLoaderCache
import net.corda.core.transactions.SignedTransaction
import java.security.PublicKey

/**
 * In addition to [StateResolutionSupport], represents the operations required to resolve and verify a [SignedTransaction].
 *
 * @see SignedTransaction.verify
 * @see SignedTransaction.toLedgerTransaction
 */
interface VerificationSupport : StateResolutionSupport {
    val attachmentsClassLoaderCache: AttachmentsClassLoaderCache

    val fixupService: FixupService

    fun getParty(key: PublicKey): Party?

    fun isAttachmentTrusted(attachment: Attachment): Boolean

    fun getTrustedClassAttachment(className: String): Attachment?

    /**
     * Apply this node's attachment fix-up rules to the given attachments.
     *
     * @param attachments A collection of [Attachment] objects, e.g. as provided by a transaction.
     * @return The [attachments] with the node's fix-up rules applied.
     */
    fun fixupAttachments(attachments: Collection<Attachment>): Collection<Attachment> {
        val attachmentsById = attachments.associateByTo(LinkedHashMap(), Attachment::id)
        val replacementIds = fixupService.fixupAttachmentIds(attachmentsById.keys)
        attachmentsById.keys.retainAll(replacementIds)
        (replacementIds - attachmentsById.keys).forEach { extraId ->
            val extraAttachment = getAttachment(extraId)
            if (extraAttachment == null || !extraAttachment.isUploaderTrusted()) {
                throw MissingAttachmentsException(listOf(extraId))
            }
            attachmentsById[extraId] = extraAttachment
        }
        return attachmentsById.values
    }

    fun doVerify(verifier: Verifier) {
        verifier.verify()
    }
}
