package net.corda.client.rpc.internal

import co.paralleluniverse.common.util.SameThreadExecutor
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.RemovalListener
import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.RPCException
import net.corda.client.rpc.RPCSinceVersion
import net.corda.client.rpc.internal.serialization.amqp.RpcClientObservableDeSerializer
import net.corda.core.context.Actor
import net.corda.core.context.Trace
import net.corda.core.context.Trace.InvocationId
import net.corda.core.internal.*
import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.serialize
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.DeduplicationChecker
import org.apache.activemq.artemis.api.core.ActiveMQException
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.*
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE
import rx.Notification
import rx.Observable
import rx.subjects.UnicastSubject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.jvm.javaMethod

/**
 * This class provides a proxy implementation of an RPC interface for RPC clients. It translates API calls to lower-level
 * RPC protocol messages. For this protocol see [RPCApi].
 *
 * When a method is called on the interface the arguments are serialised and the request is forwarded to the server. The
 * server then executes the code that implements the RPC and sends a reply.
 *
 * An RPC reply may contain [Observable]s, which are serialised simply as unique IDs. On the client side we create a
 * [UnicastSubject] for each such ID. Subsequently the server may send observations attached to this ID, which are
 * forwarded to the [UnicastSubject]. Note that the observations themselves may contain further [Observable]s, which are
 * handled in the same way.
 *
 * To do the above we take advantage of Kryo's datastructure traversal. When the client is deserialising a message from
 * the server that may contain Observables it is supplied with an [ObservableContext] that exposes the map used to demux
 * the observations. When an [Observable] is encountered during traversal a new [UnicastSubject] is added to the map and
 * we carry on. Each observation later contains the corresponding Observable ID, and we just forward that to the
 * associated [UnicastSubject].
 *
 * The client may signal that it no longer consumes a particular [Observable]. This may be done explicitly by
 * unsubscribing from the [Observable], or if the [Observable] is garbage collected the client will eventually
 * automatically signal the server. This is done using a cache that holds weak references to the [UnicastSubject]s.
 * The cleanup happens in batches using a dedicated reaper, scheduled on [reaperExecutor].
 *
 * The client will attempt to failover in case the server become unreachable. Depending on the [ServerLocataor] instance
 * passed in the constructor, failover is either handle at Artemis level or client level. If only one transport
 * was used to create the [ServerLocator], failover is handled by Artemis (retrying based on [CordaRPCClientConfiguration].
 * If a list of transport configurations was used, failover is handled locally. Artemis is able to do it, however the
 * brokers on server side need to be configured in HA mode and the [ServerLocator] needs to be created with HA as well.
 */
class RPCClientProxyHandler(
        private val rpcConfiguration: CordaRPCClientConfiguration,
        private val rpcUsername: String,
        private val rpcPassword: String,
        private val serverLocator: ServerLocator,
        private val clientAddress: SimpleString,
        private val rpcOpsClass: Class<out RPCOps>,
        serializationContext: SerializationContext,
        private val sessionId: Trace.SessionId,
        private val externalTrace: Trace?,
        private val impersonatedActor: Actor?
) : InvocationHandler {

    private enum class State {
        UNSTARTED,
        SERVER_VERSION_NOT_SET,
        STARTED,
        FINISHED
    }

    private val lifeCycle = LifeCycle(State.UNSTARTED)

    private companion object {
        private val log = contextLogger()
        // To check whether toString() is being invoked
        val toStringMethod: Method = Object::toString.javaMethod!!

        private fun addRpcCallSiteToThrowable(throwable: Throwable, callSite: Throwable) {
            var currentThrowable = throwable
            while (true) {
                val cause = currentThrowable.cause
                if (cause == null) {
                    currentThrowable.initCause(callSite)
                    break
                } else {
                    currentThrowable = cause
                }
            }
        }
    }

    // Used for reaping
    private var reaperExecutor: ScheduledExecutorService? = null
    // Used for sending
    private var sendExecutor: ExecutorService? = null

    // A sticky pool for running Observable.onNext()s. We need the stickiness to preserve the observation ordering.
    private val observationExecutorThreadFactory = ThreadFactoryBuilder().setNameFormat("rpc-client-observation-pool-%d").setDaemon(true).build()
    private val observationExecutorPool = LazyStickyPool(rpcConfiguration.observationExecutorPoolSize) {
        Executors.newFixedThreadPool(1, observationExecutorThreadFactory)
    }

    // Holds the RPC reply futures.
    private val rpcReplyMap = RpcReplyMap()
    // Optionally holds RPC call site stack traces to be shown on errors/warnings.
    private val callSiteMap = if (rpcConfiguration.trackRpcCallSites) CallSiteMap() else null
    // Holds the Observables and a reference store to keep Observables alive when subscribed to.
    private val observableContext = ObservableContext(
            callSiteMap = callSiteMap,
            observableMap = createRpcObservableMap(),
            hardReferenceStore = Collections.synchronizedSet(mutableSetOf<Observable<*>>())
    )
    // Holds a reference to the scheduled reaper.
    private var reaperScheduledFuture: ScheduledFuture<*>? = null
    // The protocol version of the server, to be initialised to the value of [RPCOps.protocolVersion]
    private var serverProtocolVersion: Int? = null

    // Stores the Observable IDs that are already removed from the map but are not yet sent to the server.
    private val observablesToReap = ThreadBox(object {
        var observables = ArrayList<InvocationId>()
    })
    private val serializationContextWithObservableContext = RpcClientObservableDeSerializer.createContext(serializationContext, observableContext)

    private fun createRpcObservableMap(): RpcObservableMap {
        val onObservableRemove = RemovalListener<InvocationId, UnicastSubject<Notification<*>>> { key, _, cause ->
            val observableId = key!!
            val rpcCallSite = callSiteMap?.remove(observableId)
            if (cause == RemovalCause.COLLECTED) {
                log.warn(listOf(
                        "A hot observable returned from an RPC was never subscribed to.",
                        "This wastes server-side resources because it was queueing observations for retrieval.",
                        "It is being closed now, but please adjust your code to call .notUsed() on the observable",
                        "to close it explicitly. (Java users: subscribe to it then unsubscribe). This warning",
                        "will appear less frequently in future versions of the platform and you can ignore it",
                        "if you want to.").joinToString(" "), rpcCallSite)
            }
            observablesToReap.locked { observables.add(observableId) }
        }
        return Caffeine.newBuilder().
                weakValues().removalListener(onObservableRemove).executor(SameThreadExecutor.getExecutor()).buildNamed("RpcClientProxyHandler_rpcObservable")
    }

    private var sessionFactory: ClientSessionFactory? = null
    private var producerSession: ClientSession? = null
    private var consumerSession: ClientSession? = null
    private var rpcProducer: ClientProducer? = null
    private var rpcConsumer: ClientConsumer? = null

    private val deduplicationChecker = DeduplicationChecker(rpcConfiguration.deduplicationCacheExpiry)
    private val deduplicationSequenceNumber = AtomicLong(0)

    private val sendingEnabled = AtomicBoolean(true)
    // Used to interrupt failover thread (i.e. client is closed while failing over).
    private var haFailoverThread: Thread? = null

    /**
     * Start the client. This creates the per-client queue, starts the consumer session and the reaper.
     */
    fun start() {
        lifeCycle.requireState(State.UNSTARTED)
        reaperExecutor = Executors.newScheduledThreadPool(
                1,
                ThreadFactoryBuilder().setNameFormat("rpc-client-reaper-%d").setDaemon(true).build()
        )
        sendExecutor = Executors.newSingleThreadExecutor(
                ThreadFactoryBuilder().setNameFormat("rpc-client-sender-%d").setDaemon(true).build()
        )
        reaperScheduledFuture = reaperExecutor!!.scheduleAtFixedRate(
                this::reapObservablesAndNotify,
                rpcConfiguration.reapInterval.toMillis(),
                rpcConfiguration.reapInterval.toMillis(),
                TimeUnit.MILLISECONDS
        )
        // Create a session factory using the first available server. If more than one transport configuration was
        // used when creating the server locator, every one will be tried during failover. The locator will round-robin
        // through the available transport configurations with the starting position being generated randomly.
        // If there's only one available, that one will be retried continuously as configured in rpcConfiguration.
        // There is no failover on first attempt, meaning that if a connection cannot be established, the serverLocator
        // will try another transport if it exists or throw an exception otherwise.
        try {
            sessionFactory = serverLocator.createSessionFactory()
        } catch (e: ActiveMQNotConnectedException) {
            throw (RPCException("Cannot connect to server(s). Tried with all available servers.", e))
        }
        // Depending on how the client is constructed, connection failure is treated differently
        if (serverLocator.staticTransportConfigurations.size == 1) {
            sessionFactory!!.addFailoverListener(this::failoverHandler)
        } else {
            sessionFactory!!.addFailoverListener(this::haFailoverHandler)
        }
        initSessions()
        lifeCycle.transition(State.UNSTARTED, State.SERVER_VERSION_NOT_SET)
        startSessions()
    }

    // This is the general function that transforms a client side RPC to internal Artemis messages.
    override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
        lifeCycle.requireState { it == State.STARTED || it == State.SERVER_VERSION_NOT_SET }
        checkProtocolVersion(method)
        if (method == toStringMethod) {
            return "Client RPC proxy for $rpcOpsClass"
        }
        if (consumerSession!!.isClosed) {
            throw RPCException("RPC Proxy is closed")
        }

        if (!sendingEnabled.get())
            throw RPCException("RPC server is not available.")

        val replyId = InvocationId.newInstance()
        callSiteMap?.set(replyId, Throwable("<Call site of root RPC '${method.name}'>"))
        try {
            val serialisedArguments = (arguments?.toList() ?: emptyList()).serialize(context = serializationContextWithObservableContext)
            val request = RPCApi.ClientToServer.RpcRequest(
                    clientAddress,
                    method.name,
                    serialisedArguments,
                    replyId,
                    sessionId,
                    externalTrace,
                    impersonatedActor
            )
            val replyFuture = SettableFuture.create<Any>()
            require(rpcReplyMap.put(replyId, replyFuture) == null) {
                "Generated several RPC requests with same ID $replyId"
            }

            sendMessage(request)
            return replyFuture.getOrThrow()
        } catch (e: RuntimeException) {
            // Already an unchecked exception, so just rethrow it
            throw e
        } catch (e: Exception) {
            // This must be a checked exception, so wrap it
            throw RPCException(e.message ?: "", e)
        } finally {
            callSiteMap?.remove(replyId)
        }
    }

    private fun sendMessage(message: RPCApi.ClientToServer) {
        val artemisMessage = producerSession!!.createMessage(false)
        message.writeToClientMessage(artemisMessage)
        sendExecutor!!.submit {
            artemisMessage.putLongProperty(RPCApi.DEDUPLICATION_SEQUENCE_NUMBER_FIELD_NAME, deduplicationSequenceNumber.getAndIncrement())
            log.debug { "-> RPC -> $message" }
            rpcProducer!!.send(artemisMessage)
        }
    }

    // The handler for Artemis messages.
    private fun artemisMessageHandler(message: ClientMessage) {
        fun completeExceptionally(id: InvocationId, e: Throwable, future: SettableFuture<Any?>?) {
            val rpcCallSite: Throwable? = callSiteMap?.get(id)
            if (rpcCallSite != null) addRpcCallSiteToThrowable(e, rpcCallSite)
            future?.setException(e.cause ?: e)
        }

        try {
            // Deserialize the reply from the server, both the wrapping metadata and the actual body of the return value.
            val serverToClient: RPCApi.ServerToClient = try {
                RPCApi.ServerToClient.fromClientMessage(serializationContextWithObservableContext, message)
            } catch (e: RPCApi.ServerToClient.FailedToDeserializeReply) {
                // Might happen if something goes wrong during mapping the response to classes, evolution, class synthesis etc.
                log.error("Failed to deserialize RPC body", e)
                completeExceptionally(e.id, e, rpcReplyMap.remove(e.id))
                return
            }
            val deduplicationSequenceNumber = message.getLongProperty(RPCApi.DEDUPLICATION_SEQUENCE_NUMBER_FIELD_NAME)
            if (deduplicationChecker.checkDuplicateMessageId(serverToClient.deduplicationIdentity, deduplicationSequenceNumber)) {
                log.info("Message duplication detected, discarding message")
                return
            }
            log.debug { "Got message from RPC server $serverToClient" }
            when (serverToClient) {
                is RPCApi.ServerToClient.RpcReply -> {
                    val replyFuture = rpcReplyMap.remove(serverToClient.id)
                    if (replyFuture == null) {
                        log.error("RPC reply arrived to unknown RPC ID ${serverToClient.id}, this indicates an internal RPC error.")
                    } else {
                        val result: Try<Any?> = serverToClient.result
                        when (result) {
                            is Try.Success -> replyFuture.set(result.value)
                            is Try.Failure -> {
                                completeExceptionally(serverToClient.id, result.exception, replyFuture)
                            }
                        }
                    }
                }
                is RPCApi.ServerToClient.Observation -> {
                    val observable: UnicastSubject<Notification<*>>? = observableContext.observableMap.getIfPresent(serverToClient.id)
                    if (observable == null) {
                        log.debug("Observation ${serverToClient.content} arrived to unknown Observable with ID ${serverToClient.id}. " +
                                "This may be due to an observation arriving before the server was " +
                                "notified of observable shutdown")
                    } else {
                        // We schedule the onNext() on an executor sticky-pooled based on the Observable ID.
                        observationExecutorPool.run(serverToClient.id) { executor ->
                            executor.submit {
                                val content = serverToClient.content
                                if (content.isOnCompleted || content.isOnError) {
                                    observableContext.observableMap.invalidate(serverToClient.id)
                                }
                                // Add call site information on error
                                if (content.isOnError) {
                                    val rpcCallSite = callSiteMap?.get(serverToClient.id)
                                    if (rpcCallSite != null) addRpcCallSiteToThrowable(content.throwable, rpcCallSite)
                                }
                                observable.onNext(content)
                            }
                        }
                    }
                }
            }
        } finally {
            message.acknowledge()
        }
    }

    /**
     * Closes this handler without notifying observables.
     * This method clears up only local resources and as such does not block on any network resources.
     */
    fun forceClose() {
        close(false)
    }

    /**
     * Closes this handler and sends notifications to all observables, so it can immediately clean up resources.
     * Notifications sent to observables are to be acknowledged, therefore this call blocks until all acknowledgements are received.
     * If this is not convenient see the [forceClose] method.
     * If an observable is not accessible this method may block for a duration of the message broker timeout.
     */
    fun notifyServerAndClose() {
        close(true)
    }

    /**
     * Closes the RPC proxy. Reaps all observables, shuts down the reaper, closes all sessions and executors.
     * When observables are to be notified (i.e. the [notify] parameter is true),
     * the method blocks until all the messages are acknowledged by the observables.
     * Note: If any of the observables is inaccessible, the method blocks for the duration of the timeout set on the message broker.
     *
     * @param notify whether to notify observables or not.
     */
    private fun close(notify: Boolean = true) {
        haFailoverThread?.apply {
            interrupt()
            join(1000)
        }

        if (notify) {
            // This is going to send remote message, see `org.apache.activemq.artemis.core.client.impl.ClientConsumerImpl.doCleanUp()`.
            sessionFactory?.close()
        } else {
            // This performs a cheaper and faster version of local cleanup.
            sessionFactory?.cleanup()
        }

        reaperScheduledFuture?.cancel(false)
        observableContext.observableMap.invalidateAll()
        reapObservables(notify)
        reaperExecutor?.shutdownNow()
        sendExecutor?.shutdownNow()
        // Note the ordering is important, we shut down the consumer *before* the observation executor, otherwise we may
        // leak borrowed executors.
        val observationExecutors = observationExecutorPool.close()
        observationExecutors.forEach { it.shutdownNow() }
        lifeCycle.justTransition(State.FINISHED)
    }

    /**
     * Check the [RPCSinceVersion] of the passed in [calledMethod] against the server's protocol version.
     */
    private fun checkProtocolVersion(calledMethod: Method) {
        val serverProtocolVersion = serverProtocolVersion
        if (serverProtocolVersion == null) {
            lifeCycle.requireState(State.SERVER_VERSION_NOT_SET)
        } else {
            lifeCycle.requireState(State.STARTED)
            val sinceVersion = calledMethod.getAnnotation(RPCSinceVersion::class.java)?.version ?: 0
            if (sinceVersion > serverProtocolVersion) {
                throw UnsupportedOperationException("Method $calledMethod was added in RPC protocol version $sinceVersion but the server is running $serverProtocolVersion")
            }
        }
    }

    /**
     * Set the server's protocol version. Note that before doing so the client is not considered fully started, although
     * RPCs already may be called with it.
     */
    internal fun setServerProtocolVersion(version: Int) {
        if (serverProtocolVersion == null) {
            serverProtocolVersion = version
        } else {
            throw IllegalStateException("setServerProtocolVersion called, but the protocol version was already set!")
        }
        lifeCycle.transition(State.SERVER_VERSION_NOT_SET, State.STARTED)
    }

    private fun reapObservablesAndNotify() = reapObservables()

    private fun reapObservables(notify: Boolean = true) {
        observableContext.observableMap.cleanUp()
        if (!notify) return
        val observableIds = observablesToReap.locked {
            if (observables.isNotEmpty()) {
                val temporary = observables
                observables = ArrayList()
                temporary
            } else {
                null
            }
        }
        if (observableIds != null) {
            log.debug { "Reaping ${observableIds.size} observables" }
            sendMessage(RPCApi.ClientToServer.ObservablesClosed(observableIds))
        }
    }

    private fun attemptReconnect() {
        var reconnectAttempts = rpcConfiguration.maxReconnectAttempts.times(serverLocator.staticTransportConfigurations.size)
        var retryInterval = rpcConfiguration.connectionRetryInterval
        val maxRetryInterval = rpcConfiguration.connectionMaxRetryInterval

        var transportIterator = serverLocator.staticTransportConfigurations.iterator()
        while (transportIterator.hasNext() && reconnectAttempts != 0) {
            val transport = transportIterator.next()
            if (!transportIterator.hasNext())
                transportIterator = serverLocator.staticTransportConfigurations.iterator()

            log.debug("Trying to connect using ${transport.params}")
            try {
                if (!serverLocator.isClosed) {
                    sessionFactory = serverLocator.createSessionFactory(transport)
                } else {
                    log.warn("Stopping reconnect attempts.")
                    log.debug("Server locator is closed or garbage collected. Proxy may have been closed during reconnect.")
                    break
                }
            } catch (e: ActiveMQException) {
                try {
                    Thread.sleep(retryInterval.toMillis())
                } catch (e: InterruptedException) {}
                // Could not connect, try with next server transport.
                reconnectAttempts--
                retryInterval = minOf(maxRetryInterval, retryInterval.times(rpcConfiguration.connectionRetryIntervalMultiplier.toLong()))
                continue
            }

            log.debug("Connected successfully after $reconnectAttempts attempts using ${transport.params}.")
            log.info("RPC server available.")
            sessionFactory!!.addFailoverListener(this::haFailoverHandler)
            initSessions()
            startSessions()
            sendingEnabled.set(true)
            break
        }

        if (reconnectAttempts == 0 || sessionFactory == null)
            log.error("Could not reconnect to the RPC server.")
    }

    private fun initSessions() {
        producerSession = sessionFactory!!.createSession(rpcUsername, rpcPassword, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
        rpcProducer = producerSession!!.createProducer(RPCApi.RPC_SERVER_QUEUE_NAME)
        consumerSession = sessionFactory!!.createSession(rpcUsername, rpcPassword, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
        consumerSession!!.createTemporaryQueue(clientAddress, RoutingType.ANYCAST, clientAddress)
        rpcConsumer = consumerSession!!.createConsumer(clientAddress)
        rpcConsumer!!.setMessageHandler(this::artemisMessageHandler)
    }

    private fun startSessions() {
        consumerSession!!.start()
        producerSession!!.start()
    }

    private fun haFailoverHandler(event: FailoverEventType) {
        if (event == FailoverEventType.FAILURE_DETECTED) {
            log.warn("Connection failure. Attempting to reconnect using back-up addresses.")
            cleanUpOnConnectionLoss()
            sessionFactory?.apply {
                connection.destroy()
                cleanup()
                close()
            }
            haFailoverThread = Thread.currentThread()
            attemptReconnect()
        }
        // Other events are not considered as reconnection is not done by Artemis.
    }

    private fun failoverHandler(event: FailoverEventType) {
        when (event) {
            FailoverEventType.FAILURE_DETECTED -> {
               cleanUpOnConnectionLoss()
            }

            FailoverEventType.FAILOVER_COMPLETED -> {
                sendingEnabled.set(true)
                log.info("RPC server available.")
            }

            FailoverEventType.FAILOVER_FAILED -> {
                log.error("Could not reconnect to the RPC server.")
            }
        }
    }

    private fun cleanUpOnConnectionLoss() {
        sendingEnabled.set(false)
        log.warn("Terminating observables.")
        val m = observableContext.observableMap.asMap()
        m.keys.forEach { k ->
            observationExecutorPool.run(k) {
                try {
                    m[k]?.onError(RPCException("Connection failure detected."))
                } catch (th: Throwable) {
                    log.error("Unexpected exception when RPC connection failure handling", th)
                }
            }
        }
        observableContext.observableMap.invalidateAll()

        rpcReplyMap.forEach { _, replyFuture ->
            replyFuture.setException(RPCException("Connection failure detected."))
        }

        rpcReplyMap.clear()
        callSiteMap?.clear()
    }
}

private typealias RpcObservableMap = Cache<InvocationId, UnicastSubject<Notification<*>>>
private typealias RpcReplyMap = ConcurrentHashMap<InvocationId, SettableFuture<Any?>>
private typealias CallSiteMap = ConcurrentHashMap<InvocationId, Throwable?>

/**
 * Holds a context available during de-serialisation of messages that are expected to contain Observables.
 *
 * @param observableMap holds the Observables that are ultimately exposed to the user.
 * @param hardReferenceStore holds references to Observables we want to keep alive while they are subscribed to.
 */
data class ObservableContext(
        val callSiteMap: CallSiteMap?,
        val observableMap: RpcObservableMap,
        val hardReferenceStore: MutableSet<Observable<*>>
)

