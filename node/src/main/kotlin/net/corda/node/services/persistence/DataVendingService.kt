package net.corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.PluginServiceHub
import net.corda.core.node.recordTransactions
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import net.corda.flows.*
import java.util.function.Function
import javax.annotation.concurrent.ThreadSafe

object DataVending {
    class Plugin : CordaPluginRegistry() {
        override val servicePlugins = listOf(Function(::Service))
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
    class Service(services: PluginServiceHub) : SingletonSerializeAsToken() {
        init {
            services.registerFlowInitiator(FetchTransactionsFlow::class, ::FetchTransactionsHandler)
            services.registerFlowInitiator(FetchAttachmentsFlow::class, ::FetchAttachmentsHandler)
            services.registerFlowInitiator(BroadcastTransactionFlow::class, ::NotifyTransactionHandler)
        }

        private class FetchTransactionsHandler(otherParty: Party) : FetchDataHandler<SignedTransaction>(otherParty) {
            override fun getData(id: SecureHash): SignedTransaction? {
                return serviceHub.storageService.validatedTransactions.getTransaction(id)
            }
        }

        // TODO: Use Artemis message streaming support here, called "large messages". This avoids the need to buffer.
        private class FetchAttachmentsHandler(otherParty: Party) : FetchDataHandler<ByteArray>(otherParty) {
            override fun getData(id: SecureHash): ByteArray? {
                return serviceHub.storageService.attachments.openAttachment(id)?.open()?.readBytes()
            }
        }

        private abstract class FetchDataHandler<out T>(val otherParty: Party) : FlowLogic<Unit>() {
            @Suspendable
            @Throws(FetchDataFlow.HashNotFound::class)
            override fun call() {
                val request = receive<FetchDataFlow.Request>(otherParty).unwrap {
                    if (it.hashes.isEmpty()) throw FlowException("Empty hash list")
                    it
                }
                val response = request.hashes.map {
                    getData(it) ?: throw FetchDataFlow.HashNotFound(it)
                }
                send(otherParty, response)
            }

            protected abstract fun getData(id: SecureHash): T?
        }


        // TODO: We should have a whitelist of contracts we're willing to accept at all, and reject if the transaction
        //       includes us in any outside that list. Potentially just if it includes any outside that list at all.
        // TODO: Do we want to be able to reject specific transactions on more complex rules, for example reject incoming
        //       cash without from unknown parties?
        class NotifyTransactionHandler(val otherParty: Party) : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                val request = receive<BroadcastTransactionFlow.NotifyTxRequest>(otherParty).unwrap { it }
                subFlow(ResolveTransactionsFlow(request.tx, otherParty), shareParentSessions = true)
                serviceHub.recordTransactions(request.tx)
            }
        }
    }

}
