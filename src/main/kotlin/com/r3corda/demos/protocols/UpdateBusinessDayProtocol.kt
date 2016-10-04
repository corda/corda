package com.r3corda.demos.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.demos.DemoClock
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.testing.node.MockNetworkMapCache
import java.time.LocalDate

/**
 * This is a less temporary, demo-oriented way of initiating processing of temporal events.
 */
object UpdateBusinessDayProtocol {

    // This is not really a HandshakeMessage but needs to be so that the send uses the default session ID. This will
    // resolve itself when the protocol session stuff is done.
    data class UpdateBusinessDayMessage(val date: LocalDate)

    class Plugin: CordaPluginRegistry() {
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }

    class Service(services: ServiceHubInternal) {
        init {
            services.registerProtocolInitiator(Broadcast::class, ::UpdateBusinessDayHandler)
        }
    }

    private class UpdateBusinessDayHandler(val otherParty: Party) : ProtocolLogic<Unit>() {
        override fun call() {
            val message = receive<UpdateBusinessDayMessage>(otherParty).unwrap { it }
            (serviceHub.clock as DemoClock).updateDate(message.date)
        }
    }


    class Broadcast(val date: LocalDate,
                    override val progressTracker: ProgressTracker = Broadcast.tracker()) : ProtocolLogic<Unit>() {

        companion object {
            object NOTIFYING : ProgressTracker.Step("Notifying peers")

            fun tracker() = ProgressTracker(NOTIFYING)
        }

        @Suspendable
        override fun call(): Unit {
            progressTracker.currentStep = NOTIFYING
            for (recipient in serviceHub.networkMapCache.partyNodes) {
                doNextRecipient(recipient)
            }
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
