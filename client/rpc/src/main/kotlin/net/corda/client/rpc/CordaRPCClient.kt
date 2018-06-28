/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.rpc

import net.corda.client.rpc.internal.RPCClient
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.core.context.Actor
import net.corda.core.context.Trace
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.ArtemisTcpTransport.Companion.rpcConnectorTcpTransport
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.utilities.days
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT
import java.time.Duration

/**
 * This class is essentially just a wrapper for an RPCConnection<CordaRPCOps> and can be treated identically.
 *
 * @see RPCConnection
 */
class CordaRPCConnection internal constructor(connection: RPCConnection<CordaRPCOps>) : RPCConnection<CordaRPCOps> by connection

/**
 * Can be used to configure the RPC client connection.
 */
open class CordaRPCClientConfiguration @JvmOverloads constructor(

        /**
         * Maximum retry interval.
         */
        open val connectionMaxRetryInterval: Duration = 3.minutes,

        /**
         * The minimum protocol version required from the server.
         */
        open val minimumServerProtocolVersion: Int = 0,

        /**
         * If set to true the client will track RPC call sites. If an error occurs subsequently during the RPC or in a
         * returned Observable stream the stack trace of the originating RPC will be shown as well. Note that
         * constructing call stacks is a moderately expensive operation.
         */
        open val trackRpcCallSites: Boolean = false,

        /**
         * The interval of unused observable reaping. Leaked Observables (unused ones) are detected using weak references
         * and are cleaned up in batches in this interval. If set too large it will waste server side resources for this
         * duration. If set too low it wastes client side cycles.
         */
        open val reapInterval: Duration = 1.seconds,

        /**
         * The number of threads to use for observations (for executing [Observable.onNext]).
         */
        open val observationExecutorPoolSize: Int = 4,

        /**
         * Determines the concurrency level of the Observable Cache. This is exposed because it implicitly determines
         * the limit on the number of leaked observables reaped because of garbage collection per reaping.
         * See the implementation of [com.google.common.cache.LocalCache] for details.
         */
        open val cacheConcurrencyLevel: Int = 1,

        /**
         * The retry interval of Artemis connections in milliseconds.
         */
        open val connectionRetryInterval: Duration = 5.seconds,

        /**
         * The retry interval multiplier for exponential backoff.
         */
        open val connectionRetryIntervalMultiplier: Double = 1.5,

        /**
         * Maximum reconnect attempts on failover>
         */
        open val maxReconnectAttempts: Int = unlimitedReconnectAttempts,

        /**
         * Maximum file size, in bytes.
         */
        open val maxFileSize: Int = 10485760,
        // 10 MiB maximum allowed file size for attachments, including message headers.
        // TODO: acquire this value from Network Map when supported.

        /**
         * The cache expiry of a deduplication watermark per client.
         */
        open val deduplicationCacheExpiry: Duration = 1.days

) {

    companion object {

        private const val unlimitedReconnectAttempts = -1

        @JvmField
        val DEFAULT: CordaRPCClientConfiguration = CordaRPCClientConfiguration()

    }

    /**
     * Create a new copy of a configuration object with zero or more parameters modified.
     */
    @JvmOverloads
    fun copy(
            connectionMaxRetryInterval: Duration = this.connectionMaxRetryInterval,
            minimumServerProtocolVersion: Int = this.minimumServerProtocolVersion,
            trackRpcCallSites: Boolean = this.trackRpcCallSites,
            reapInterval: Duration = this.reapInterval,
            observationExecutorPoolSize: Int = this.observationExecutorPoolSize,
            cacheConcurrencyLevel: Int = this.cacheConcurrencyLevel,
            connectionRetryInterval: Duration = this.connectionRetryInterval,
            connectionRetryIntervalMultiplier: Double = this.connectionRetryIntervalMultiplier,
            maxReconnectAttempts: Int = this.maxReconnectAttempts,
            maxFileSize: Int = this.maxFileSize,
            deduplicationCacheExpiry: Duration = this.deduplicationCacheExpiry
    ): CordaRPCClientConfiguration {
        return CordaRPCClientConfiguration(
                connectionMaxRetryInterval,
                minimumServerProtocolVersion,
                trackRpcCallSites,
                reapInterval,
                observationExecutorPoolSize,
                cacheConcurrencyLevel,
                connectionRetryInterval,
                connectionRetryIntervalMultiplier,
                maxReconnectAttempts,
                maxFileSize,
                deduplicationCacheExpiry
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CordaRPCClientConfiguration
        if (connectionMaxRetryInterval != other.connectionMaxRetryInterval) return false
        if (minimumServerProtocolVersion != other.minimumServerProtocolVersion) return false
        if (trackRpcCallSites != other.trackRpcCallSites) return false
        if (reapInterval != other.reapInterval) return false
        if (observationExecutorPoolSize != other.observationExecutorPoolSize) return false
        if (cacheConcurrencyLevel != other.cacheConcurrencyLevel) return false
        if (connectionRetryInterval != other.connectionRetryInterval) return false
        if (connectionRetryIntervalMultiplier != other.connectionRetryIntervalMultiplier) return false
        if (maxReconnectAttempts != other.maxReconnectAttempts) return false
        if (maxFileSize != other.maxFileSize) return false
        if (deduplicationCacheExpiry != other.deduplicationCacheExpiry) return false

        return true
    }

    override fun hashCode(): Int {
        var result = minimumServerProtocolVersion
        result = 31 * result + connectionMaxRetryInterval.hashCode()
        result = 31 * result + trackRpcCallSites.hashCode()
        result = 31 * result + reapInterval.hashCode()
        result = 31 * result + observationExecutorPoolSize
        result = 31 * result + cacheConcurrencyLevel
        result = 31 * result + connectionRetryInterval.hashCode()
        result = 31 * result + connectionRetryIntervalMultiplier.hashCode()
        result = 31 * result + maxReconnectAttempts
        result = 31 * result + maxFileSize
        result = 31 * result + deduplicationCacheExpiry.hashCode()
        return result
    }

    override fun toString(): String {
        return "CordaRPCClientConfiguration(" +
                "connectionMaxRetryInterval=$connectionMaxRetryInterval, " +
                "minimumServerProtocolVersion=$minimumServerProtocolVersion, trackRpcCallSites=$trackRpcCallSites, " +
                "reapInterval=$reapInterval, observationExecutorPoolSize=$observationExecutorPoolSize, " +
                "cacheConcurrencyLevel=$cacheConcurrencyLevel, connectionRetryInterval=$connectionRetryInterval, " +
                "connectionRetryIntervalMultiplier=$connectionRetryIntervalMultiplier, " +
                "maxReconnectAttempts=$maxReconnectAttempts, maxFileSize=$maxFileSize, " +
                "deduplicationCacheExpiry=$deduplicationCacheExpiry)"
    }

    // Left is for backwards compatibility with version 3.1
    operator fun component1() = connectionMaxRetryInterval

}

/**
 * An RPC client connects to the specified server and allows you to make calls to the server that perform various
 * useful tasks. Please see the Client RPC section of docs.corda.net to learn more about how this API works. A brief
 * description is provided here.
 *
 * Calling [start] returns an [RPCConnection] containing a proxy that lets you invoke RPCs on the server. Calls on
 * it block, and if the server throws an exception then it will be rethrown on the client. Proxies are thread safe and
 * may be used to invoke multiple RPCs in parallel.
 *
 * RPC sends and receives are logged on the net.corda.rpc logger.
 *
 * The [CordaRPCOps] defines what client RPCs are available. If an RPC returns an [rx.Observable] anywhere in the object
 * graph returned then the server-side observable is transparently forwarded to the client side here.
 * *You are expected to use it*. The server will begin sending messages immediately that will be buffered on the
 * client, you are expected to drain by subscribing to the returned observer. You can opt-out of this by simply
 * calling the [net.corda.client.rpc.notUsed] method on it.
 *
 * You don't have to explicitly close the observable if you actually subscribe to it: it will close itself and free up
 * the server-side resources either when the client or JVM itself is shutdown, or when there are no more subscribers to
 * it. Once all the subscribers to a returned observable are unsubscribed or the observable completes successfully or
 * with an error, the observable is closed and you can't then re-subscribe again: you'll have to re-request a fresh
 * observable with another RPC.
 *
 * In case of loss of connection to the server, the client will try to reconnect using the settings provided via
 * [CordaRPCClientConfiguration]. While attempting failover, current and future RPC calls will throw
 * [RPCException] and previously returned observables will call onError().
 *
 * If the client was created using a list of hosts, automatic failover will occur (the servers have to be started in
 * HA mode).
 *
 * @param hostAndPort The network address to connect to.
 * @param configuration An optional configuration used to tweak client behaviour.
 * @param sslConfiguration An optional [ClientRpcSslOptions] used to enable secure communication with the server.
 * @param haAddressPool A list of [NetworkHostAndPort] representing the addresses of servers in HA mode.
 * The client will attempt to connect to a live server by trying each address in the list. If the servers are not in
 * HA mode, the client will round-robin from the beginning of the list and try all servers.
 */
class CordaRPCClient private constructor(
        private val hostAndPort: NetworkHostAndPort,
        private val configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
        private val sslConfiguration: ClientRpcSslOptions? = null,
        private val nodeSslConfiguration: SSLConfiguration? = null,
        private val classLoader: ClassLoader? = null,
        private val haAddressPool: List<NetworkHostAndPort> = emptyList(),
        private val internalConnection: Boolean = false
) {
    @JvmOverloads
    constructor(hostAndPort: NetworkHostAndPort,
                configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT)
            : this(hostAndPort, configuration, null)

    /**
     * @param haAddressPool A list of [NetworkHostAndPort] representing the addresses of servers in HA mode.
     * The client will attempt to connect to a live server by trying each address in the list. If the servers are not in
     * HA mode, the client will round-robin from the beginning of the list and try all servers.
     * @param configuration An optional configuration used to tweak client behaviour.
     */
    @JvmOverloads
    constructor(haAddressPool: List<NetworkHostAndPort>, configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT) : this(haAddressPool.first(), configuration, null, null, null, haAddressPool)

    companion object {
        fun createWithSsl(
                hostAndPort: NetworkHostAndPort,
                sslConfiguration: ClientRpcSslOptions,
                configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
        ): CordaRPCClient {
            return CordaRPCClient(hostAndPort, configuration, sslConfiguration)
        }

        fun createWithSsl(
                haAddressPool: List<NetworkHostAndPort>,
                sslConfiguration: ClientRpcSslOptions,
                configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
        ): CordaRPCClient {
            return CordaRPCClient(haAddressPool.first(), configuration, sslConfiguration, haAddressPool = haAddressPool)
        }

        internal fun createWithSslAndClassLoader(
                hostAndPort: NetworkHostAndPort,
                configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
                sslConfiguration: ClientRpcSslOptions? = null,
                classLoader: ClassLoader? = null
        ): CordaRPCClient {
            return CordaRPCClient(hostAndPort, configuration, sslConfiguration, null, classLoader)
        }

        internal fun createWithInternalSslAndClassLoader(
                hostAndPort: NetworkHostAndPort,
                configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
                sslConfiguration: SSLConfiguration?,
                classLoader: ClassLoader? = null
        ): CordaRPCClient {
            return CordaRPCClient(hostAndPort, configuration, null, sslConfiguration, classLoader, internalConnection = true)
        }

        internal fun createWithSslAndClassLoader(
                haAddressPool: List<NetworkHostAndPort>,
                configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
                sslConfiguration: ClientRpcSslOptions? = null,
                classLoader: ClassLoader? = null
        ): CordaRPCClient {
            return CordaRPCClient(haAddressPool.first(), configuration, sslConfiguration, null, classLoader, haAddressPool)
        }
    }

    init {
        try {
            effectiveSerializationEnv
        } catch (e: IllegalStateException) {
            try {
                AMQPClientSerializationScheme.initialiseSerialization(classLoader)
            } catch (e: IllegalStateException) {
                // Race e.g. two of these constructed in parallel, ignore.
            }
        }
    }

    private fun getRpcClient(): RPCClient<CordaRPCOps> {
        return when {
        // Node->RPC broker, mutually authenticated SSL. This is used when connecting the integrated shell
            internalConnection == true -> RPCClient(hostAndPort, nodeSslConfiguration!!)

        // Client->RPC broker
            haAddressPool.isEmpty() -> RPCClient(
                    rpcConnectorTcpTransport(hostAndPort, config = sslConfiguration),
                    configuration,
                    if (classLoader != null) AMQP_RPC_CLIENT_CONTEXT.withClassLoader(classLoader) else AMQP_RPC_CLIENT_CONTEXT)
            else -> {
                RPCClient(haAddressPool,
                        sslConfiguration,
                        configuration,
                        if (classLoader != null) AMQP_RPC_CLIENT_CONTEXT.withClassLoader(classLoader) else AMQP_RPC_CLIENT_CONTEXT)
            }
        }
    }

    /**
     * Logs in to the target server and returns an active connection. The returned connection is a [java.io.Closeable]
     * and can be used with a try-with-resources statement. If you don't use that, you should use the
     * [RPCConnection.notifyServerAndClose] or [RPCConnection.forceClose] methods to dispose of the connection object
     * when done.
     *
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @throws RPCException if the server version is too low or if the server isn't reachable within a reasonable timeout.
     */
    fun start(username: String, password: String): CordaRPCConnection {
        return start(username, password, null, null)
    }

    /**
     * Logs in to the target server and returns an active connection. The returned connection is a [java.io.Closeable]
     * and can be used with a try-with-resources statement. If you don't use that, you should use the
     * [RPCConnection.notifyServerAndClose] or [RPCConnection.forceClose] methods to dispose of the connection object
     * when done.
     *
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @param externalTrace external [Trace] for correlation.
     * @throws RPCException if the server version is too low or if the server isn't reachable within a reasonable timeout.
     */
    fun start(username: String, password: String, externalTrace: Trace?, impersonatedActor: Actor?): CordaRPCConnection {
        return CordaRPCConnection(getRpcClient().start(CordaRPCOps::class.java, username, password, externalTrace, impersonatedActor))
    }

    /**
     * A helper for Kotlin users that simply closes the connection after the block has executed. Be careful not to
     * over-use this, as setting up and closing connections takes time.
     */
    inline fun <A> use(username: String, password: String, block: (CordaRPCConnection) -> A): A {
        return start(username, password).use(block)
    }
}