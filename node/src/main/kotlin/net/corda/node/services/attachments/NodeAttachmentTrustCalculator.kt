package net.corda.node.services.attachments

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.*
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.Builder
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.node.services.persistence.AttachmentStorageInternal
import java.security.PublicKey

/**
 * Implementation of [AttachmentTrustCalculator].
 *
 * @param keysToBlacklist Is a string representation of a SHA-256 [SecureHash] which is converted
 * during initialisation of this class.
 */
class NodeAttachmentTrustCalculator(
    private val attachmentStorage: AttachmentStorageInternal,
    keysToBlacklist: List<String> = emptyList()
) : AttachmentTrustCalculator, SingletonSerializeAsToken() {

    private companion object {
        private val log = contextLogger()
    }

    // A cache for caching whether a signing key is trusted
    private val trustedKeysCache: MutableMap<PublicKey, Boolean> =
        createSimpleCache<PublicKey, Boolean>(100).toSynchronised()

    /**
     * Attachments signed by any of these public keys will not be considered as trust roots for any
     * attachments received over the network.
     *
     * The list consists of SHA-256 hashes of public keys
     */
    private val blacklistedAttachmentSigningKeys: List<SecureHash> = keysToBlacklist.mapNotNull {
        try {
            SecureHash.parse(it)
        } catch (e: IllegalArgumentException) {
            log.warn("Failed to parse blacklisted attachment signing key: $it. The key will not be added to the list of blacklisted attachment signing keys")
            null
        }
    }

    override fun calculate(attachment: Attachment): Boolean {
        val trustedByUploader = when (attachment) {
            is ContractAttachment, is AbstractAttachment -> isUploaderTrusted(attachment.uploader)
            else -> false
        }

        if (trustedByUploader) {
            // add signers to the cache as this is a fully trusted attachment
            attachment.signerKeys
                .filterNot { it.isBlacklisted() }
                .forEach { trustedKeysCache[it] = true }
            return true
        }

        if (attachment.isSignedByBlacklistedKeys()) return false

        return attachment.signerKeys.any { signer ->
            trustedKeysCache.computeIfAbsent(signer) {
                val queryCriteria = AttachmentQueryCriteria.AttachmentsQueryCriteria(
                    signersCondition = Builder.equal(listOf(signer)),
                    uploaderCondition = Builder.`in`(TRUSTED_UPLOADERS)
                )
                attachmentStorage.queryAttachments(queryCriteria).isNotEmpty()
            }
        }
    }

    override fun calculateAllTrustRoots(): List<AttachmentTrustRoot> {

        val publicKeyToTrustRootMap = mutableMapOf<PublicKey, TrustedAttachment>()
        val attachmentTrustRoots = mutableListOf<AttachmentTrustRoot>()

        for ((name, attachment) in getTrustedAttachments()) {
            attachment.signerKeys.forEach {
                // add signers to the cache as this is a fully trusted attachment
                trustedKeysCache[it] = true
                publicKeyToTrustRootMap.putIfAbsent(
                    it,
                    TrustedAttachment(attachment.id, name)
                )
            }
            attachmentTrustRoots += AttachmentTrustRoot(
                attachmentId = attachment.id,
                fileName = name,
                uploader = attachment.uploader,
                trustRootId = attachment.id,
                trustRootFileName = name
            )
        }

        for ((name, attachment) in getUntrustedAttachments()) {
            val trustRoot = when {
                attachment.isSignedByBlacklistedKeys() -> null
                else -> attachment.signerKeys
                    .mapNotNull { publicKeyToTrustRootMap[it] }
                    .firstOrNull()
            }
            attachmentTrustRoots += AttachmentTrustRoot(
                attachmentId = attachment.id,
                fileName = name,
                uploader = attachment.uploader,
                trustRootId = trustRoot?.id,
                trustRootFileName = trustRoot?.name
            )
        }

        return attachmentTrustRoots
    }

    private fun getTrustedAttachments() = attachmentStorage.getAllAttachmentsByCriteria(
        AttachmentQueryCriteria.AttachmentsQueryCriteria(
            uploaderCondition = Builder.`in`(
                TRUSTED_UPLOADERS
            )
        )
    )

    private fun getUntrustedAttachments() = attachmentStorage.getAllAttachmentsByCriteria(
        AttachmentQueryCriteria.AttachmentsQueryCriteria(
            uploaderCondition = Builder.notIn(
                TRUSTED_UPLOADERS
            )
        )
    )

    private data class TrustedAttachment(val id: AttachmentId, val name: String?)

    private fun Attachment.isSignedByBlacklistedKeys() =
        signerKeys.any { it.isBlacklisted() }

    private fun PublicKey.isBlacklisted() =
        blacklistedAttachmentSigningKeys.contains(this.hash)

    private val Attachment.uploader: String?
        get() = when (this) {
            is ContractAttachment -> uploader
            is AbstractAttachment -> uploader
            else -> null
        }
}