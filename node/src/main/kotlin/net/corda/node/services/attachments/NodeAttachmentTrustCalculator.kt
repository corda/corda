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
import java.util.stream.Stream

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

        if (attachment.isUploaderTrusted()) return true

        if (attachment.isSignedByBlacklistedKey()) return false

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

    override fun calculateAllTrustInfo(): List<AttachmentTrustInfo> {

        val publicKeyToTrustRootMap = mutableMapOf<PublicKey, TrustedAttachment>()
        val attachmentTrustInfos = mutableListOf<AttachmentTrustInfo>()

        val db = checkNotNull(database) {
            // This should never be hit, except for tests that have not been setup correctly to test internal code
            "CordaPersistence has not been set"
        }
        db.transaction {
            getTrustedAttachments().use { trustedAttachments ->
                for ((name, attachment) in trustedAttachments) {
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
            }

            getUntrustedAttachments().use { untrustedAttachments ->
                for ((name, attachment) in untrustedAttachments) {
                    val trustRoot = if (attachment.isSignedByBlacklistedKey()) {
                        null
                    } else {
                        attachment.signerKeys
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
        }

        return attachmentTrustInfos
    }

    private fun getTrustedAttachments(): Stream<Pair<String?, Attachment>> {
        return attachmentStorage.getAllAttachmentsByCriteria(
            // `isSignedCondition` is not included here as attachments uploaded by trusted uploaders are considered trusted
            AttachmentQueryCriteria.AttachmentsQueryCriteria(
                uploaderCondition = Builder.`in`(
                    TRUSTED_UPLOADERS
                )
            )
        )
    }

    private fun getUntrustedAttachments(): Stream<Pair<String?, Attachment>> {
        return attachmentStorage.getAllAttachmentsByCriteria(
            // Filter by `isSignedCondition` so normal data attachments are not returned
            AttachmentQueryCriteria.AttachmentsQueryCriteria(
                uploaderCondition = Builder.notIn(
                    TRUSTED_UPLOADERS
                ),
                isSignedCondition = Builder.equal(true)
            )
        )
    }

    private data class TrustedAttachment(val id: AttachmentId, val name: String?)

    private fun Attachment.isSignedByBlacklistedKey() =
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