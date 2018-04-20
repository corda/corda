/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.rpc.internal

import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.RPCConnection
import net.corda.client.rpc.RPCException
import net.corda.core.context.Actor
import net.corda.core.context.Trace
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.logElapsedTime
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.*
import net.corda.nodeapi.ArtemisTcpTransport.Companion.tcpTransport
import net.corda.nodeapi.ArtemisTcpTransport.Companion.tcpTransportsFromList
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.config.SSLConfiguration
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import java.lang.reflect.Proxy
import java.time.Duration

/**
 * This configuration may be used to tweak the internals of the RPC client.
 */
data class CordaRPCClientConfigurationImpl(
        override val minimumServerProtocolVersion: Int,
        override val trackRpcCallSites: Boolean,
        override val reapInterval: Duration,
        override val observationExecutorPoolSize: Int,
        override val connectionRetryInterval: Duration,
        override val connectionRetryIntervalMultiplier: Double,
        override val connectionMaxRetryInterval: Duration,
        override val maxReconnectAttempts: Int,
        override val maxFileSize: Int,
        override val deduplicationCacheExpiry: Duration
) : CordaRPCClientConfiguration {
    companion object {
        private const val unlimitedReconnectAttempts = -1
        @JvmStatic
        val default = CordaRPCClientConfigurationImpl(
                minimumServerProtocolVersion = 0,
                trackRpcCallSites = false,
                reapInterval = 1.seconds,
                observationExecutorPoolSize = 4,
                connectionRetryInterval = 5.seconds,
                connectionRetryIntervalMultiplier = 1.5,
                connectionMaxRetryInterval = 3.minutes,
                maxReconnectAttempts = unlimitedReconnectAttempts,
                /** 10 MiB maximum allowed file size for attachments, including message headers. TODO: acquire this value from Network Map when supported. */
                maxFileSize = 10485760,
                deduplicationCacheExpiry = 1.days
        )
    }
}

class RPCClient<I : RPCOps>(
        val transport: TransportConfiguration,
        val rpcConfiguration: CordaRPCClientConfiguration = CordaRPCClientConfigurationImpl.default,
        val serializationContext: SerializationContext = SerializationDefaults.RPC_CLIENT_CONTEXT,
        val haPoolTransportConfigurations: List<TransportConfiguration> = emptyList()
) {
    constructor(
            hostAndPort: NetworkHostAndPort,
            sslConfiguration: SSLConfiguration? = null,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfigurationImpl.default,
            serializationContext: SerializationContext = SerializationDefaults.RPC_CLIENT_CONTEXT
    ) : this(tcpTransport(ConnectionDirection.Outbound(), hostAndPort, sslConfiguration), configuration, serializationContext)

    constructor(
            haAddressPool: List<NetworkHostAndPort>,
            sslConfiguration: SSLConfiguration? = null,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfigurationImpl.default,
            serializationContext: SerializationContext = SerializationDefaults.RPC_CLIENT_CONTEXT
    ) : this(tcpTransport(ConnectionDirection.Outbound(), haAddressPool.first(), sslConfiguration),
            configuration, serializationContext, tcpTransportsFromList(ConnectionDirection.Outbound(), haAddressPool, sslConfiguration))

    companion object {
        private val log = contextLogger()
    }

    fun start(
            rpcOpsClass: Class<I>,
            username: String,
            password: String,
            externalTrace: Trace? = null,
            impersonatedActor: Actor? = null
    ): RPCConnection<I> {
        return log.logElapsedTime("Startup") {
            val clientAddress = SimpleString("${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$username.${random63BitValue()}")

            val serverLocator = (if (haPoolTransportConfigurations.isEmpty()) {
                ActiveMQClient.createServerLocatorWithoutHA(transport)
            } else {
                ActiveMQClient.createServerLocatorWithoutHA(*haPoolTransportConfigurations.toTypedArray())
            }).apply {
                retryInterval = rpcConfiguration.connectionRetryInterval.toMillis()
                retryIntervalMultiplier = rpcConfiguration.connectionRetryIntervalMultiplier
                maxRetryInterval = rpcConfiguration.connectionMaxRetryInterval.toMillis()
                reconnectAttempts = if (haPoolTransportConfigurations.isEmpty()) rpcConfiguration.maxReconnectAttempts else 0
                minLargeMessageSize = rpcConfiguration.maxFileSize
                isUseGlobalPools = nodeSerializationEnv != null
            }
            val sessionId = Trace.SessionId.newInstance()
            val proxyHandler = RPCClientProxyHandler(rpcConfiguration, username, password, serverLocator, clientAddress, rpcOpsClass, serializationContext, sessionId, externalTrace, impersonatedActor)
            try {
                proxyHandler.start()
                val ops: I = uncheckedCast(Proxy.newProxyInstance(rpcOpsClass.classLoader, arrayOf(rpcOpsClass), proxyHandler))
                val serverProtocolVersion = ops.protocolVersion
                if (serverProtocolVersion < rpcConfiguration.minimumServerProtocolVersion) {
                    throw RPCException("Requested minimum protocol version (${rpcConfiguration.minimumServerProtocolVersion}) is higher" +
                            " than the server's supported protocol version ($serverProtocolVersion)")
                }
                proxyHandler.setServerProtocolVersion(serverProtocolVersion)

                log.debug("RPC connected, returning proxy")
                object : RPCConnection<I> {
                    override val proxy = ops
                    override val serverProtocolVersion = serverProtocolVersion

                    private fun close(notify: Boolean) {
                        if (notify) {
                            proxyHandler.notifyServerAndClose()
                        } else {
                            proxyHandler.forceClose()
                        }
                        serverLocator.close()
                    }

                    override fun notifyServerAndClose() {
                        close(true)
                    }

                    override fun forceClose() {
                        close(false)
                    }

                    override fun close() {
                        close(true)
                    }
                }
            } catch (exception: Throwable) {
                proxyHandler.notifyServerAndClose()
                serverLocator.close()
                throw exception
            }
        }
    }
}
