@file:Suppress("LongParameterList")

package net.corda.nodeapi.internal

import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.nodeapi.internal.config.DEFAULT_SSL_HANDSHAKE_TIMEOUT
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.config.SslConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.trustManagerFactory
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants
import javax.net.ssl.TrustManagerFactory

@Suppress("LongParameterList")
class ArtemisTcpTransport {
    companion object {
        val CIPHER_SUITES = listOf(
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
        )

        val TLS_VERSIONS = listOf("TLSv1.2")

        const val SSL_HANDSHAKE_TIMEOUT_NAME = "Corda-SSLHandshakeTimeout"
        const val TRUST_MANAGER_FACTORY_NAME = "Corda-TrustManagerFactory"
        const val TRACE_NAME = "Corda-Trace"
        const val THREAD_POOL_NAME_NAME = "Corda-ThreadPoolName"

        // Turn on AMQP support, which needs the protocol jar on the classpath.
        // Unfortunately we cannot disable core protocol as artemis only uses AMQP for interop.
        // It does not use AMQP messages for its own messages e.g. topology and heartbeats.
        private const val P2P_PROTOCOLS = "CORE,AMQP"
        private const val RPC_PROTOCOLS = "CORE"

        private fun defaultArtemisOptions(hostAndPort: NetworkHostAndPort, protocols: String) = mapOf(
                // Basic TCP target details.
                TransportConstants.HOST_PROP_NAME to hostAndPort.host,
                TransportConstants.PORT_PROP_NAME to hostAndPort.port,
                TransportConstants.PROTOCOLS_PROP_NAME to protocols,
                TransportConstants.USE_GLOBAL_WORKER_POOL_PROP_NAME to (nodeSerializationEnv != null),
                // turn off direct delivery in Artemis - this is latency optimisation that can lead to
                //hick-ups under high load (CORDA-1336)
                TransportConstants.DIRECT_DELIVER to false)

        private fun SslConfiguration.addToTransportOptions(options: MutableMap<String, Any>) {
            if (keyStore != null || trustStore != null) {
                options[TransportConstants.SSL_ENABLED_PROP_NAME] = true
                options[TransportConstants.NEED_CLIENT_AUTH_PROP_NAME] = true
            }
            keyStore?.let {
                with (it) {
                    path.requireOnDefaultFileSystem()
                    options[TransportConstants.KEYSTORE_PROVIDER_PROP_NAME] = "JKS"
                    options[TransportConstants.KEYSTORE_PATH_PROP_NAME] = path
                    options[TransportConstants.KEYSTORE_PASSWORD_PROP_NAME] = get().password
                }
            }
            trustStore?.let {
                with (it) {
                    path.requireOnDefaultFileSystem()
                    options[TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME] = "JKS"
                    options[TransportConstants.TRUSTSTORE_PATH_PROP_NAME] = path
                    options[TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME] = get().password
                }
            }
            options[TransportConstants.SSL_PROVIDER] = if (useOpenSsl) TransportConstants.OPENSSL_PROVIDER else TransportConstants.DEFAULT_SSL_PROVIDER
            options[SSL_HANDSHAKE_TIMEOUT_NAME] = handshakeTimeout ?: DEFAULT_SSL_HANDSHAKE_TIMEOUT
        }

        private fun ClientRpcSslOptions.toTransportOptions() = mapOf(
                TransportConstants.SSL_ENABLED_PROP_NAME to true,
                TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME to trustStoreProvider,
                TransportConstants.TRUSTSTORE_PATH_PROP_NAME to trustStorePath,
                TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME to trustStorePassword)

        private fun BrokerRpcSslOptions.toTransportOptions() = mapOf(
                TransportConstants.SSL_ENABLED_PROP_NAME to true,
                TransportConstants.KEYSTORE_PROVIDER_PROP_NAME to "JKS",
                TransportConstants.KEYSTORE_PATH_PROP_NAME to keyStorePath,
                TransportConstants.KEYSTORE_PASSWORD_PROP_NAME to keyStorePassword,
                TransportConstants.NEED_CLIENT_AUTH_PROP_NAME to false)

        fun p2pAcceptorTcpTransport(hostAndPort: NetworkHostAndPort,
                                    config: MutualSslConfiguration?,
                                    trustManagerFactory: TrustManagerFactory?,
                                    enableSSL: Boolean = true,
                                    threadPoolName: String = "P2PServer",
                                    trace: Boolean = false,
                                    remotingThreads: Int? = null): TransportConfiguration {
            val options = mutableMapOf<String, Any>()
            if (enableSSL) {
                config?.addToTransportOptions(options)
            }
            return createAcceptorTransport(
                    hostAndPort,
                    P2P_PROTOCOLS,
                    options,
                    trustManagerFactory,
                    enableSSL,
                    threadPoolName,
                    trace,
                    remotingThreads
            )
        }

        fun p2pConnectorTcpTransport(hostAndPort: NetworkHostAndPort,
                                     config: MutualSslConfiguration?,
                                     enableSSL: Boolean = true,
                                     threadPoolName: String = "P2PClient",
                                     trace: Boolean = false,
                                     remotingThreads: Int? = null): TransportConfiguration {
            val options = mutableMapOf<String, Any>()
            if (enableSSL) {
                config?.addToTransportOptions(options)
            }
            return createConnectorTransport(hostAndPort, P2P_PROTOCOLS, options, enableSSL, threadPoolName, trace, remotingThreads)
        }

        fun rpcAcceptorTcpTransport(hostAndPort: NetworkHostAndPort,
                                    config: BrokerRpcSslOptions?,
                                    enableSSL: Boolean = true,
                                    trace: Boolean = false,
                                    remotingThreads: Int? = null): TransportConfiguration {
            val options = mutableMapOf<String, Any>()
            if (config != null && enableSSL) {
                config.keyStorePath.requireOnDefaultFileSystem()
                options.putAll(config.toTransportOptions())
            }
            return createAcceptorTransport(hostAndPort, RPC_PROTOCOLS, options, null, enableSSL, "RPCServer", trace, remotingThreads)
        }

        fun rpcConnectorTcpTransport(hostAndPort: NetworkHostAndPort,
                                     config: ClientRpcSslOptions?,
                                     enableSSL: Boolean = true,
                                     trace: Boolean = false,
                                     remotingThreads: Int? = null): TransportConfiguration {
            val options = mutableMapOf<String, Any>()
            if (config != null && enableSSL) {
                config.trustStorePath.requireOnDefaultFileSystem()
                options.putAll(config.toTransportOptions())
            }
            return createConnectorTransport(hostAndPort, RPC_PROTOCOLS, options, enableSSL, "RPCClient", trace, remotingThreads)
        }

        fun rpcInternalClientTcpTransport(hostAndPort: NetworkHostAndPort,
                                          config: SslConfiguration,
                                          trace: Boolean = false): TransportConfiguration {
            val options = mutableMapOf<String, Any>()
            config.addToTransportOptions(options)
            return createConnectorTransport(hostAndPort, RPC_PROTOCOLS, options, true, "Internal-RPCClient", trace, null)
        }

        fun rpcInternalAcceptorTcpTransport(hostAndPort: NetworkHostAndPort,
                                            config: SslConfiguration,
                                            trace: Boolean = false,
                                            remotingThreads: Int? = null): TransportConfiguration {
            val options = mutableMapOf<String, Any>()
            config.addToTransportOptions(options)
            return createAcceptorTransport(
                    hostAndPort,
                    RPC_PROTOCOLS,
                    options,
                    trustManagerFactory(requireNotNull(config.trustStore).get()),
                    true,
                    "Internal-RPCServer",
                    trace,
                    remotingThreads
            )
        }

        private fun createAcceptorTransport(hostAndPort: NetworkHostAndPort,
                                            protocols: String,
                                            options: MutableMap<String, Any>,
                                            trustManagerFactory: TrustManagerFactory?,
                                            enableSSL: Boolean,
                                            threadPoolName: String,
                                            trace: Boolean,
                                            remotingThreads: Int?): TransportConfiguration {
            // Suppress core.server.lambda$channelActive$0 - AMQ224088 error from load balancer type connections
            options[TransportConstants.HANDSHAKE_TIMEOUT] = 0
            if (trustManagerFactory != null) {
                // NettyAcceptor only creates default TrustManagerFactorys with the provided trust store details. However, we need to use
                // more customised instances which use our revocation checkers, which we pass directly into NodeNettyAcceptorFactory.
                //
                // This, however, requires copying a lot of code from NettyAcceptor into NodeNettyAcceptor. The version of Artemis in
                // Corda 4.9 solves this problem by introducing a "trustManagerFactoryPlugin" config option.
                options[TRUST_MANAGER_FACTORY_NAME] = trustManagerFactory
            }
            return createTransport(
                    "net.corda.node.services.messaging.NodeNettyAcceptorFactory",
                    hostAndPort,
                    protocols,
                    options,
                    enableSSL,
                    threadPoolName,
                    trace,
                    remotingThreads
            )
        }

        private fun createConnectorTransport(hostAndPort: NetworkHostAndPort,
                                             protocols: String,
                                             options: MutableMap<String, Any>,
                                             enableSSL: Boolean,
                                             threadPoolName: String,
                                             trace: Boolean,
                                             remotingThreads: Int?): TransportConfiguration {
            return createTransport(
                    "net.corda.node.services.messaging.NodeNettyConnectorFactory",
                    hostAndPort,
                    protocols,
                    options,
                    enableSSL,
                    threadPoolName,
                    trace,
                    remotingThreads
            )
        }

        private fun createTransport(className: String,
                                    hostAndPort: NetworkHostAndPort,
                                    protocols: String,
                                    options: MutableMap<String, Any>,
                                    enableSSL: Boolean,
                                    threadPoolName: String,
                                    trace: Boolean,
                                    remotingThreads: Int?): TransportConfiguration {
            options += defaultArtemisOptions(hostAndPort, protocols)
            if (enableSSL) {
                options[TransportConstants.ENABLED_CIPHER_SUITES_PROP_NAME] = CIPHER_SUITES.joinToString(",")
                options[TransportConstants.ENABLED_PROTOCOLS_PROP_NAME] = TLS_VERSIONS.joinToString(",")
            }
            // By default, use only one remoting thread in tests (https://github.com/corda/corda/pull/2357)
            options[TransportConstants.REMOTING_THREADS_PROPNAME] = remotingThreads ?: if (nodeSerializationEnv == null) 1 else -1
            options[THREAD_POOL_NAME_NAME] = threadPoolName
            options[TRACE_NAME] = trace
            return TransportConfiguration(className, options)
        }
    }
}
