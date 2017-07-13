package net.corda.flows

import net.corda.core.contracts.AbstractAttachment
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.serialization.SerializationToken
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext

/**
 * Given a set of hashes either loads from from local storage  or requests them from the other peer. Downloaded
 * attachments are saved to local storage automatically.
 */
@InitiatingFlow
class FetchAttachmentsFlow(requests: Set<SecureHash>,
                           otherSide: Party) : FetchDataFlow<Attachment, ByteArray>(requests, otherSide) {

    override fun load(txid: SecureHash): Attachment? = serviceHub.attachments.openAttachment(txid)

    override fun convert(wire: ByteArray): Attachment = FetchedAttachment({ wire })

    override fun maybeWriteToDisk(downloaded: List<Attachment>) {
        for (attachment in downloaded) {
            serviceHub.attachments.importAttachment(attachment.open())
        }
    }

    private class FetchedAttachment(dataLoader: () -> ByteArray) : AbstractAttachment(dataLoader), SerializeAsToken {
        override val id: SecureHash by lazy { attachmentData.sha256() }

        private class Token(private val id: SecureHash) : SerializationToken {
            override fun fromToken(context: SerializeAsTokenContext) = FetchedAttachment(context.attachmentDataLoader(id))
        }

        override fun toToken(context: SerializeAsTokenContext) = Token(id)
    }
}
