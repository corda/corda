package net.corda.client.rpc.internal

import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.RPCConnection
import net.corda.client.rpc.UnrecoverableRPCException
import net.corda.client.rpc.ext.RPCConnectionListener
import net.corda.core.context.Actor
import net.corda.core.context.Trace
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.logElapsedTime
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.ArtemisTcpTransport.Companion.rpcConnectorTcpTransport
import net.corda.nodeapi.internal.ArtemisTcpTransport.Companion.rpcConnectorTcpTransportsFromList
import net.corda.nodeapi.internal.ArtemisTcpTransport.Companion.rpcInternalClientTcpTransport
import net.corda.nodeapi.internal.RoundRobinConnectionPolicy
import net.corda.nodeapi.internal.config.SslConfiguration
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArraySet

/**
 * [RPCClient] is meant to run outside of Corda Node JVM and provide connectivity to a node using RPC protocol.
 * Since Corda Node can expose multiple RPC interfaces, it is possible to specify which [RPCOps] interface should be used.
 *
 * When `haAddressPool` [RPCClient] will perform connectivity failover using parameters specified by [CordaRPCClientConfiguration].
 * Whenever status of connection changes registered [RPCConnectionListener] will be informed about those events.
 */
class RPCClient<I : RPCOps>(
        private val transport: TransportConfiguration,
        private val rpcConfiguration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
        private val serializationContext: SerializationContext = SerializationDefaults.RPC_CLIENT_CONTEXT,
        private val haPoolTransportConfigurations: List<TransportConfiguration> = emptyList()
) {
    constructor(
            hostAndPort: NetworkHostAndPort,
            sslConfiguration: ClientRpcSslOptions? = null,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
            serializationContext: SerializationContext = SerializationDefaults.RPC_CLIENT_CONTEXT
    ) : this(rpcConnectorTcpTransport(hostAndPort, sslConfiguration), configuration, serializationContext)

    constructor(
            hostAndPort: NetworkHostAndPort,
            sslConfiguration: SslConfiguration,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
            serializationContext: SerializationContext = SerializationDefaults.RPC_CLIENT_CONTEXT
    ) : this(rpcInternalClientTcpTransport(hostAndPort, sslConfiguration), configuration, serializationContext)

    /**
     * A way to create RPC connections to a pool of RPC addresses for resiliency
     */
    constructor(
            haAddressPool: List<NetworkHostAndPort>,
            sslConfiguration: ClientRpcSslOptions? = null,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
            serializationContext: SerializationContext = SerializationDefaults.RPC_CLIENT_CONTEXT
    ) : this(rpcConnectorTcpTransport(haAddressPool.first(), sslConfiguration),
            configuration, serializationContext, rpcConnectorTcpTransportsFromList(haAddressPool, sslConfiguration))

    companion object {
        private val log = contextLogger()
    }

    private val listeners: MutableSet<RPCConnectionListener<I>> = CopyOnWriteArraySet()

    fun start(
            rpcOpsClass: Class<I>,
            username: String,
            password: String,
            externalTrace: Trace? = null,
            impersonatedActor: Actor? = null,
            targetLegalIdentity: CordaX500Name? = null
    ): RPCConnection<I> {
        return log.logElapsedTime("Startup") {
            val serverLocator = (if (haPoolTransportConfigurations.isEmpty()) {
                ActiveMQClient.createServerLocatorWithoutHA(transport)
            } else {
                ActiveMQClient.createServerLocatorWithoutHA(*haPoolTransportConfigurations.toTypedArray())
            }).apply {
                connectionTTL = 60000
                clientFailureCheckPeriod = 30000
                retryInterval = rpcConfiguration.connectionRetryInterval.toMillis()
                retryIntervalMultiplier = rpcConfiguration.connectionRetryIntervalMultiplier
                maxRetryInterval = rpcConfiguration.connectionMaxRetryInterval.toMillis()
                reconnectAttempts = if (haPoolTransportConfigurations.isEmpty()) rpcConfiguration.maxReconnectAttempts else 0
                minLargeMessageSize = rpcConfiguration.maxFileSize
                isUseGlobalPools = nodeSerializationEnv != null
                // By default RoundRobinConnectionLoadBalancingPolicy is used that picks first endpoint from the pool
                // at random. This may be undesired and non-deterministic. For more information, see [RoundRobinConnectionPolicy]
                connectionLoadBalancingPolicyClassName = RoundRobinConnectionPolicy::class.java.canonicalName
                // Without this any type of "send" time failures will not be delivered back to the client
                isBlockOnNonDurableSend = true
            }

            val targetString = "${transport.params[TransportConstants.HOST_PROP_NAME]}:${transport.params[TransportConstants.PORT_PROP_NAME]}"
            val rpcClientTelemetry = RPCClientTelemetry("rpcClient-$targetString", rpcConfiguration.openTelemetryEnabled,
                    rpcConfiguration.simpleLogTelemetryEnabled, rpcConfiguration.spanStartEndEventsEnabled, rpcConfiguration.copyBaggageToTags)
            val sessionId = Trace.SessionId.newInstance()
            val distributionMux = DistributionMux(listeners, username)
            val proxyHandler = RPCClientProxyHandler(rpcConfiguration, username, password, serverLocator,
                    rpcOpsClass, serializationContext, sessionId, externalTrace, impersonatedActor, targetLegalIdentity, distributionMux,
                    rpcClientTelemetry)
            try {
                proxyHandler.start()
                val ops: I = uncheckedCast(Proxy.newProxyInstance(rpcOpsClass.classLoader, arrayOf(rpcOpsClass), proxyHandler))
                val serverProtocolVersion = ops.protocolVersion
                if (serverProtocolVersion < rpcConfiguration.minimumServerProtocolVersion) {
                    throw UnrecoverableRPCException("Requested minimum protocol version " +
                            "(${rpcConfiguration.minimumServerProtocolVersion}) is higher" +
                            " than the server's supported protocol version ($serverProtocolVersion)")
                }
                proxyHandler.setServerProtocolVersion(serverProtocolVersion)

                log.debug("RPC connected, returning proxy")
                val connection = object : RPCConnection<I> {
                    override val proxy = ops
                    override val serverProtocolVersion = serverProtocolVersion

                    override fun <T> getTelemetryHandle(telemetryClass: Class<T>): T? {
                        return rpcClientTelemetry.getTelemetryHandle(telemetryClass)
                    }

                    private fun close(notify: Boolean) {
                        if (notify) {
                            proxyHandler.notifyServerAndClose()
                        } else {
                            proxyHandler.forceClose()
                        }
                        serverLocator.close()
                        rpcClientTelemetry.telemetryService.shutdownTelemetry()
                    }

                    override fun notifyServerAndClose() {
                        close(true)
                    }

                    override fun forceClose() {
                        close(false)
                    }
                }
                distributionMux.connectionOpt = connection
                distributionMux.onConnect()
                connection
            } catch (throwable: Throwable) {
                proxyHandler.notifyServerAndClose()
                serverLocator.close()
                distributionMux.onPermanentFailure(throwable)
                throw throwable
            }
        }
    }

    /**
     * Adds [RPCConnectionListener] to this [RPCClient] to be informed about important connectivity events.
     * @return `true` if the element has been added, `false` when listener is already contained in the set of listeners.
     */
    fun addConnectionListener(listener: RPCConnectionListener<I>) : Boolean {
        return listeners.add(listener)
    }

    /**
     * Removes [RPCConnectionListener] from this [RPCClient].
     *
     * @return `true` if the element has been successfully removed; `false` if it was not present in the set of listeners.
     */
    fun removeConnectionListener(listener: RPCConnectionListener<I>) : Boolean {
        return listeners.remove(listener)
    }
}