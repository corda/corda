package net.corda.flows

import net.corda.core.contracts.AbstractAttachment
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.node.services.StorageService
import net.corda.core.serialization.SerializationToken
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import java.io.InputStream
import java.util.function.Supplier

/**
 * Given a set of hashes either loads from from local storage  or requests them from the other peer. Downloaded
 * attachments are saved to local storage automatically.
 */
class FetchAttachmentsFlow(requests: Set<SecureHash>,
                           otherSide: Party) : FetchDataFlow<Attachment, ByteArray>(requests, otherSide) {

    override fun load(txid: SecureHash): Attachment? = serviceHub.storageService.attachments.openAttachment(txid)

    override fun convert(wire: ByteArray): Attachment = ByteArrayAttachment({ wire })

    override fun maybeWriteToDisk(downloaded: List<Attachment>) {
        for (attachment in downloaded) {
            serviceHub.storageService.attachments.importAttachment(attachment.open())
        }
    }

    private class ByteArrayAttachment(dataLoader: () -> ByteArray) : AbstractAttachment(dataLoader), SerializeAsToken {
        override val id: SecureHash by lazy { attachmentData.sha256() }

        private class Token(private val id: SecureHash) : SerializationToken {
            override fun fromToken(context: SerializeAsTokenContext) = ByteArrayAttachment(context.attachmentDataLoader(id))
        }

        override fun toToken(context: SerializeAsTokenContext) = Token(id)
    }
}
