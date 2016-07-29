package com.r3corda.demos.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.demos.DemoClock
import com.r3corda.node.internal.Node
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.services.network.MockNetworkMapCache
import java.time.LocalDate

/**
 * This is a less temporary, demo-oriented way of initiating processing of temporal events.
 */
object UpdateBusinessDayProtocol {

    val TOPIC = "businessday.topic"

    data class UpdateBusinessDayMessage(val date: LocalDate)

    class Plugin: CordaPluginRegistry() {
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }

    class Service(services: ServiceHubInternal) {

        init {
            services.networkService.addMessageHandler(TOPIC, DEFAULT_SESSION_ID) { msg, registration ->
                val updateBusinessDayMessage = msg.data.deserialize<UpdateBusinessDayMessage>()
                (services.clock as DemoClock).updateDate(updateBusinessDayMessage.date)
            }
        }
    }

    class Broadcast(val date: LocalDate,
                    override val progressTracker: ProgressTracker = Broadcast.tracker()) : ProtocolLogic<Unit>() {

        companion object {
            object NOTIFYING : ProgressTracker.Step("Notifying peers")

            fun tracker() = ProgressTracker(NOTIFYING)
        }

        override val topic: String get() = TOPIC

        @Suspendable
        override fun call(): Unit {
            progressTracker.currentStep = NOTIFYING
            val message = UpdateBusinessDayMessage(date)
            for (recipient in serviceHub.networkMapCache.partyNodes) {
                doNextRecipient(recipient, message)
            }
        }

        @Suspendable
        private fun doNextRecipient(recipient: NodeInfo, message: UpdateBusinessDayMessage) {
            if (recipient.address is MockNetworkMapCache.MockAddress) {
                // Ignore
            } else {
                send(recipient.identity, 0, message)
            }
        }
    }

}
