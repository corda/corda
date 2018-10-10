package net.corda.nodeapi.internal

import net.corda.core.crypto.toStringShort
import net.corda.core.identity.Party
import net.corda.core.messaging.MessageRecipientGroup
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.serialization.CordaSerializable
import org.apache.activemq.artemis.api.core.Message
import org.apache.activemq.artemis.api.core.SimpleString
import java.security.PublicKey

/**
 * The base class for Artemis services that defines shared data structures and SSL transport configuration.
 */
class ArtemisMessagingComponent {
    companion object {
        init {
            System.setProperty("org.jboss.logging.provider", "slf4j")
        }

        // System users must contain an invalid RPC username character to prevent any chance of name clash which in this
        // case is a forward slash
        const val NODE_P2P_USER = "SystemUsers/Node"
        const val NODE_RPC_USER = "SystemUsers/NodeRPC"
        const val PEER_USER = "SystemUsers/Peer"
        // User used only in devMode when nodes have a shell attached by default.
        const val INTERNAL_SHELL_USER = "internalShell"

        const val INTERNAL_PREFIX = "internal."
        const val PEERS_PREFIX = "${INTERNAL_PREFIX}peers." //TODO Come up with better name for common peers/services queue
        const val P2P_PREFIX = "p2p.inbound."
        const val BRIDGE_CONTROL = "${INTERNAL_PREFIX}bridge.control"
        const val BRIDGE_NOTIFY = "${INTERNAL_PREFIX}bridge.notify"
        const val NOTIFICATIONS_ADDRESS = "${INTERNAL_PREFIX}activemq.notifications"
        // This is a rough guess on the extra space needed on top of maxMessageSize to store the journal.
        // TODO: we might want to make this value configurable.
        const val JOURNAL_HEADER_SIZE = 1024
        object P2PMessagingHeaders {
            // This is a "property" attached to an Artemis MQ message object, which contains our own notion of "topic".
            // We should probably try to unify our notion of "topic" (really, just a string that identifies an endpoint
            // that will handle messages, like a URL) with the terminology used by underlying MQ libraries, to avoid
            // confusion.
            val topicProperty = SimpleString("platform-topic")
            val cordaVendorProperty = SimpleString("corda-vendor")
            val releaseVersionProperty = SimpleString("release-version")
            val platformVersionProperty = SimpleString("platform-version")
            val senderUUID = SimpleString("sender-uuid")
            val senderSeqNo = SimpleString("send-seq-no")
            /**
             * In the operation mode where we have an out of process bridge we cannot correctly populate the Artemis validated user header
             * as the TLS does not terminate directly onto Artemis. We therefore use this internal only header to forward
             * the equivalent information from the Float.
             */
            val bridgedCertificateSubject = SimpleString("sender-subject-name")

            object Type {
                const val KEY = "corda_p2p_message_type"
                const val SESSION_INIT_VALUE = "session_init"
            }

            val whitelistedHeaders: Set<String> = setOf(topicProperty.toString(),
                    cordaVendorProperty.toString(),
                    releaseVersionProperty.toString(),
                    platformVersionProperty.toString(),
                    senderUUID.toString(),
                    senderSeqNo.toString(),
                    bridgedCertificateSubject.toString(),
                    Type.KEY,
                    Message.HDR_DUPLICATE_DETECTION_ID.toString(),
                    Message.HDR_VALIDATED_USER.toString())
        }
    }

    interface ArtemisAddress : MessageRecipients {
        val queueName: String
    }

    interface ArtemisPeerAddress : ArtemisAddress, SingleMessageRecipient

    /**
     * This is the class used to implement [SingleMessageRecipient], for now. Note that in future this class
     * may change or evolve and code that relies upon it being a simple host/port may not function correctly.
     * For instance it may contain onion routing data.
     *
     * [NodeAddress] identifies a specific peer node and an associated queue. The queue may be the peer's own queue or
     *     an advertised service's queue.
     *
     * @param queueName The name of the queue this address is associated with.
     */
    @CordaSerializable
    data class NodeAddress(override val queueName: String) : ArtemisPeerAddress {
        constructor(peerIdentity: PublicKey) :
                this("$PEERS_PREFIX${peerIdentity.toStringShort()}")
    }

    /**
     * [ServiceAddress] implements [MessageRecipientGroup]. It holds a queue associated with a service advertised by
     * zero or more nodes. Each advertising node has an associated consumer.
     *
     * By sending to such an address Artemis will pick a consumer (uses Round Robin by default) and sends the message
     * there. We use this to establish sessions involving service counterparties.
     *
     * @param identity The service identity's owning key.
     */
    data class ServiceAddress(val identity: PublicKey) : ArtemisAddress, MessageRecipientGroup {
        override val queueName: String = "$PEERS_PREFIX${identity.toStringShort()}"
    }

    /**
     * [RemoteInboxAddress] implements [SingleMessageRecipient]. It represents the non-local address of a remote inbox.
     * @param identity The Node public identity
     */
    data class RemoteInboxAddress(val identity: PublicKey) : ArtemisAddress, SingleMessageRecipient {
        constructor(party: Party) : this(party.owningKey)

        companion object {
            /**
             * When transferring a message from the local holding queue to the remote inbox queue
             * this method provides a simple translation of the address string.
             * The topics are distinct so that proper segregation of internal
             * and external access permissions can be made.
             */
            fun translateLocalQueueToInboxAddress(address: String): String {
                require(address.startsWith(PEERS_PREFIX)) { "Failed to map address: $address to a remote topic as it is not in the $PEERS_PREFIX namespace" }
                return P2P_PREFIX + address.substring(PEERS_PREFIX.length)
            }
        }

        override val queueName: String = "$P2P_PREFIX${identity.toStringShort()}"
    }
}
