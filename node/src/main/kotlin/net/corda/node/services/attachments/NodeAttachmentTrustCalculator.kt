package net.corda.node.services.attachments

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.*
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.Builder
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.persistence.AttachmentStorageInternal
import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.security.PublicKey

/**
 * Implementation of [AttachmentTrustCalculator].
 *
 * @param blacklistedAttachmentSigningKeys Attachments signed by any of these public keys will not be considered as trust roots for any
 * attachments received over the network. The list consists of SHA-256 hashes of public keys
 */
class NodeAttachmentTrustCalculator(
    private val attachmentStorage: AttachmentStorageInternal,
    private val database: CordaPersistence?,
    cacheFactory: NamedCacheFactory,
    private val blacklistedAttachmentSigningKeys: List<SecureHash> = emptyList()
) : AttachmentTrustCalculator, SingletonSerializeAsToken() {

    @VisibleForTesting
    constructor(
        attachmentStorage: AttachmentStorageInternal,
        cacheFactory: NamedCacheFactory,
        blacklistedAttachmentSigningKeys: List<SecureHash> = emptyList()
    ) : this(attachmentStorage, null, cacheFactory, blacklistedAttachmentSigningKeys)

    // A cache for caching whether a signing key is trusted
    private val trustedKeysCache = cacheFactory.buildNamed<PublicKey, Boolean>(
        Caffeine.newBuilder(),
        "NodeAttachmentTrustCalculator_trustedKeysCache"
    )

    override fun calculate(attachment: Attachment): Boolean {
        val trustedByUploader = when (attachment) {
            is ContractAttachment, is AbstractAttachment -> isUploaderTrusted(attachment.uploader)
            else -> false
        }

        if (trustedByUploader) {
            // add signers to the cache as this is a fully trusted attachment
            attachment.signerKeys
                .filterNot { it.isBlacklisted() }
                .forEach { trustedKeysCache.put(it, true) }
            return true
        }

        if (attachment.isSignedByBlacklistedKeys()) return false

        return attachment.signerKeys.any { signer ->
            trustedKeysCache.get(signer) {
                val queryCriteria = AttachmentQueryCriteria.AttachmentsQueryCriteria(
                    signersCondition = Builder.equal(listOf(signer)),
                    uploaderCondition = Builder.`in`(TRUSTED_UPLOADERS)
                )
                attachmentStorage.queryAttachments(queryCriteria).isNotEmpty()
            }!!
        }
    }

    override fun calculateAllTrustRoots(): List<AttachmentTrustInfo> {

        val publicKeyToTrustRootMap = mutableMapOf<PublicKey, TrustedAttachment>()
        val attachmentTrustInfos = mutableListOf<AttachmentTrustInfo>()

        require(database != null) {
            // This should never be hit, except for tests that have not been setup correctly to test internal code
            "CordaPersistence has not been set"
        }
        database!!.transaction {
            for ((name, attachment) in getTrustedAttachments()) {
                attachment.signerKeys.forEach {
                    // add signers to the cache as this is a fully trusted attachment
                    trustedKeysCache.put(it, true)
                    publicKeyToTrustRootMap.putIfAbsent(
                        it,
                        TrustedAttachment(attachment.id, name)
                    )
                }
                attachmentTrustInfos += AttachmentTrustInfo(
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
                attachmentTrustInfos += AttachmentTrustInfo(
                    attachmentId = attachment.id,
                    fileName = name,
                    uploader = attachment.uploader,
                    trustRootId = trustRoot?.id,
                    trustRootFileName = trustRoot?.name
                )
            }
        }

        return attachmentTrustInfos
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