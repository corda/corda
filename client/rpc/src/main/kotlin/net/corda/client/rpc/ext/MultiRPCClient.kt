package net.corda.client.rpc.ext

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.RPCConnection
import net.corda.client.rpc.RPCException
import net.corda.client.rpc.internal.RPCClient
import net.corda.client.rpc.internal.SerializationEnvironmentHelper
import net.corda.core.context.Actor
import net.corda.core.context.Trace
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.ArtemisTcpTransport
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An RPC client connects to the specified server and allows to make calls using specified remote interface to the server that perform various
 * useful tasks. Please see the Client RPC section of [Corda Documentation](http://docs.corda.net) to learn more about how this API works.
 * Only a brief description is provided here.
 *
 * Calling [start] returns an [RPCConnection] containing a proxy that allows making RPCs  calls to the server.
 * This is a blocking communication, and if the server throws an exception then it will be rethrown on the client. Proxies are thread safe and
 * may be used to invoke multiple RPCs in parallel.
 *
 * RPC sends and receives are logged on the `net.corda.rpc` logger.
 *
 * In case of loss of connection to the server, the client will try to reconnect using the settings provided via
 * [CordaRPCClientConfiguration]. If the client was created using a list of hosts via [haAddressPool], automatic failover will occur
 * (the servers have to be started in HA mode). While attempting failover, current and future RPC calls will throw
 * [RPCException].
 *
 * It is also possible to add [RPCConnectionListener]s event before connection is started to be notified about connection lifecycle.
 * Please see documentation on [RPCConnectionListener] for more details.
 *
 * @param hostAndPort The network address to connect to.
 * @param haAddressPool A list of [NetworkHostAndPort] representing the addresses of servers in HA mode.
 *  The client will attempt to connect to a live server by trying each address in the list. If the servers are not in
 *  HA mode, the client will round-robin from the beginning of the list and try all servers.
 * @param rpcOpsClass [Class] instance of the [RPCOps] remote interface that will be used for communication.
 * @param username The username to authenticate with.
 * @param password The password to authenticate with.
 * @param configuration An optional configuration used to tweak client behaviour.
 * @param sslConfiguration An optional [ClientRpcSslOptions] used to enable secure communication with the server.
 * @param classLoader a classloader, which will be used (if provided) to discover available [SerializationCustomSerializer]s
 *  and [SerializationWhitelist]s. If no classloader is provided, the classloader of the current class will be used by default
 *  for the aforementioned discovery process.
 * @param customSerializers a set of [SerializationCustomSerializer]s to be used. If this parameter is specified, then no classpath scanning
 *  will be performed for custom serializers, the provided ones will be used instead. This parameter serves as a more user-friendly option
 *  to specify your serializers and disable the classpath scanning (e.g. for performance reasons).
 * @param externalTrace external [Trace] for correlation.
 * @param impersonatedActor the actor on behalf of which all the invocations will be made.
 * @param targetLegalIdentity in case of multi-identity RPC endpoint specific legal identity to which the calls must be addressed.
 */
class MultiRPCClient<I : RPCOps> private constructor(
        private val hostAndPort: NetworkHostAndPort?,
        private val haAddressPool: List<NetworkHostAndPort>,
        private val rpcOpsClass: Class<I>,
        private val username: String,
        private val password: String,
        private val configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
        private val sslConfiguration: ClientRpcSslOptions? = null,
        private val classLoader: ClassLoader? = null,
        private val customSerializers: Set<SerializationCustomSerializer<*, *>>? = null,
        private val externalTrace: Trace? = null,
        private val impersonatedActor: Actor? = null,
        private val targetLegalIdentity: CordaX500Name? = null
) : AutoCloseable {

    private companion object {
        private val logger = contextLogger()
    }

    @JvmOverloads
    constructor(
            hostAndPort: NetworkHostAndPort,
            rpcOpsClass: Class<I>,
            username: String,
            password: String,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
    ) : this(
            hostAndPort = hostAndPort,
            haAddressPool = emptyList(),
            rpcOpsClass = rpcOpsClass,
            username = username,
            password = password,
            configuration = configuration
    )

    constructor(
            hostAndPort: NetworkHostAndPort,
            rpcOpsClass: Class<I>,
            username: String,
            password: String,
            classLoader: ClassLoader,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
    ) : this(
            hostAndPort = hostAndPort,
            rpcOpsClass = rpcOpsClass,
            username = username,
            password = password,
            configuration = configuration,
            sslConfiguration = null,
            classLoader = classLoader
    )

    constructor(
            hostAndPort: NetworkHostAndPort,
            rpcOpsClass: Class<I>,
            username: String,
            password: String,
            sslConfiguration: ClientRpcSslOptions? = null,
            classLoader: ClassLoader? = null
    ) : this(
            hostAndPort = hostAndPort,
            haAddressPool = emptyList(),
            rpcOpsClass = rpcOpsClass,
            username = username,
            password = password,
            sslConfiguration = sslConfiguration,
            classLoader = classLoader
    )

    @JvmOverloads
    constructor(
            hostAndPort: NetworkHostAndPort,
            rpcOpsClass: Class<I>,
            username: String,
            password: String,
            configuration: CordaRPCClientConfiguration,
            sslConfiguration: ClientRpcSslOptions?,
            classLoader: ClassLoader? = null
    ) : this(
            hostAndPort = hostAndPort,
            haAddressPool = emptyList(),
            rpcOpsClass = rpcOpsClass,
            username = username,
            password = password,
            configuration = configuration,
            sslConfiguration = sslConfiguration,
            classLoader = classLoader
    )

    @JvmOverloads
    constructor(
            haAddressPool: List<NetworkHostAndPort>,
            rpcOpsClass: Class<I>,
            username: String,
            password: String,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
            sslConfiguration: ClientRpcSslOptions? = null,
            classLoader: ClassLoader? = null
    ) : this(
            hostAndPort = null,
            haAddressPool = haAddressPool,
            rpcOpsClass = rpcOpsClass,
            username = username,
            password = password,
            configuration = configuration,
            sslConfiguration = sslConfiguration,
            classLoader = classLoader
    )

    @JvmOverloads
    constructor(
            hostAndPort: NetworkHostAndPort,
            rpcOpsClass: Class<I>,
            username: String,
            password: String,
            customSerializers: Set<SerializationCustomSerializer<*, *>>?,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
            sslConfiguration: ClientRpcSslOptions? = null,
            classLoader: ClassLoader? = null,
            externalTrace: Trace? = null,
            impersonatedActor: Actor? = null,
            targetLegalIdentity: CordaX500Name? = null
    ) : this(
            hostAndPort = hostAndPort,
            haAddressPool = emptyList(),
            rpcOpsClass = rpcOpsClass,
            username = username,
            password = password,
            configuration = configuration,
            sslConfiguration = sslConfiguration,
            classLoader = classLoader,
            customSerializers = customSerializers,
            externalTrace = externalTrace,
            impersonatedActor = impersonatedActor,
            targetLegalIdentity = targetLegalIdentity
    )

    @JvmOverloads
    constructor(
            haAddressPool: List<NetworkHostAndPort>,
            rpcOpsClass: Class<I>,
            username: String,
            password: String,
            customSerializers: Set<SerializationCustomSerializer<*, *>>?,
            configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
            sslConfiguration: ClientRpcSslOptions? = null,
            classLoader: ClassLoader? = null,
            externalTrace: Trace? = null,
            impersonatedActor: Actor? = null,
            targetLegalIdentity: CordaX500Name? = null

    ) : this(
            hostAndPort = null,
            haAddressPool = haAddressPool,
            rpcOpsClass = rpcOpsClass,
            username = username,
            password = password,
            configuration = configuration,
            sslConfiguration = sslConfiguration,
            classLoader = classLoader,
            customSerializers = customSerializers,
            externalTrace = externalTrace,
            impersonatedActor = impersonatedActor,
            targetLegalIdentity = targetLegalIdentity
    )

    init {
        SerializationEnvironmentHelper.ensureEffectiveSerializationEnvSet(classLoader, customSerializers)
    }

    private val endpointString: String get() = hostAndPort?.toString() ?: haAddressPool.toString()

    private val internalImpl: RPCClient<I> = createInternalRpcClient()

    private val connectionFuture = CompletableFuture<RPCConnection<I>>()
    private val connectionStarterThread = Executors.newSingleThreadExecutor(
            ThreadFactoryBuilder().setNameFormat("RPCConnectionStarter-$username@$endpointString").build())
    private val connectionStarted = AtomicBoolean(false)

    private fun createInternalRpcClient(): RPCClient<I> {
        val serializationContext = if (classLoader != null) {
            AMQP_RPC_CLIENT_CONTEXT.withClassLoader(classLoader)
        } else {
            AMQP_RPC_CLIENT_CONTEXT
        }
        return when {
            haAddressPool.isEmpty() -> RPCClient(
                    ArtemisTcpTransport.rpcConnectorTcpTransport(hostAndPort!!, config = sslConfiguration),
                    configuration,
                    serializationContext)
            else -> {
                RPCClient(haAddressPool, sslConfiguration, configuration, serializationContext)
            }
        }
    }

    /**
     * Adds [RPCConnectionListener] to this [MultiRPCClient] to be informed about important connectivity events.
     * @return `true` if the element has been added, `false` when listener is already contained in the set of listeners.
     */
    fun addConnectionListener(listener: RPCConnectionListener<I>): Boolean {
        return internalImpl.addConnectionListener(listener)
    }

    /**
     * Removes [RPCConnectionListener] from this [MultiRPCClient].
     *
     * @return `true` if the element has been successfully removed; `false` if it was not present in the set of listeners.
     */
    fun removeConnectionListener(listener: RPCConnectionListener<I>): Boolean {
        return internalImpl.removeConnectionListener(listener)
    }

    /**
     * Logs in to the target server and returns an active connection.
     *
     * It only makes sense to this method once. If it is called repeatedly it will return the same by reference [CompletableFuture]
     *
     * @return [CompletableFuture] containing [RPCConnection]  or throwing [RPCException] if the server version is too low or if the server is not
     * reachable within a reasonable timeout or if login credentials provided are incorrect.
     */
    fun start(): CompletableFuture<RPCConnection<I>> {
        if(connectionStarted.compareAndSet(false, true)) {
            connectionStarterThread.submit {
                try {
                    connectionFuture.complete(internalImpl.start(
                            rpcOpsClass, username, password, externalTrace, impersonatedActor, targetLegalIdentity))
                } catch (ex: Throwable) {
                    logger.warn("Unable to start RPC connection", ex)
                    connectionFuture.completeExceptionally(ex)
                }
                // Do not wait for close, release the thread as soon as
                connectionStarterThread.shutdown()
            }
        }
        return connectionFuture
    }

    /**
     * Stops the client and closes [RPCConnection] if it has been previously established
     */
    fun stop() = close()

    override fun close() {
        connectionStarterThread.shutdownNow()
        // Close connection if future is ready and was successful
        if(connectionFuture.isDone && !connectionFuture.isCompletedExceptionally) {
            connectionFuture.get().notifyServerAndClose()
        }
    }
}