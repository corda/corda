package net.corda.client.rpc.internal

import net.corda.client.rpc.*
import net.corda.client.rpc.internal.ReconnectingCordaRPCOps.ReconnectingRPCConnection.CurrentState.*
import net.corda.client.rpc.reconnect.CouldNotStartFlowException
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.div
import net.corda.core.internal.messaging.InternalCordaRPCOps
import net.corda.core.internal.times
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowHandle
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.seconds
import net.corda.nodeapi.exceptions.RejectedCommandException
import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.apache.activemq.artemis.api.core.ActiveMQUnBlockedException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Wrapper over [CordaRPCOps] that handles exceptions when the node or the connection to the node fail.
 *
 * All operations are retried on failure, except flow start operations that die before receiving a valid [FlowHandle], in which case a
 * [CouldNotStartFlowException] is thrown.
 *
 * When calling methods that return a [DataFeed] like [CordaRPCOps.vaultTrackBy], the returned [DataFeed.updates] object will no longer
 * be a usable [rx.Observable] but an instance of [ReconnectingObservable].
 * The caller has to explicitly cast to [ReconnectingObservable] and call [ReconnectingObservable.subscribe]. If used as an [rx.Observable]
 * it will just fail.
 * The returned [DataFeed.snapshot] is the snapshot as it was when the feed was first retrieved.
 *
 * Note: There is no guarantee that observations will not be lost.
 *
 * *This class is not a stable API. Any project that wants to use it, must copy and paste it.*
 */
// TODO The executor service is not needed. All we need is a single thread that deals with reconnecting and onto which
//  ReconnectingObservables and other things can attach themselves as listeners for reconnect events.
class ReconnectingCordaRPCOps private constructor(
        val reconnectingRPCConnection: ReconnectingRPCConnection
) : InternalCordaRPCOps by proxy(reconnectingRPCConnection) {
    constructor(
            nodeHostAndPorts: List<NetworkHostAndPort>,
            username: String,
            password: String,
            rpcConfiguration: CordaRPCClientConfiguration,
            gracefulReconnect: GracefulReconnect? = null,
            sslConfiguration: ClientRpcSslOptions? = null,
            classLoader: ClassLoader? = null,
            observersPool: ExecutorService
    ) : this(ReconnectingRPCConnection(
            nodeHostAndPorts,
            username,
            password,
            rpcConfiguration,
            sslConfiguration,
            classLoader,
            gracefulReconnect,
            observersPool))
    private companion object {
        private val log = contextLogger()
        private fun proxy(reconnectingRPCConnection: ReconnectingRPCConnection): InternalCordaRPCOps {
            return Proxy.newProxyInstance(
                    this::class.java.classLoader,
                    arrayOf(InternalCordaRPCOps::class.java),
                    ErrorInterceptingHandler(reconnectingRPCConnection)) as InternalCordaRPCOps
        }
    }
    private val retryFlowsPool = Executors.newScheduledThreadPool(1)
    /**
     * This function runs a flow and retries until it completes successfully.
     *
     * [runFlow] is a function that starts a flow.
     * [hasFlowStarted] is a function that checks if the flow has actually completed by checking some side-effect, for example the vault.
     * [onFlowConfirmed] Callback when the flow is confirmed.
     * [timeout] Indicative timeout to wait until the flow would create the side-effect. Should be increased if the flow is slow. Note that
     * this timeout is calculated after the rpc client has reconnected to the node.
     *
     * Note that this method does not guarantee 100% that the flow will not be started twice.
     */
    fun runFlowWithLogicalRetry(
            runFlow: (CordaRPCOps) -> StateMachineRunId,
            hasFlowStarted: (CordaRPCOps) -> Boolean,
            onFlowConfirmed: () -> Unit = {},
            timeout: Duration = 4.seconds
    ) {
        try {
            runFlow(this)
            onFlowConfirmed()
        } catch (e: CouldNotStartFlowException) {
            log.error("Couldn't start flow: ${e.message}")
            retryFlowsPool.schedule(
                    {
                        if (!hasFlowStarted(this)) {
                            runFlowWithLogicalRetry(runFlow, hasFlowStarted, onFlowConfirmed, timeout)
                        } else {
                            onFlowConfirmed()
                        }
                    },
                    timeout.seconds, TimeUnit.SECONDS
            )
        }
    }
    /**
     * Helper class useful for reconnecting to a Node.
     */
    data class ReconnectingRPCConnection(
            val nodeHostAndPorts: List<NetworkHostAndPort>,
            val username: String,
            val password: String,
            val rpcConfiguration: CordaRPCClientConfiguration,
            val sslConfiguration: ClientRpcSslOptions? = null,
            val classLoader: ClassLoader?,
            val gracefulReconnect: GracefulReconnect? = null,
            val observersPool: ExecutorService
    ) : RPCConnection<CordaRPCOps> {
        private var currentRPCConnection: CordaRPCConnection? = null
        enum class CurrentState {
            UNCONNECTED, CONNECTED, CONNECTING, CLOSED, DIED
        }

        private var currentState = UNCONNECTED

        init {
            current
        }
        private val current: CordaRPCConnection
            @Synchronized get() = when (currentState) {
                UNCONNECTED -> connect()
                CONNECTED -> currentRPCConnection!!
                CLOSED -> throw IllegalArgumentException("The ReconnectingRPCConnection has been closed.")
                CONNECTING, DIED -> throw IllegalArgumentException("Illegal state: $currentState ")
            }

        @Synchronized
        private fun doReconnect(e: Throwable, previousConnection: CordaRPCConnection?) {
            if (previousConnection != currentRPCConnection) {
                // We've already done this, skip
                return
            }
            // First one to get here gets to do all the reconnect logic, including calling onDisconnect and onReconnect. This makes sure
            // that they're only called once per reconnect.
            currentState = DIED
            gracefulReconnect?.onDisconnect?.invoke()
            //TODO - handle error cases
            log.error("Reconnecting to ${this.nodeHostAndPorts} due to error: ${e.message}")
            log.debug("", e)
            connect()
            previousConnection?.forceClose()
            gracefulReconnect?.onReconnect?.invoke()
        }
        /**
         * Called on external error.
         * Will block until the connection is established again.
         */
        fun reconnectOnError(e: Throwable) {
            val previousConnection = currentRPCConnection
            doReconnect(e, previousConnection)
        }
        @Synchronized
        private fun connect(): CordaRPCConnection {
            currentState = CONNECTING
            currentRPCConnection = establishConnectionWithRetry()
            currentState = CONNECTED
            return currentRPCConnection!!
        }

        private tailrec fun establishConnectionWithRetry(
                retryInterval: Duration = 1.seconds,
                roundRobinIndex: Int = 0
        ): CordaRPCConnection {
            val attemptedAddress = nodeHostAndPorts[roundRobinIndex]
            log.info("Connecting to: $attemptedAddress")
            try {
                return CordaRPCClient(
                        attemptedAddress,
                        rpcConfiguration.copy(connectionMaxRetryInterval = retryInterval, maxReconnectAttempts = 1),
                        sslConfiguration,
                        classLoader
                ).start(username, password).also {
                    // Check connection is truly operational before returning it.
                    require(it.proxy.nodeInfo().legalIdentitiesAndCerts.isNotEmpty()) {
                        "Could not establish connection to $attemptedAddress."
                    }
                    log.debug { "Connection successfully established with: $attemptedAddress" }
                }
            } catch (ex: Exception) {
                when (ex) {
                    is ActiveMQSecurityException -> {
                        log.error("Failed to login to node.", ex)
                        throw ex
                    }
                    is RPCException -> {
                        // Deliberately not logging full stack trace as it will be full of internal stacktraces.
                        log.debug { "Exception upon establishing connection: ${ex.message}" }
                    }
                    is ActiveMQConnectionTimedOutException -> {
                        // Deliberately not logging full stack trace as it will be full of internal stacktraces.
                        log.debug { "Exception upon establishing connection: ${ex.message}" }
                    }
                    is ActiveMQUnBlockedException -> {
                        // Deliberately not logging full stack trace as it will be full of internal stacktraces.
                        log.debug { "Exception upon establishing connection: ${ex.message}" }
                    }
                    else -> {
                        log.warn("Unknown exception upon establishing connection.", ex)
                    }
                }
            }
            // Could not connect this time round - pause before giving another try.
            Thread.sleep(retryInterval.toMillis())
            // TODO - make the exponential retry factor configurable.
            val nextRoundRobinIndex = (roundRobinIndex + 1) % nodeHostAndPorts.size
            return establishConnectionWithRetry((retryInterval * 10) / 9, nextRoundRobinIndex)
        }
        override val proxy: CordaRPCOps
            get() = current.proxy
        override val serverProtocolVersion
            get() = current.serverProtocolVersion
        @Synchronized
        override fun notifyServerAndClose() {
            currentState = CLOSED
            currentRPCConnection?.notifyServerAndClose()
        }
        @Synchronized
        override fun forceClose() {
            currentState = CLOSED
            currentRPCConnection?.forceClose()
        }
        @Synchronized
        override fun close() {
            currentState = CLOSED
            currentRPCConnection?.close()
        }
    }
    private class ErrorInterceptingHandler(val reconnectingRPCConnection: ReconnectingRPCConnection) : InvocationHandler {
        private fun Method.isStartFlow() = name.startsWith("startFlow") || name.startsWith("startTrackedFlow")

        private fun checkIfIsStartFlow(method: Method, e: InvocationTargetException) {
            if (method.isStartFlow()) {
                // Don't retry flows
                throw CouldNotStartFlowException(e.targetException)
            }
        }

        private tailrec fun doInvoke(method: Method, args: Array<out Any>?): Any? {
            // will stop recursing when [method.invoke] succeeds
            try {
                log.debug { "Invoking RPC $method..." }
                return method.invoke(reconnectingRPCConnection.proxy, *(args ?: emptyArray())).also {
                    log.debug { "RPC $method invoked successfully." }
                }
            } catch (e: InvocationTargetException) {
                when (e.targetException) {
                    is RejectedCommandException -> {
                        log.error("Node is being shutdown. Operation ${method.name} rejected. Retrying when node is up...", e)
                        reconnectingRPCConnection.reconnectOnError(e)
                    }
                    is ConnectionFailureException -> {
                        if (!reconnectingRPCConnection.proxy.isWaitingForShutdown()) {
                            log.error("Failed to perform operation ${method.name}. Connection dropped. Retrying....", e)
                            reconnectingRPCConnection.reconnectOnError(e)
                        }
                        checkIfIsStartFlow(method, e)
                    }
                    is RPCException -> {
                        log.error("Failed to perform operation ${method.name}. RPCException. Retrying....", e)
                        reconnectingRPCConnection.reconnectOnError(e)
                        Thread.sleep(1000) // TODO - explain why this sleep is necessary
                        checkIfIsStartFlow(method, e)
                    }
                    else -> {
                        log.error("Failed to perform operation ${method.name}. Unknown error. Retrying....", e)
                        reconnectingRPCConnection.reconnectOnError(e)
                        checkIfIsStartFlow(method, e)
                    }
                }
            }
            return doInvoke(method, args)
        }

        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            return when (method.returnType) {
                DataFeed::class.java -> {
                    // Intercept the data feed methods and return a ReconnectingObservable instance
                    val initialFeed: DataFeed<Any, Any?> = uncheckedCast(doInvoke(method, args))
                    val observable = ReconnectingObservable(reconnectingRPCConnection, initialFeed) {
                        // This handles reconnecting and creates new feeds.
                        uncheckedCast(this.invoke(reconnectingRPCConnection.proxy, method, args))
                    }
                    initialFeed.copy(updates = observable)
                }
                // TODO - add handlers for Observable return types.
                else -> doInvoke(method, args)
            }
        }
    }

    fun close() {
        retryFlowsPool.shutdown()
        reconnectingRPCConnection.forceClose()
    }
}
