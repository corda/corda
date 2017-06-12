package net.corda.nodeapi

import com.google.common.net.HostAndPort
import net.corda.nodeapi.config.SSLConfiguration
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants
import org.bouncycastle.asn1.x500.X500Name
import java.nio.file.FileSystems
import java.nio.file.Path

sealed class ConnectionDirection {
    data class Inbound(val acceptorFactoryClassName: String) : ConnectionDirection()
    data class Outbound(
            val expectedCommonName: X500Name? = null,
            val connectorFactoryClassName: String = NettyConnectorFactory::class.java.name
    ) : ConnectionDirection()
}

class ArtemisTcpTransport {
    companion object {
        const val VERIFY_PEER_LEGAL_NAME = "corda.verifyPeerCommonName"

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

        fun tcpTransport(
                direction: ConnectionDirection,
                hostAndPort: HostAndPort,
                config: SSLConfiguration?,
                enableSSL: Boolean = true
        ): TransportConfiguration {
            val options = mutableMapOf<String, Any?>(
                    // Basic TCP target details
                    TransportConstants.HOST_PROP_NAME to hostAndPort.host,
                    TransportConstants.PORT_PROP_NAME to hostAndPort.port,

                    // Turn on AMQP support, which needs the protocol jar on the classpath.
                    // Unfortunately we cannot disable core protocol as artemis only uses AMQP for interop
                    // It does not use AMQP messages for its own messages e.g. topology and heartbeats
                    // TODO further investigate how to ensure we use a well defined wire level protocol for Node to Node communications
                    TransportConstants.PROTOCOLS_PROP_NAME to "CORE,AMQP"
            )

            if (config != null && enableSSL) {
                config.sslKeystore.expectedOnDefaultFileSystem()
                config.trustStoreFile.expectedOnDefaultFileSystem()
                val tlsOptions = mapOf(
                        // Enable TLS transport layer with client certs and restrict to at least SHA256 in handshake
                        // and AES encryption
                        TransportConstants.SSL_ENABLED_PROP_NAME to true,
                        TransportConstants.KEYSTORE_PROVIDER_PROP_NAME to "JKS",
                        TransportConstants.KEYSTORE_PATH_PROP_NAME to config.sslKeystore,
                        TransportConstants.KEYSTORE_PASSWORD_PROP_NAME to config.keyStorePassword, // TODO proper management of keystores and password
                        TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME to "JKS",
                        TransportConstants.TRUSTSTORE_PATH_PROP_NAME to config.trustStoreFile,
                        TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME to config.trustStorePassword,
                        TransportConstants.ENABLED_CIPHER_SUITES_PROP_NAME to CIPHER_SUITES.joinToString(","),
                        TransportConstants.ENABLED_PROTOCOLS_PROP_NAME to "TLSv1.2",
                        TransportConstants.NEED_CLIENT_AUTH_PROP_NAME to true,
                        VERIFY_PEER_LEGAL_NAME to (direction as? ConnectionDirection.Outbound)?.expectedCommonName
                )
                options.putAll(tlsOptions)
            }
            val factoryName = when (direction) {
                is ConnectionDirection.Inbound -> direction.acceptorFactoryClassName
                is ConnectionDirection.Outbound -> direction.connectorFactoryClassName
            }
            return TransportConfiguration(factoryName, options)
        }
    }
}

fun Path.expectedOnDefaultFileSystem() {
    require(fileSystem == FileSystems.getDefault()) { "Artemis only uses the default file system" }
}
