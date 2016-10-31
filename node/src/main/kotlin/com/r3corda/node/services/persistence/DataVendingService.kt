package com.r3corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.core.node.PluginServiceHub
import com.r3corda.core.node.recordTransactions
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.protocols.*
import java.io.InputStream
import javax.annotation.concurrent.ThreadSafe

object DataVending {

    class Plugin : CordaPluginRegistry() {
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }

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
    // TODO:  I don't like that this needs ServiceHubInternal, but passing in a state machine breaks MockServices because
//        the state machine isn't set when this is constructed. [NodeSchedulerService] has the same problem, and both
//        should be fixed at the same time.
    class Service(services: PluginServiceHub) : SingletonSerializeAsToken() {

        companion object {
            val logger = loggerFor<DataVending.Service>()
        }

        class TransactionRejectedError(msg: String) : Exception(msg)

        init {
            services.registerProtocolInitiator(FetchTransactionsProtocol::class, ::FetchTransactionsHandler)
            services.registerProtocolInitiator(FetchAttachmentsProtocol::class, ::FetchAttachmentsHandler)
            services.registerProtocolInitiator(BroadcastTransactionProtocol::class, ::NotifyTransactionHandler)
        }


        private class FetchTransactionsHandler(val otherParty: Party) : ProtocolLogic<Unit>() {
            @Suspendable
            override fun call() {
                val request = receive<FetchDataProtocol.Request>(otherParty).unwrap {
                    require(it.hashes.isNotEmpty())
                    it
                }
                val txs = request.hashes.map {
                    val tx = serviceHub.storageService.validatedTransactions.getTransaction(it)
                    if (tx == null)
                        logger.info("Got request for unknown tx $it")
                    tx
                }
                send(otherParty, txs)
            }
        }


        // TODO: Use Artemis message streaming support here, called "large messages". This avoids the need to buffer.
        private class FetchAttachmentsHandler(val otherParty: Party) : ProtocolLogic<Unit>() {
            @Suspendable
            override fun call() {
                val request = receive<FetchDataProtocol.Request>(otherParty).unwrap {
                    require(it.hashes.isNotEmpty())
                    it
                }
                val attachments = request.hashes.map {
                    val jar: InputStream? = serviceHub.storageService.attachments.openAttachment(it)?.open()
                    if (jar == null) {
                        logger.info("Got request for unknown attachment $it")
                        null
                    } else {
                        jar.readBytes()
                    }
                }
                send(otherParty, attachments)
            }
        }


        // TODO: We should have a whitelist of contracts we're willing to accept at all, and reject if the transaction
        //       includes us in any outside that list. Potentially just if it includes any outside that list at all.
        // TODO: Do we want to be able to reject specific transactions on more complex rules, for example reject incoming
        //       cash without from unknown parties?
        class NotifyTransactionHandler(val otherParty: Party) : ProtocolLogic<Unit>() {
            @Suspendable
            override fun call() {
                val request = receive<BroadcastTransactionProtocol.NotifyTxRequest>(otherParty).unwrap { it }
                subProtocol(ResolveTransactionsProtocol(request.tx, otherParty), shareParentSessions = true)
                serviceHub.recordTransactions(request.tx)
            }
        }
    }

}
