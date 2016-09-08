package com.r3corda.node.services.messaging

import com.google.common.annotations.VisibleForTesting
import com.google.common.net.HostAndPort
import com.r3corda.core.crypto.X509Utilities
import com.r3corda.core.crypto.parsePublicKeyBase58
import com.r3corda.core.crypto.toBase58String
import com.r3corda.core.messaging.MessageRecipients
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.node.services.config.NodeConfiguration
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants
import java.nio.file.Files
import java.nio.file.Path
import java.security.PublicKey

/**
 * The base class for Artemis services that defines shared data structures and transport configuration
 *
 * @param directory A place where Artemis can stash its message journal and other files.
 * @param config The config object is used to pass in the passwords for the certificate KeyStore and TrustStore
 */
abstract class ArtemisMessagingComponent(val directory: Path, val config: NodeConfiguration) : SingletonSerializeAsToken() {
    private val keyStorePath = directory.resolve("certificates").resolve("sslkeystore.jks")
    private val trustStorePath = directory.resolve("certificates").resolve("truststore.jks")


    companion object {
        const val PEERS_PREFIX = "peers."

        @JvmStatic
        protected val NETWORK_MAP_ADDRESS = SimpleString(PEERS_PREFIX +"networkmap")

        /**
         * Assuming the passed in target address is actually an ArtemisAddress will extract the host and port of the node. This should
         * only be used in unit tests and the internals of the messaging services to keep addressing opaque for the future.
         * N.B. Marked as JvmStatic to allow use in the inherited classes.
         */
        @JvmStatic
        @VisibleForTesting
        fun toHostAndPort(target: MessageRecipients): HostAndPort {
            val addr = target as? ArtemisMessagingComponent.ArtemisAddress ?: throw IllegalArgumentException("Not an Artemis address")
            return addr.hostAndPort
        }

        /**
         * Assuming the passed in target address is actually an ArtemisAddress will extract the queue name used.
         * For now the queue name is the Base58 version of the node's identity.
         * This should only be used in the internals of the messaging services to keep addressing opaque for the future.
         * N.B. Marked as JvmStatic to allow use in the inherited classes.
         */
        @JvmStatic
        protected fun toQueueName(target: MessageRecipients): SimpleString {
            val addr = target as? ArtemisMessagingComponent.ArtemisAddress ?: throw IllegalArgumentException("Not an Artemis address")
            return addr.queueName

        }
    }

    protected interface ArtemisAddress {
        val queueName: SimpleString
        val hostAndPort: HostAndPort
    }

    protected data class NetworkMapAddress(override val hostAndPort: HostAndPort) : SingleMessageRecipient, ArtemisAddress {
        override val queueName: SimpleString = NETWORK_MAP_ADDRESS
    }

    // In future: can contain onion routing info, etc.
    protected data class NodeAddress(val identity: PublicKey, override val hostAndPort: HostAndPort) : SingleMessageRecipient, ArtemisAddress {
        override val queueName: SimpleString by lazy { SimpleString(PEERS_PREFIX+identity.toBase58String()) }

        override fun toString(): String {
            return "NodeAddress(identity = $queueName, $hostAndPort"
        }
    }

    protected fun tryParseKeyFromQueueName(queueName: SimpleString): PublicKey? {
        val name = queueName.toString()
        if(!name.startsWith(PEERS_PREFIX)) {
            return null
        }
        val keyCode = name.substring(PEERS_PREFIX.length)
        return try {
            parsePublicKeyBase58(keyCode)
        } catch (ex: Exception) {
            null
        }

    }

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
            "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256")

    protected fun tcpTransport(direction: ConnectionDirection, host: String, port: Int) =
            TransportConfiguration(
                    when (direction) {
                        ConnectionDirection.INBOUND -> NettyAcceptorFactory::class.java.name
                        ConnectionDirection.OUTBOUND -> NettyConnectorFactory::class.java.name
                    },
                    mapOf(
                            // Basic TCP target details
                            TransportConstants.HOST_PROP_NAME to host,
                            TransportConstants.PORT_PROP_NAME to port.toInt(),

                            // Turn on AMQP support, which needs the protocol jar on the classpath.
                            // Unfortunately we cannot disable core protocol as artemis only uses AMQP for interop
                            // It does not use AMQP messages for its own messages e.g. topology and heartbeats
                            // TODO further investigate how to ensure we use a well defined wire level protocol for Node to Node communications
                            TransportConstants.PROTOCOLS_PROP_NAME to "CORE,AMQP",

                            // Enable TLS transport layer with client certs and restrict to at least SHA256 in handshake
                            // and AES encryption
                            TransportConstants.SSL_ENABLED_PROP_NAME to true,
                            TransportConstants.KEYSTORE_PROVIDER_PROP_NAME to "JKS",
                            TransportConstants.KEYSTORE_PATH_PROP_NAME to keyStorePath,
                            TransportConstants.KEYSTORE_PASSWORD_PROP_NAME to config.keyStorePassword, // TODO proper management of keystores and password
                            TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME to "JKS",
                            TransportConstants.TRUSTSTORE_PATH_PROP_NAME to trustStorePath,
                            TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME to config.trustStorePassword,
                            TransportConstants.ENABLED_CIPHER_SUITES_PROP_NAME to CIPHER_SUITES.joinToString(","),
                            TransportConstants.ENABLED_PROTOCOLS_PROP_NAME to "TLSv1.2",
                            TransportConstants.NEED_CLIENT_AUTH_PROP_NAME to true
                    )
            )

    /**
     * Strictly for dev only automatically construct a server certificate/private key signed from
     * the CA certs in Node resources. Then provision KeyStores into certificates folder under node path.
     */
    fun configureWithDevSSLCertificate() {

        val keyStorePath = directory.resolve("certificates").resolve("sslkeystore.jks")
        val trustStorePath = directory.resolve("certificates").resolve("truststore.jks")

        Files.createDirectories(directory.resolve("certificates"))
        if (!Files.exists(trustStorePath)) {
            Files.copy(javaClass.classLoader.getResourceAsStream("com/r3corda/node/internal/certificates/cordatruststore.jks"),
                    trustStorePath)
        }
        if (!Files.exists(keyStorePath)) {
            val caKeyStore = X509Utilities.loadKeyStore(
                    javaClass.classLoader.getResourceAsStream("com/r3corda/node/internal/certificates/cordadevcakeys.jks"),
                    "cordacadevpass")
            X509Utilities.createKeystoreForSSL(keyStorePath, config.keyStorePassword, config.keyStorePassword, caKeyStore, "cordacadevkeypass")
        }
    }
}
