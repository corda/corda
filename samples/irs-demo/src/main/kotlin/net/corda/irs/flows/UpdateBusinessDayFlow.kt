package net.corda.irs.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.NodeInfo
import net.corda.core.node.PluginServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.node.utilities.TestClock
import net.corda.testing.node.MockNetworkMapCache
import java.time.LocalDate
import java.util.function.Function

/**
 * This is a less temporary, demo-oriented way of initiating processing of temporal events.
 */
object UpdateBusinessDayFlow {

    // This is not really a HandshakeMessage but needs to be so that the send uses the default session ID. This will
    // resolve itself when the flow session stuff is done.
    @CordaSerializable
    data class UpdateBusinessDayMessage(val date: LocalDate)

    class Plugin : CordaPluginRegistry() {
        override val servicePlugins = listOf(Function(::Service))
    }

    class Service(services: PluginServiceHub) {
        init {
            services.registerServiceFlow(Broadcast::class.java, ::UpdateBusinessDayHandler)
        }
    }

    private class UpdateBusinessDayHandler(val otherParty: Party) : FlowLogic<Unit>() {
        override fun call() {
            val message = receive<UpdateBusinessDayMessage>(otherParty).unwrap { it }
            (serviceHub.clock as TestClock).updateDate(message.date)
        }
    }


    class Broadcast(val date: LocalDate, override val progressTracker: ProgressTracker) : FlowLogic<Unit>() {
        constructor(date: LocalDate) : this(date, tracker())

        companion object {
            object NOTIFYING : ProgressTracker.Step("Notifying peers")

            fun tracker() = ProgressTracker(NOTIFYING)
        }

        @Suspendable
        override fun call(): Unit {
            progressTracker.currentStep = NOTIFYING
            for (recipient in getRecipients()) {
                doNextRecipient(recipient)
            }
        }

        /**
         * Returns recipients ordered by legal name, with notary nodes taking priority over party nodes.
         * Ordering is required so that we avoid situations where on clock update a party starts a scheduled flow, but
         * the notary or counterparty still use the old clock, so the timestamp on the transaction does not validate.
         */
        private fun getRecipients(): Iterable<NodeInfo> {
            val notaryNodes = serviceHub.networkMapCache.notaryNodes
            val partyNodes = (serviceHub.networkMapCache.partyNodes - notaryNodes).sortedBy { it.legalIdentity.name.toString() }
            return notaryNodes + partyNodes
        }

        @Suspendable
        private fun doNextRecipient(recipient: NodeInfo) {
            if (recipient.address is MockNetworkMapCache.MockAddress) {
                // Ignore
            } else {
                send(recipient.legalIdentity, UpdateBusinessDayMessage(date))
            }
        }
    }

}
