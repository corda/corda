package core.node.services

import core.SignedTransaction
import core.crypto.SecureHash
import core.messaging.Message
import core.messaging.MessagingService
import core.messaging.SingleMessageRecipient
import core.messaging.send
import core.serialization.deserialize
import core.utilities.loggerFor
import protocols.AbstractRequestMessage
import protocols.FetchAttachmentsProtocol
import protocols.FetchTransactionsProtocol
import java.io.InputStream
import javax.annotation.concurrent.ThreadSafe

/**
 * This class sets up network message handlers for requests from peers for data keyed by hash. It is a piece of simple
 * glue that sits between the network layer and the database layer.
 *
 * Note that in our data model, to be able to name a thing by hash automatically gives the power to request it. There
 * are no access control lists. If you want to keep some data private, then you must be careful who you give its name
 * to, and trust that they will not pass the name onwards. If someone suspects some data might exist but does not have
 * its name, then the 256-bit search space they'd have to cover makes it physically impossible to enumerate, and as
 * such the hash of a piece of data can be seen as a type of password allowing access to it.
 *
 * Additionally, because nodes do not store invalid transactions, requesting such a transaction will always yield null.
 */
@ThreadSafe
class DataVendingService(net: MessagingService, private val storage: StorageService) : AbstractNodeService(net) {
    companion object {
        val logger = loggerFor<DataVendingService>()
    }

    init {
        addMessageHandler(FetchTransactionsProtocol.TOPIC,
                { req: Request -> handleTXRequest(req) },
                { message, e -> logger.error("Failure processing data vending request.", e) }
        )
        addMessageHandler(FetchAttachmentsProtocol.TOPIC,
                { req: Request -> handleAttachmentRequest(req) },
                { message, e -> logger.error("Failure processing data vending request.", e) }
        )
    }

    class Request(val hashes: List<SecureHash>, replyTo: SingleMessageRecipient, sessionID: Long) : AbstractRequestMessage(replyTo, sessionID)

    private fun handleTXRequest(req: Request): List<SignedTransaction?> {
        require(req.hashes.isNotEmpty())
        return req.hashes.map {
            val tx = storage.validatedTransactions[it]
            if (tx == null)
                logger.info("Got request for unknown tx $it")
            tx
        }
    }

    private fun handleAttachmentRequest(req: Request): List<ByteArray?> {
        // TODO: Use Artemis message streaming support here, called "large messages". This avoids the need to buffer.
        require(req.hashes.isNotEmpty())
        return req.hashes.map {
            val jar: InputStream? = storage.attachments.openAttachment(it)?.open()
            if (jar == null) {
                logger.info("Got request for unknown attachment $it")
                null
            } else {
                jar.readBytes()
            }
        }
    }
}
