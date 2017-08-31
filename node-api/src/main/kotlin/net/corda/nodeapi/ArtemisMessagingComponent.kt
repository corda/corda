package net.corda.nodeapi

import net.corda.core.utilities.toBase58String
import net.corda.core.messaging.MessageRecipientGroup
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.internal.read
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.config.SSLConfiguration
import java.security.KeyStore
import java.security.PublicKey

/**
 * The base class for Artemis services that defines shared data structures and SSL transport configuration.
 */
abstract class ArtemisMessagingComponent : SingletonSerializeAsToken() {
    companion object {
        init {
            System.setProperty("org.jboss.logging.provider", "slf4j")
        }

        // System users must contain an invalid RPC username character to prevent any chance of name clash which in this
        // case is a forward slash
        const val NODE_USER = "SystemUsers/Node"
        const val PEER_USER = "SystemUsers/Peer"

        const val INTERNAL_PREFIX = "internal."
        const val PEERS_PREFIX = "${INTERNAL_PREFIX}peers."
        const val SERVICES_PREFIX = "${INTERNAL_PREFIX}services."
        const val IP_REQUEST_PREFIX = "ip."
        const val P2P_QUEUE = "p2p.inbound"
        const val NOTIFICATIONS_ADDRESS = "${INTERNAL_PREFIX}activemq.notifications"
        const val NETWORK_MAP_QUEUE = "${INTERNAL_PREFIX}networkmap"
    }

    interface ArtemisAddress : MessageRecipients {
        val queueName: String
    }

    interface ArtemisPeerAddress : ArtemisAddress, SingleMessageRecipient {
        val hostAndPort: NetworkHostAndPort
    }

    @CordaSerializable
    data class NetworkMapAddress(override val hostAndPort: NetworkHostAndPort) : ArtemisPeerAddress {
        override val queueName: String get() = NETWORK_MAP_QUEUE
    }

    /**
     * This is the class used to implement [SingleMessageRecipient], for now. Note that in future this class
     * may change or evolve and code that relies upon it being a simple host/port may not function correctly.
     * For instance it may contain onion routing data.
     *
     * [NodeAddress] identifies a specific peer node and an associated queue. The queue may be the peer's own queue or
     *     an advertised service's queue.
     *
     * @param queueName The name of the queue this address is associated with.
     * @param hostAndPort The address of the node.
     */
    @CordaSerializable
    data class NodeAddress(override val queueName: String, override val hostAndPort: NetworkHostAndPort) : ArtemisPeerAddress {
        companion object {
            fun asPeer(peerIdentity: PublicKey, hostAndPort: NetworkHostAndPort): NodeAddress {
                return NodeAddress("$PEERS_PREFIX${peerIdentity.toBase58String()}", hostAndPort)
            }

            fun asService(serviceIdentity: PublicKey, hostAndPort: NetworkHostAndPort): NodeAddress {
                return NodeAddress("$SERVICES_PREFIX${serviceIdentity.toBase58String()}", hostAndPort)
            }
        }
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
        override val queueName: String = "$SERVICES_PREFIX${identity.toBase58String()}"
    }

    /** The config object is used to pass in the passwords for the certificate KeyStore and TrustStore */
    abstract val config: SSLConfiguration?

    /**
     * Returns nothing if the keystore was opened OK or throws if not. Useful to check the password, as
     * unfortunately Artemis tends to bury the exception when the password is wrong.
     */
    fun checkStorePasswords() {
        val config = config ?: return
        arrayOf(config.sslKeystore, config.nodeKeystore).forEach {
            it.read {
                KeyStore.getInstance("JKS").load(it, config.keyStorePassword.toCharArray())
            }
        }
        config.trustStoreFile.read {
            KeyStore.getInstance("JKS").load(it, config.trustStorePassword.toCharArray())
        }
    }

    fun getArtemisPeerAddress(nodeInfo: NodeInfo): ArtemisPeerAddress {
        return if (nodeInfo.advertisedServices.any { it.info.type == ServiceType.networkMap }) {
            NetworkMapAddress(nodeInfo.addresses.first())
        } else {
            NodeAddress.asPeer(nodeInfo.legalIdentity.owningKey, nodeInfo.addresses.first())
        }
    }
}
