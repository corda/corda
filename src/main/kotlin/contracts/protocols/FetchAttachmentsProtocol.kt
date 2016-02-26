/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts.protocols

import core.Attachment
import core.crypto.SecureHash
import core.crypto.sha256
import core.messaging.SingleMessageRecipient
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Given a set of hashes either loads from from local storage  or requests them from the other peer. Downloaded
 * attachments are saved to local storage automatically.
 */
class FetchAttachmentsProtocol(requests: Set<SecureHash>,
                               otherSide: SingleMessageRecipient) : FetchDataProtocol<Attachment, ByteArray>(requests, otherSide) {
    companion object {
        const val TOPIC = "platform.fetch.attachment"
    }

    override fun load(txid: SecureHash): Attachment? = serviceHub.storageService.attachments.openAttachment(txid)

    override val queryTopic: String = TOPIC

    override fun convert(wire: ByteArray): Attachment {
        return object : Attachment {
            override fun open(): InputStream = ByteArrayInputStream(wire)
            override val id: SecureHash = wire.sha256()
        }
    }

    override fun maybeWriteToDisk(downloaded: List<Attachment>) {
        for (attachment in downloaded) {
            serviceHub.storageService.attachments.importAttachment(attachment.open())
        }
    }
}