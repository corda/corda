package com.r3corda.demos.protocols

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.testing.node.MockNetworkMapCache
import java.util.concurrent.TimeUnit

object ExitServerProtocol {

    // Will only be enabled if you install the Handler
    @Volatile private var enabled = false

    // This is not really a HandshakeMessage but needs to be so that the send uses the default session ID. This will
    // resolve itself when the protocol session stuff is done.
    data class ExitMessage(val exitCode: Int)

    class Plugin: CordaPluginRegistry() {
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }

    class Service(services: ServiceHubInternal) {
        init {
            services.registerProtocolInitiator(Broadcast::class, ::ExitServerHandler)
            enabled = true
        }
    }


    private class ExitServerHandler(val otherParty: Party) : ProtocolLogic<Unit>() {
        override fun call() {
            // Just to validate we got the message
            if (enabled) {
                val message = receive<ExitMessage>(otherParty).unwrap { it }
                System.exit(message.exitCode)
            }
        }
    }


    /**
     * This takes a Java Integer rather than Kotlin Int as that is what we end up with in the calling map and currently
     * we do not support coercing numeric types in the reflective search for matching constructors.
     */
    class Broadcast(val exitCode: Int) : ProtocolLogic<Boolean>() {

        @Suspendable
        override fun call(): Boolean {
            if (enabled) {
                for (recipient in serviceHub.networkMapCache.partyNodes) {
                    doNextRecipient(recipient)
                }
                // Sleep a little in case any async message delivery to other nodes needs to happen
                Strand.sleep(1, TimeUnit.SECONDS)
                System.exit(exitCode)
            }
            return enabled
        }

        @Suspendable
        private fun doNextRecipient(recipient: NodeInfo) {
            if (recipient.address is MockNetworkMapCache.MockAddress) {
                // Ignore
            } else {
                send(recipient.legalIdentity, ExitMessage(exitCode))
            }
        }
    }

}
