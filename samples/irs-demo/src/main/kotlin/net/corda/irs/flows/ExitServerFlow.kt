package net.corda.irs.flows

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.NodeInfo
import net.corda.core.node.PluginServiceHub
import net.corda.core.utilities.unwrap
import net.corda.testing.node.MockNetworkMapCache
import java.util.concurrent.TimeUnit
import java.util.function.Function

object ExitServerFlow {

    // Will only be enabled if you install the Handler
    @Volatile private var enabled = false

    // This is not really a HandshakeMessage but needs to be so that the send uses the default session ID. This will
    // resolve itself when the flow session stuff is done.
    data class ExitMessage(val exitCode: Int)

    class Plugin : CordaPluginRegistry() {
        override val servicePlugins = listOf(Function(::Service))
    }

    class Service(services: PluginServiceHub) {
        init {
            services.registerFlowInitiator(Broadcast::class, ::ExitServerHandler)
            enabled = true
        }
    }


    private class ExitServerHandler(val otherParty: Party) : FlowLogic<Unit>() {
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
    class Broadcast(val exitCode: Int) : FlowLogic<Boolean>() {

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
