package net.corda.flows

import net.corda.core.contracts.Attachment
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.serialization.SerializationToken
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import java.io.InputStream

/**
 * Given a set of hashes either loads from from local storage  or requests them from the other peer. Downloaded
 * attachments are saved to local storage automatically.
 */
class FetchAttachmentsFlow(requests: Set<SecureHash>,
                           otherSide: Party) : FetchDataFlow<Attachment, ByteArray>(requests, otherSide) {

    override fun load(txid: SecureHash): Attachment? = serviceHub.storageService.attachments.openAttachment(txid)

    override fun convert(wire: ByteArray): Attachment = ByteArrayAttachment(wire)

    override fun maybeWriteToDisk(downloaded: List<Attachment>) {
        for (attachment in downloaded) {
            serviceHub.storageService.attachments.importAttachment(attachment.open())
        }
    }

    private class ByteArrayAttachment(private val wire: ByteArray) : Attachment, SerializeAsToken {
        override val id: SecureHash by lazy { wire.sha256() }
        override fun open(): InputStream = wire.inputStream()
        override fun equals(other: Any?) = other === this || other is Attachment && other.id == this.id
        override fun hashCode(): Int = id.hashCode()
        override fun toString(): String = "${javaClass.simpleName}(id=$id)"

        private class Token(private val id: SecureHash) : SerializationToken {
            override fun fromToken(context: SerializeAsTokenContext) = ByteArrayAttachment(context.attachmentReloader.reloadAttachment(id))
        }

        override fun toToken(context: SerializeAsTokenContext) = Token(id)

    }
}
