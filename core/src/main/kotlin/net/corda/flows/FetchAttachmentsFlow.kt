package net.corda.flows

import net.corda.core.contracts.Attachment
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Given a set of hashes either loads from from local storage  or requests them from the other peer. Downloaded
 * attachments are saved to local storage automatically.
 */
class FetchAttachmentsFlow(requests: Set<SecureHash>,
                           otherSide: Party) : FetchDataFlow<Attachment, ByteArray>(requests, otherSide) {

    override fun load(txid: SecureHash): Attachment? = serviceHub.storageService.attachments.openAttachment(txid)

    override fun convert(wire: ByteArray): Attachment {
        return object : Attachment {
            override fun open(): InputStream = ByteArrayInputStream(wire)
            override val id: SecureHash = wire.sha256()
            override fun equals(other: Any?) = (other is Attachment) && other.id == id
            override fun hashCode(): Int = id.hashCode()
        }
    }

    override fun maybeWriteToDisk(downloaded: List<Attachment>) {
        for (attachment in downloaded) {
            serviceHub.storageService.attachments.importAttachment(attachment.open())
        }
    }
}
