package net.corda.client.rpc

import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.client.rpc.internal.RPCClient
import net.corda.client.rpc.internal.CordaRPCClientConfigurationImpl
import net.corda.core.context.Actor
import net.corda.core.context.Trace
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.ArtemisTcpTransport.Companion.tcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.serialization.KRYO_RPC_CLIENT_CONTEXT
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
interface CordaRPCClientConfiguration {

    /** The minimum protocol version required from the server */
    val minimumServerProtocolVersion: Int get() = default().minimumServerProtocolVersion
    /**
     * If set to true the client will track RPC call sites. If an error occurs subsequently during the RPC or in a
     * returned Observable stream the stack trace of the originating RPC will be shown as well. Note that
     * constructing call stacks is a moderately expensive operation.
     */
    val trackRpcCallSites: Boolean get() = default().trackRpcCallSites
    /**
     * The interval of unused observable reaping. Leaked Observables (unused ones) are detected using weak references
     * and are cleaned up in batches in this interval. If set too large it will waste server side resources for this
     * duration. If set too low it wastes client side cycles.
     */
    val reapInterval: Duration get() = default().reapInterval
    /** The number of threads to use for observations (for executing [Observable.onNext]) */
    val observationExecutorPoolSize: Int get() = default().observationExecutorPoolSize
    /**
     * Determines the concurrency level of the Observable Cache. This is exposed because it implicitly determines
     * the limit on the number of leaked observables reaped because of garbage collection per reaping.
     * See the implementation of [com.google.common.cache.LocalCache] for details.
     */
    val cacheConcurrencyLevel: Int get() = default().cacheConcurrencyLevel
    /** The retry interval of artemis connections in milliseconds */
    val connectionRetryInterval: Duration get() = default().connectionRetryInterval
    /** The retry interval multiplier for exponential backoff */
    val connectionRetryIntervalMultiplier: Double get() = default().connectionRetryIntervalMultiplier
    /** Maximum retry interval */
    val connectionMaxRetryInterval: Duration get() = default().connectionMaxRetryInterval
    /** Maximum reconnect attempts on failover */
    val maxReconnectAttempts: Int get() = default().maxReconnectAttempts
    /** Maximum file size */
    val maxFileSize: Int get() = default().maxFileSize
    /** The cache expiry of a deduplication watermark per client. */
    val deduplicationCacheExpiry: Duration get() = default().deduplicationCacheExpiry

    companion object {
        fun default(): CordaRPCClientConfiguration = CordaRPCClientConfigurationImpl.default
    }
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
 * @param hostAndPort The network address to connect to.
 * @param configuration An optional configuration used to tweak client behaviour.
 * @param sslConfiguration An optional [SSLConfiguration] used to enable secure communication with the server.
 */
class CordaRPCClient private constructor(
        hostAndPort: NetworkHostAndPort,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.default(),
        sslConfiguration: SSLConfiguration? = null,
        classLoader: ClassLoader? = null
) {
    @JvmOverloads
    constructor(hostAndPort: NetworkHostAndPort, configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.default()) : this(hostAndPort, configuration, null)

    companion object {
        internal fun createWithSsl(
                hostAndPort: NetworkHostAndPort,
                configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.default(),
                sslConfiguration: SSLConfiguration? = null
        ): CordaRPCClient {
            return CordaRPCClient(hostAndPort, configuration, sslConfiguration)
        }

        internal fun createWithSslAndClassLoader(
                hostAndPort: NetworkHostAndPort,
                configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.default(),
                sslConfiguration: SSLConfiguration? = null,
                classLoader: ClassLoader? = null
        ): CordaRPCClient {
            return CordaRPCClient(hostAndPort, configuration, sslConfiguration, classLoader)
        }
    }

    init {
        try {
            effectiveSerializationEnv
        } catch (e: IllegalStateException) {
            try {
                KryoClientSerializationScheme.initialiseSerialization(classLoader)
            } catch (e: IllegalStateException) {
                // Race e.g. two of these constructed in parallel, ignore.
            }
        }
    }

    private val rpcClient = RPCClient<CordaRPCOps>(
            tcpTransport(ConnectionDirection.Outbound(), hostAndPort, config = sslConfiguration),
            configuration,
            if (classLoader != null) KRYO_RPC_CLIENT_CONTEXT.withClassLoader(classLoader) else KRYO_RPC_CLIENT_CONTEXT
    )

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
        return CordaRPCConnection(rpcClient.start(CordaRPCOps::class.java, username, password, externalTrace, impersonatedActor))
    }

    /**
     * A helper for Kotlin users that simply closes the connection after the block has executed. Be careful not to
     * over-use this, as setting up and closing connections takes time.
     */
    inline fun <A> use(username: String, password: String, block: (CordaRPCConnection) -> A): A {
        return start(username, password).use(block)
    }
}