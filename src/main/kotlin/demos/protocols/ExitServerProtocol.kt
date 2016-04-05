package demos.protocols

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import core.node.Node
import core.node.services.LegallyIdentifiableNode
import core.node.services.MockNetworkMapService
import core.protocols.ProtocolLogic
import core.serialization.deserialize
import java.util.concurrent.TimeUnit


object ExitServerProtocol {

    val TOPIC = "exit.topic"

    // Will only be enabled if you install the Handler
    @Volatile private var enabled = false

    data class ExitMessage(val exitCode: Int)

    object Handler {

        fun register(node: Node) {
            node.net.addMessageHandler("${TOPIC}.0") { msg, registration ->
                // Just to validate we got the message
                if(enabled) {
                    val message = msg.data.deserialize<ExitMessage>()
                    System.exit(message.exitCode)
                }
            }
            enabled = true
        }
    }

    /**
     * This takes a Java Integer rather than Kotlin Int as that is what we end up with in the calling map and currently
     * we do not support coercing numeric types in the reflective search for matching constructors
     */
    class Broadcast(val exitCode: Integer) : ProtocolLogic<Boolean>() {

        @Suspendable
        override fun call(): Boolean {
            if(enabled) {
                val rc = exitCode.toInt()
                val message = ExitMessage(rc)

                for (recipient in serviceHub.networkMapService.partyNodes) {
                    doNextRecipient(recipient, message)
                }
                // Sleep a little in case any async message delivery to other nodes needs to happen
                Strand.sleep(1, TimeUnit.SECONDS)
                System.exit(rc)
            }
            return enabled
        }

        @Suspendable
        private fun doNextRecipient(recipient: LegallyIdentifiableNode, message: ExitMessage) {
            if(recipient.address is MockNetworkMapService.MockAddress) {
                // Ignore
            } else {
                // TODO: messaging ourselves seems to trigger a bug for the time being and we continuously receive messages
                if (recipient.identity != serviceHub.storageService.myLegalIdentity) {
                    send(TOPIC, recipient.address, 0, message)
                }
            }
        }
    }

}
