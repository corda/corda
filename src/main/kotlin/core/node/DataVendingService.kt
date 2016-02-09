/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import core.StorageService
import core.crypto.SecureHash
import core.messaging.Message
import core.messaging.MessagingService
import core.messaging.SingleMessageRecipient
import core.messaging.send
import core.serialization.deserialize
import core.utilities.loggerFor
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
class DataVendingService(private val net: MessagingService, private val storage: StorageService) {
    companion object {
        val TX_FETCH_TOPIC = "platform.fetch.tx"
        val CONTRACT_FETCH_TOPIC = "platform.fetch.contract"

        val logger = loggerFor<DataVendingService>()
    }

    init {
        net.addMessageHandler("$TX_FETCH_TOPIC.0") { msg, registration -> handleTXRequest(msg) }
        net.addMessageHandler("$CONTRACT_FETCH_TOPIC.0") { msg, registration -> handleContractRequest(msg) }
    }

    // TODO: Give all messages a respond-to address+session ID automatically.
    data class Request(val hashes: List<SecureHash>, val responseTo: SingleMessageRecipient, val sessionID: Long)

    private fun handleTXRequest(msg: Message) {
        val req = msg.data.deserialize<Request>()
        require(req.hashes.isNotEmpty())
        val answers = req.hashes.map {
            val tx = storage.validatedTransactions[it]
            if (tx == null)
                logger.info("Got request for unknown tx $it")
            tx
        }
        net.send("$TX_FETCH_TOPIC.${req.sessionID}", req.responseTo, answers)
    }

    private fun handleContractRequest(msg: Message) {
        TODO("PLT-12: Basic module/sandbox system for contracts")
    }
}
