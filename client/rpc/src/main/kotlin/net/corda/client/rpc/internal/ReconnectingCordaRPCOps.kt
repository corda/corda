package net.corda.client.rpc.internal

import net.corda.client.rpc.*
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.div
import net.corda.core.internal.times
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowHandle
import net.corda.core.utilities.*
import net.corda.nodeapi.exceptions.RejectedCommandException
import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.apache.activemq.artemis.api.core.ActiveMQUnBlockedException
import rx.Observable
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Wrapper over [CordaRPCOps] that handles exceptions when the node or the connection to the node fail.
 *
 * All operations are retried on failure, except flow start operations that die before receiving a valid [FlowHandle], in which case a [CouldNotStartFlowException] is thrown.
 *
 * When calling methods that return a [DataFeed] like [CordaRPCOps.vaultTrackBy], the returned [DataFeed.updates] object will no longer
 * be a usable [rx.Observable] but an instance of [ReconnectingObservable].
 * The caller has to explicitly cast to [ReconnectingObservable] and call [ReconnectingObservable.subscribe]. If used as an [rx.Observable] it will just fail.
 * The returned [DataFeed.snapshot] is the snapshot as it was when the feed was first retrieved.
 *
 * Note: There is no guarantee that observations will not be lost.
 *
 * *This class is not a stable API. Any project that wants to use it, must copy and paste it.*
 */
class ReconnectingCordaRPCOps private constructor(
        private val reconnectingRPCConnection: ReconnectingRPCConnection,
        private val observersPool: ExecutorService,
        private val userPool: Boolean
) : AutoCloseable, CordaRPCOps by proxy(reconnectingRPCConnection, observersPool) {

    // Constructors that mirror CordaRPCClient.
    constructor(
            nodeHostAndPort: NetworkHostAndPort,
            username: String,
            password: String,
            sslConfiguration: ClientRpcSslOptions? = null,
            classLoader: ClassLoader? = null,
            observersPool: ExecutorService? = null
    ) : this(
            ReconnectingRPCConnection(listOf(nodeHostAndPort), username, password, sslConfiguration, classLoader),
            observersPool ?: Executors.newCachedThreadPool(),
            observersPool != null)

    constructor(
            nodeHostAndPorts: List<NetworkHostAndPort>,
            username: String,
            password: String,
            sslConfiguration: ClientRpcSslOptions? = null,
            classLoader: ClassLoader? = null,
            observersPool: ExecutorService? = null
    ) : this(
            ReconnectingRPCConnection(nodeHostAndPorts, username, password, sslConfiguration, classLoader),
            observersPool ?: Executors.newCachedThreadPool(),
            observersPool != null)

    private companion object {
        private val log = contextLogger()
        private fun proxy(reconnectingRPCConnection: ReconnectingRPCConnection, observersPool: ExecutorService): CordaRPCOps {
            return Proxy.newProxyInstance(
                    this::class.java.classLoader,
                    arrayOf(CordaRPCOps::class.java),
                    ErrorInterceptingHandler(reconnectingRPCConnection, observersPool)) as CordaRPCOps
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
    fun runFlowWithLogicalRetry(runFlow: (CordaRPCOps) -> StateMachineRunId, hasFlowStarted: (CordaRPCOps) -> Boolean, onFlowConfirmed: () -> Unit = {}, timeout: Duration = 4.seconds) {
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
     * This function is similar to [runFlowWithLogicalRetry] but is blocking and it returns the result of the flow.
     *
     * [runFlow] - starts a flow and returns the [FlowHandle].
     * [hasFlowCompleted] - Runs a vault query and is able to recreate the result of the flow.
     */
    fun <T> runFlowAndReturnResultWithLogicalRetry(runFlow: (CordaRPCOps) -> FlowHandle<T>, hasFlowCompleted: (CordaRPCOps) -> T?, timeout: Duration = 4.seconds): T {
        return try {
            runFlow(this).returnValue.get()
        } catch (e: CouldNotStartFlowException) {
            log.error("Couldn't start flow: ${e.message}")
            Thread.sleep(timeout.toMillis())
            hasFlowCompleted(this) ?: runFlowAndReturnResultWithLogicalRetry(runFlow, hasFlowCompleted, timeout)
        }
    }

    /**
     * Helper class useful for reconnecting to a Node.
     */
    internal data class ReconnectingRPCConnection(
            val nodeHostAndPorts: List<NetworkHostAndPort>,
            val username: String,
            val password: String,
            val sslConfiguration: ClientRpcSslOptions? = null,
            val classLoader: ClassLoader?
    ) : RPCConnection<CordaRPCOps> {
        private var currentRPCConnection: CordaRPCConnection? = null

        enum class CurrentState {
            UNCONNECTED, CONNECTED, CONNECTING, CLOSED, DIED
        }

        private var currentState = CurrentState.UNCONNECTED

        init {
            current
        }

        private val current: CordaRPCConnection
            @Synchronized get() = when (currentState) {
                CurrentState.UNCONNECTED -> connect()
                CurrentState.CONNECTED -> currentRPCConnection!!
                CurrentState.CLOSED -> throw IllegalArgumentException("The ReconnectingRPCConnection has been closed.")
                CurrentState.CONNECTING, CurrentState.DIED -> throw IllegalArgumentException("Illegal state: $currentState ")
            }

        /**
         * Called on external error.
         * Will block until the connection is established again.
         */
        @Synchronized
        fun error(e: Throwable) {
            currentState = CurrentState.DIED
            //TODO - handle error cases
            log.error("Reconnecting to ${this.nodeHostAndPorts} due to error: ${e.message}")
            connect()
        }

        @Synchronized
        private fun connect(): CordaRPCConnection {
            currentState = CurrentState.CONNECTING
            currentRPCConnection = establishConnectionWithRetry()
            currentState = CurrentState.CONNECTED
            return currentRPCConnection!!
        }

        private tailrec fun establishConnectionWithRetry(retryInterval: Duration = 1.seconds, currentAuthenticationRetries: Int = 0): CordaRPCConnection {
            var _currentAuthenticationRetries = currentAuthenticationRetries
            log.info("Connecting to: $nodeHostAndPorts")
            try {
                return CordaRPCClient(
                        nodeHostAndPorts, CordaRPCClientConfiguration(connectionMaxRetryInterval = retryInterval), sslConfiguration, classLoader
                ).start(username, password).also {
                    // Check connection is truly operational before returning it.
                    require(it.proxy.nodeInfo().legalIdentitiesAndCerts.isNotEmpty()) {
                        "Could not establish connection to ${nodeHostAndPorts}."
                    }
                    log.debug { "Connection successfully established with: ${nodeHostAndPorts}" }
                }
            } catch (ex: Exception) {
                when (ex) {
                    is ActiveMQSecurityException -> {
                        // Happens when incorrect credentials provided.
                        // It can happen at startup as well when the credentials are correct.
                        if (_currentAuthenticationRetries++ > 3) {
                            log.error("Failed to login to node.", ex)
                            throw ex
                        }
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
            return establishConnectionWithRetry((retryInterval * 10) / 9, _currentAuthenticationRetries)
        }

        override val proxy: CordaRPCOps
            get() = current.proxy

        override val serverProtocolVersion
            get() = current.serverProtocolVersion

        @Synchronized
        override fun notifyServerAndClose() {
            currentState = CurrentState.CLOSED
            currentRPCConnection?.notifyServerAndClose()
        }

        @Synchronized
        override fun forceClose() {
            currentState = CurrentState.CLOSED
            currentRPCConnection?.forceClose()
        }

        @Synchronized
        override fun close() {
            currentState = CurrentState.CLOSED
            currentRPCConnection?.close()
        }
    }

    internal class ReconnectingObservableImpl<T> internal constructor(
            val reconnectingRPCConnection: ReconnectingRPCConnection,
            val observersPool: ExecutorService,
            val initial: DataFeed<*, T>,
            val createDataFeed: () -> DataFeed<*, T>
    ) : Observable<T>(null), ReconnectingObservable<T> {

        private var initialStartWith: Iterable<T>? = null
        private fun _subscribeWithReconnect(observerHandle: ObserverHandle, onNext: (T) -> Unit, onStop: () -> Unit, onDisconnect: () -> Unit, onReconnect: () -> Unit, startWithValues: Iterable<T>? = null) {
            var subscriptionError: Throwable?
            try {
                val subscription = initial.updates.let { if (startWithValues != null) it.startWith(startWithValues) else it }
                        .subscribe(onNext, observerHandle::fail, observerHandle::stop)
                subscriptionError = observerHandle.await()
                subscription.unsubscribe()
            } catch (e: Exception) {
                log.error("Failed to register subscriber .", e)
                subscriptionError = e
            }

            // In case there was no exception the observer has finished gracefully.
            if (subscriptionError == null) {
                onStop()
                return
            }

            onDisconnect()
            // Only continue if the subscription failed.
            reconnectingRPCConnection.error(subscriptionError)
            log.debug { "Recreating data feed." }

            val newObservable = createDataFeed().updates as ReconnectingObservableImpl<T>
            onReconnect()
            return newObservable._subscribeWithReconnect(observerHandle, onNext, onStop, onDisconnect, onReconnect)
        }

        override fun subscribe(onNext: (T) -> Unit, onStop: () -> Unit, onDisconnect: () -> Unit, onReconnect: () -> Unit): ObserverHandle {
            val observerNotifier = ObserverHandle()
            // TODO - change the establish connection method to be non-blocking
            observersPool.execute {
                _subscribeWithReconnect(observerNotifier, onNext, onStop, onDisconnect, onReconnect, initialStartWith)
            }
            return observerNotifier
        }

        override fun startWithValues(values: Iterable<T>): ReconnectingObservable<T> {
            initialStartWith = values
            return this
        }
    }

    private class ErrorInterceptingHandler(val reconnectingRPCConnection: ReconnectingRPCConnection, val observersPool: ExecutorService) : InvocationHandler {
        private fun Method.isStartFlow() = name.startsWith("startFlow") || name.startsWith("startTrackedFlow")

        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            val result: Any? = try {
                log.debug { "Invoking RPC $method..." }
                method.invoke(reconnectingRPCConnection.proxy, *(args ?: emptyArray())).also {
                    log.debug { "RPC $method invoked successfully." }
                }
            } catch (e: InvocationTargetException) {
                fun retry() = if (method.isStartFlow()) {
                    // Don't retry flows
                    throw CouldNotStartFlowException(e.targetException)
                } else {
                    this.invoke(proxy, method, args)
                }

                when (e.targetException) {
                    is RejectedCommandException -> {
                        log.error("Node is being shutdown. Operation ${method.name} rejected. Retrying when node is up...", e)
                        reconnectingRPCConnection.error(e)
                        this.invoke(proxy, method, args)
                    }
                    is ConnectionFailureException -> {
                        log.error("Failed to perform operation ${method.name}. Connection dropped. Retrying....", e)
                        reconnectingRPCConnection.error(e)
                        retry()
                    }
                    is RPCException -> {
                        log.error("Failed to perform operation ${method.name}. RPCException. Retrying....", e)
                        reconnectingRPCConnection.error(e)
                        Thread.sleep(1000) // TODO - explain why this sleep is necessary
                        retry()
                    }
                    else -> {
                        log.error("Failed to perform operation ${method.name}. Unknown error. Retrying....", e)
                        reconnectingRPCConnection.error(e)
                        retry()
                    }
                }
            }

            return when (method.returnType) {
                DataFeed::class.java -> {
                    // Intercept the data feed methods and returned a ReconnectingObservable instance
                    val initialFeed: DataFeed<Any, Any?> = uncheckedCast(result)
                    val observable = ReconnectingObservableImpl(reconnectingRPCConnection, observersPool, initialFeed) {
                        // This handles reconnecting and creates new feeds.
                        uncheckedCast(this.invoke(reconnectingRPCConnection.proxy, method, args))
                    }
                    initialFeed.copy(updates = observable)
                }
                // TODO - add handlers for Observable return types.
                else -> result
            }
        }
    }

    override fun close() {
        if (!userPool) observersPool.shutdown()
        retryFlowsPool.shutdown()
        reconnectingRPCConnection.forceClose()
    }
}

/**
 * Returned as the `updates` field when calling methods that return a [DataFeed] on the [ReconnectingCordaRPCOps].
 *
 * TODO - provide a logical function to know how to retrieve missing events that happened during disconnects.
 */
interface ReconnectingObservable<T> {
    fun subscribe(onNext: (T) -> Unit): ObserverHandle = subscribe(onNext, {}, {}, {})
    fun subscribe(onNext: (T) -> Unit, onStop: () -> Unit, onDisconnect: () -> Unit, onReconnect: () -> Unit): ObserverHandle
    fun startWithValues(values: Iterable<T>): ReconnectingObservable<T>
}

/**
 * Utility to externally control a subscribed observer.
 */
class ObserverHandle {
    private val terminated = LinkedBlockingQueue<Optional<Throwable>>(1)

    fun stop() = terminated.put(Optional.empty())
    internal fun fail(e: Throwable) = terminated.put(Optional.of(e))

    /**
     * Returns null if the observation ended successfully.
     */
    internal fun await(duration: Duration = 60.minutes): Throwable? = terminated.poll(duration.seconds, TimeUnit.SECONDS).orElse(null)
}

/**
 * Thrown when a flow start command died before receiving a [net.corda.core.messaging.FlowHandle].
 * On catching this exception, the typical behaviour is to run a "logical retry", meaning only retry the flow if the expected outcome did not occur.
 */
class CouldNotStartFlowException(cause: Throwable? = null) : RPCException("Could not start flow as connection failed", cause)

/**
 * Mainly for Kotlin users.
 */
fun <T> Observable<T>.asReconnecting(): ReconnectingObservable<T> = uncheckedCast(this)

fun <T> Observable<T>.asReconnectingWithInitialValues(values: Iterable<T>): ReconnectingObservable<T> = asReconnecting().startWithValues(values)
