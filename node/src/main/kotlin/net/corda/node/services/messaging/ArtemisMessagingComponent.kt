package net.corda.node.services.messaging

import com.google.common.annotations.VisibleForTesting
import com.google.common.net.HostAndPort
import net.corda.core.crypto.CompositeKey
import net.corda.core.messaging.MessageRecipientGroup
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.read
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.config.NodeSSLConfiguration
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants
import java.nio.file.FileSystems
import java.nio.file.Path
import java.security.KeyStore

/**
 * The base class for Artemis services that defines shared data structures and transport configuration
 */
abstract class ArtemisMessagingComponent() : SingletonSerializeAsToken() {

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
        const val CLIENTS_PREFIX = "clients."
        const val P2P_QUEUE = "p2p.inbound"
        const val RPC_REQUESTS_QUEUE = "rpc.requests"
        const val RPC_QUEUE_REMOVALS_QUEUE = "rpc.qremovals"
        const val NOTIFICATIONS_ADDRESS = "${INTERNAL_PREFIX}activemq.notifications"

        @JvmStatic
        val NETWORK_MAP_ADDRESS = "${INTERNAL_PREFIX}networkmap"

        /**
         * Assuming the passed in target address is actually an ArtemisAddress will extract the host and port of the node. This should
         * only be used in unit tests and the internals of the messaging services to keep addressing opaque for the future.
         * N.B. Marked as JvmStatic to allow use in the inherited classes.
         */
        @JvmStatic
        @VisibleForTesting
        fun toHostAndPort(target: MessageRecipients): HostAndPort {
            val addr = target as? ArtemisMessagingComponent.ArtemisPeerAddress ?: throw IllegalArgumentException("Not an Artemis address")
            return addr.hostAndPort
        }
    }

    interface ArtemisAddress : MessageRecipients {
        val queueName: SimpleString
    }

    interface ArtemisPeerAddress : ArtemisAddress, SingleMessageRecipient {
        val hostAndPort: HostAndPort
    }

    data class NetworkMapAddress(override val hostAndPort: HostAndPort) : SingleMessageRecipient, ArtemisPeerAddress {
        override val queueName = SimpleString(NETWORK_MAP_ADDRESS)
    }

    /**
     * This is the class used to implement [SingleMessageRecipient], for now. Note that in future this class
     * may change or evolve and code that relies upon it being a simple host/port may not function correctly.
     * For instance it may contain onion routing data.
     *
     * [NodeAddress] identifies a specific peer node and an associated queue. The queue may be the peer's p2p queue or
     *     an advertised service's queue.
     *
     * @param queueName The name of the queue this address is associated with.
     * @param hostAndPort The address of the node.
     */
    data class NodeAddress(override val queueName: SimpleString, override val hostAndPort: HostAndPort) : ArtemisPeerAddress {
        companion object {
            fun asPeer(peerIdentity: CompositeKey, hostAndPort: HostAndPort): NodeAddress {
                return NodeAddress(SimpleString("$PEERS_PREFIX${peerIdentity.toBase58String()}"), hostAndPort)
            }
            fun asService(serviceIdentity: CompositeKey, hostAndPort: HostAndPort): NodeAddress {
                return NodeAddress(SimpleString("$SERVICES_PREFIX${serviceIdentity.toBase58String()}"), hostAndPort)
            }
        }
        override fun toString(): String = "${javaClass.simpleName}(queue = $queueName, $hostAndPort)"
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
    data class ServiceAddress(val identity: CompositeKey) : ArtemisAddress, MessageRecipientGroup {
        override val queueName: SimpleString = SimpleString("$SERVICES_PREFIX${identity.toBase58String()}")
    }

    /** The config object is used to pass in the passwords for the certificate KeyStore and TrustStore */
    abstract val config: NodeSSLConfiguration

    protected enum class ConnectionDirection { INBOUND, OUTBOUND }

    // Restrict enabled Cipher Suites to AES and GCM as minimum for the bulk cipher.
    // Our self-generated certificates all use ECDSA for handshakes, but we allow classical RSA certificates to work
    // in case we need to use keytool certificates in some demos
    private val CIPHER_SUITES = listOf(
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256"
    )

    /**
     * Returns nothing if the keystore was opened OK or throws if not. Useful to check the password, as
     * unfortunately Artemis tends to bury the exception when the password is wrong.
     */
    fun checkStorePasswords() {
        config.keyStorePath.read {
            KeyStore.getInstance("JKS").load(it, config.keyStorePassword.toCharArray())
        }
        config.trustStorePath.read {
            KeyStore.getInstance("JKS").load(it, config.trustStorePassword.toCharArray())
        }
    }

    protected fun tcpTransport(direction: ConnectionDirection, host: String, port: Int): TransportConfiguration {
        config.keyStorePath.expectedOnDefaultFileSystem()
        config.trustStorePath.expectedOnDefaultFileSystem()
        return TransportConfiguration(
                when (direction) {
                    ConnectionDirection.INBOUND -> NettyAcceptorFactory::class.java.name
                    ConnectionDirection.OUTBOUND -> NettyConnectorFactory::class.java.name
                },
                mapOf(
                        // Basic TCP target details
                        TransportConstants.HOST_PROP_NAME to host,
                        TransportConstants.PORT_PROP_NAME to port,

                        // Turn on AMQP support, which needs the protocol jar on the classpath.
                        // Unfortunately we cannot disable core protocol as artemis only uses AMQP for interop
                        // It does not use AMQP messages for its own messages e.g. topology and heartbeats
                        // TODO further investigate how to ensure we use a well defined wire level protocol for Node to Node communications
                        TransportConstants.PROTOCOLS_PROP_NAME to "CORE,AMQP",

                        // Enable TLS transport layer with client certs and restrict to at least SHA256 in handshake
                        // and AES encryption
                        TransportConstants.SSL_ENABLED_PROP_NAME to true,
                        TransportConstants.KEYSTORE_PROVIDER_PROP_NAME to "JKS",
                        TransportConstants.KEYSTORE_PATH_PROP_NAME to config.keyStorePath,
                        TransportConstants.KEYSTORE_PASSWORD_PROP_NAME to config.keyStorePassword, // TODO proper management of keystores and password
                        TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME to "JKS",
                        TransportConstants.TRUSTSTORE_PATH_PROP_NAME to config.trustStorePath,
                        TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME to config.trustStorePassword,
                        TransportConstants.ENABLED_CIPHER_SUITES_PROP_NAME to CIPHER_SUITES.joinToString(","),
                        TransportConstants.ENABLED_PROTOCOLS_PROP_NAME to "TLSv1.2",
                        TransportConstants.NEED_CLIENT_AUTH_PROP_NAME to true

                        // TODO: Set up the connector's host name verifier logic to ensure we connect to the expected node even in case of MITM or BGP hijacks
                )
        )
    }

    protected fun Path.expectedOnDefaultFileSystem() {
        require(fileSystem == FileSystems.getDefault()) { "Artemis only uses the default file system" }
    }
}
