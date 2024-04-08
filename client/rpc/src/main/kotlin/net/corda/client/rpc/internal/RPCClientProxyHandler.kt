package net.corda.client.rpc.internal

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.RemovalListener
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.client.rpc.ConnectionFailureException
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.RPCException
import net.corda.client.rpc.RPCSinceVersion
import net.corda.client.rpc.internal.RPCUtils.isShutdownCmd
import net.corda.core.context.Actor
import net.corda.core.context.Trace
import net.corda.core.context.Trace.InvocationId
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.LazyStickyPool
import net.corda.core.internal.LifeCycle
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.telemetry.TelemetryStatusCode
import net.corda.core.internal.times
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.serialize
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.RPCApi.CLASS_METHOD_DIVIDER
import net.corda.nodeapi.internal.DeduplicationChecker
import net.corda.nodeapi.internal.rpc.client.CallSite
import net.corda.nodeapi.internal.rpc.client.CallSiteMap
import net.corda.nodeapi.internal.rpc.client.ObservableContext
import net.corda.nodeapi.internal.rpc.client.RpcClientObservableDeSerializer
import net.corda.nodeapi.internal.rpc.client.RpcObservableMap
import org.apache.activemq.artemis.api.core.ActiveMQException
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
import org.apache.activemq.artemis.api.core.QueueConfiguration
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory
import org.apache.activemq.artemis.api.core.client.FailoverEventListener
import org.apache.activemq.artemis.api.core.client.FailoverEventType
import org.apache.activemq.artemis.api.core.client.ServerLocator
import rx.Notification
import rx.Observable
import rx.exceptions.OnErrorNotImplementedException
import rx.subjects.UnicastSubject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
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
 * To do the above we take advantage of serialisation data structure traversal. When the client is deserialising a message from
 * the server that may contain [Observable]s, it is supplied with an [ObservableContext] that exposes the map used to demux
 * the observations. When a new [Observable] is encountered during traversal a new [UnicastSubject] is added to the map and
 * we carry on. Each observation later contains the corresponding [Observable] ID, and we just forward that to the
 * associated [UnicastSubject].
 *
 * The client may signal that it no longer consumes a particular [Observable]. This may be done explicitly by
 * unsubscribing from the [Observable], or if the [Observable] is garbage collected the client will eventually
 * automatically signal the server. This is done using a cache that holds weak references to the [UnicastSubject]s.
 * The cleanup happens in batches using a dedicated reaper, scheduled on [reaperExecutor].
 *
 * The client will attempt to failover in case the server become unreachable. Depending on the [ServerLocator] instance
 * passed in the constructor, failover is either handled at Artemis level or client level. If only one transport
 * was used to create the [ServerLocator], failover is handled by Artemis (retrying based on [CordaRPCClientConfiguration].
 * If a list of transport configurations was used, failover is handled locally. Artemis is able to do it, however the
 * brokers on server side need to be configured in HA mode and the [ServerLocator] needs to be created with HA as well.
 */
internal class RPCClientProxyHandler(
        private val rpcConfiguration: CordaRPCClientConfiguration,
        private val rpcUsername: String,
        private val rpcPassword: String,
        private val serverLocator: ServerLocator,
        private val rpcOpsClass: Class<out RPCOps>,
        serializationContext: SerializationContext,
        private val sessionId: Trace.SessionId,
        private val externalTrace: Trace?,
        private val impersonatedActor: Actor?,
        private val targetLegalIdentity: CordaX500Name?,
        private val notificationDistributionMux: DistributionMux<out RPCOps>,
        private val rpcClientTelemetry: RPCClientTelemetry,
        private val cacheFactory: NamedCacheFactory = ClientCacheFactory()
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
        val equalsMethod: Method = Object::equals.javaMethod!!
        val hashCodeMethod: Method = Object::hashCode.javaMethod!!
        var terminating  = false
        private fun addRpcCallSiteToThrowable(throwable: Throwable, callSite: CallSite) {
            var currentThrowable = throwable
            while (true) {
                val cause = currentThrowable.cause
                if (cause == null) {
                    try {
                        currentThrowable.initCause(callSite)
                    } catch (e: IllegalStateException) {
                        // OK, we did our best, but the first throwable with a null cause was instantiated using
                        // Throwable(Throwable) or Throwable(String, Throwable) which means initCause can't ever
                        // be called even if it was passed null.
                    }
                    break
                } else {
                    currentThrowable = cause
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun closeObservable(observable: UnicastSubject<Notification<*>>) {
            // Notify listeners of the observables that the connection is being terminated.
            try {
                observable.onError(ConnectionFailureException())
            } catch (ex: OnErrorNotImplementedException) {
                // Indicates the observer does not have any error handling.
                log.debug { "Closed connection on observable whose observers have no error handling." }
            } catch (ex: Exception) {
                log.error("Unexpected exception when RPC connection failure handling", ex)
            }
        }
    }

    // Used for reaping
    private var reaperExecutor: ScheduledExecutorService? = null
    // Used for sending
    private var sendExecutor: ExecutorService? = null

    // A sticky pool for running Observable.onNext()s. We need the stickiness to preserve the observation ordering.
    private val observationExecutorThreadFactory = ThreadFactoryBuilder()
            .setNameFormat("rpc-client-observation-pool-%d")
            .setDaemon(true)
            .build()
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
    private val serializationContextWithObservableContext = RpcClientObservableDeSerializer
            .createContext(serializationContext, observableContext)

    private fun createRpcObservableMap(): RpcObservableMap {
        val onObservableRemove = RemovalListener<InvocationId, UnicastSubject<Notification<*>>> { key, _, cause ->
            val observableId = key!!
            val rpcCallSite: CallSite? = callSiteMap?.remove(observableId)

            if (cause == RemovalCause.COLLECTED) {
                log.warn(listOf(
                        "A hot observable returned from an RPC was never subscribed to.",
                        "This wastes server-side resources because it was queueing observations for retrieval.",
                        "It is being closed now, but please adjust your code to call .notUsed() on the observable",
                        "to close it explicitly. (Java users: subscribe to it then unsubscribe). If you aren't sure",
                        "where the leak is coming from, set -Dnet.corda.client.rpc.trackRpcCallSites=true on the JVM",
                        "command line and you will get a stack trace with this warning."
                ).joinToString(" "), rpcCallSite)
                rpcCallSite?.printStackTrace()
            }
            observablesToReap.locked { observables.add(observableId) }
        }
        return cacheFactory.buildNamed(
                Caffeine.newBuilder()
                        .weakValues()
                        .removalListener(onObservableRemove)
                        .executor(MoreExecutors.directExecutor()),
                "RpcClientProxyHandler_rpcObservable"
        )
    }

    private var clientAddress: SimpleString? = null
    private var sessionFactory: ClientSessionFactory? = null
    private var producerSession: ClientSession? = null
    private var consumerSession: ClientSession? = null
    private var rpcProducer: ClientProducer? = null
    private var rpcConsumer: ClientConsumer? = null

    private val deduplicationChecker = DeduplicationChecker(rpcConfiguration.deduplicationCacheExpiry, cacheFactory = cacheFactory)
    private val deduplicationSequenceNumber = AtomicLong(0)

    private val sendingEnabled = AtomicBoolean(true)
    // Used to interrupt failover thread (i.e. client is closed while failing over).
    private var haFailoverThread: Thread? = null
    private val haFailoverHandler: FailoverHandler = FailoverHandler(
            detected = { log.warn("Connection failure. Attempting to reconnect using back-up addresses.")
                cleanUpOnConnectionLoss()
                sessionFactory?.apply {
                    connection.destroy()
                    cleanup()
                    close()
                }
                haFailoverThread = Thread.currentThread()
                attemptReconnect()
            })
    private val defaultFailoverHandler: FailoverHandler = FailoverHandler(
            detected = { cleanUpOnConnectionLoss() },
            completed = { sendingEnabled.set(true)
                log.info("RPC server available.")},
            failed = { log.error("Could not reconnect to the RPC server.")})

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
            throw RPCException("Cannot connect to server(s). Tried with all available servers.", e)
        }
        // Depending on how the client is constructed, connection failure is treated differently
        if (serverLocator.staticTransportConfigurations.size == 1) {
            sessionFactory!!.addFailoverListener(defaultFailoverHandler)
        } else {
            sessionFactory!!.addFailoverListener(haFailoverHandler)
        }
        initSessions()
        lifeCycle.transition(State.UNSTARTED, State.SERVER_VERSION_NOT_SET)
        startSessions()
    }

    class FailoverHandler(private val detected: () -> Unit = {},
                          private val completed: () -> Unit = {},
                          private val failed: () -> Unit = {}): FailoverEventListener {
        override fun failoverEvent(eventType: FailoverEventType?) {
            when (eventType) {
                FailoverEventType.FAILURE_DETECTED -> { detected() }
                FailoverEventType.FAILOVER_COMPLETED -> { completed() }
                FailoverEventType.FAILOVER_FAILED -> { if (!terminating) failed() }
            }
        }
    }

    // This is the general function that transforms a client side RPC to internal Artemis messages.
    override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
        lifeCycle.requireState { it == State.STARTED || it == State.SERVER_VERSION_NOT_SET }
        checkProtocolVersion(method)
        if (method == toStringMethod) {
            return toString()
        }
        if (method == equalsMethod) {
            return equals(arguments?.getOrNull(0))
        }
        if (method == hashCodeMethod) {
            return hashCode()
        }
        if (consumerSession!!.isClosed) {
            throw RPCException("RPC Proxy is closed")
        }

        if (!sendingEnabled.get())
            throw RPCException("RPC server is not available.")

        val replyId = InvocationId.newInstance()
        val methodFqn = produceMethodFullyQualifiedName(method)
        callSiteMap?.set(replyId, CallSite(methodFqn))

        val telemetryId = rpcClientTelemetry.telemetryService.startSpanForFlow("client-$methodFqn", emptyMap())
        try {
            val serialisedArguments = (arguments?.toList() ?: emptyList()).serialize(context = serializationContextWithObservableContext)
            val request = RPCApi.ClientToServer.RpcRequest(
                    clientAddress!!,
                    methodFqn,
                    serialisedArguments,
                    replyId,
                    sessionId,
                    externalTrace,
                    impersonatedActor,
                    rpcClientTelemetry.telemetryService.getCurrentTelemetryData()
            )
            val replyFuture = SettableFuture.create<Any>()
            require(rpcReplyMap.put(replyId, replyFuture) == null) {
                "Generated several RPC requests with same ID $replyId"
            }

            if (request.isShutdownCmd()){
                terminating = true
            }

            sendMessage(request)
            return replyFuture.getOrThrow()
        } catch (e: RuntimeException) {
            rpcClientTelemetry.telemetryService.recordException(telemetryId, e)
            rpcClientTelemetry.telemetryService.setStatus(telemetryId, TelemetryStatusCode.ERROR, e.message ?: "RuntimeException occurred")
            // Already an unchecked exception, so just rethrow it
            throw e
        } catch (e: Exception) {
            rpcClientTelemetry.telemetryService.recordException(telemetryId, e)
            rpcClientTelemetry.telemetryService.setStatus(telemetryId, TelemetryStatusCode.ERROR, e.message ?: "Exception occurred")
            // This must be a checked exception, so wrap it
            throw RPCException(e.message ?: "", e)
        } finally {
            callSiteMap?.remove(replyId)
            rpcClientTelemetry.telemetryService.endSpanForFlow(telemetryId)
        }
    }

    private fun produceMethodFullyQualifiedName(method: Method) : String {
        /*
         * Until version 4.3, rpc calls did not include class names.
         * Up to this version, only CordaRPCOps was supported.
         * So, for these classes only methods are sent across the wire to preserve backwards compatibility.
         */
        return if (CordaRPCOps::class.java == rpcOpsClass) {
            method.name
        } else {
            rpcOpsClass.name + CLASS_METHOD_DIVIDER + method.name
        }
    }

    private fun sendMessage(message: RPCApi.ClientToServer) {
        val artemisMessage = producerSession!!.createMessage(false)
        message.writeToClientMessage(artemisMessage)
        targetLegalIdentity?.let {
            artemisMessage.putStringProperty(RPCApi.RPC_TARGET_LEGAL_IDENTITY, it.toString())
        }
        val future: Future<*> = sendExecutor!!.submit {
            artemisMessage.putLongProperty(RPCApi.DEDUPLICATION_SEQUENCE_NUMBER_FIELD_NAME, deduplicationSequenceNumber.getAndIncrement())
            log.debug { "-> RPC -> $message" }
            rpcProducer!!.let {
                if (!it.isClosed) {
                    it.send(artemisMessage)
                } else {
                    log.info("Producer is already closed. Not sending: $message")
                }
            }
        }
        future.getOrThrow()
    }

    // The handler for Artemis messages.
    private fun artemisMessageHandler(message: ClientMessage) {
        fun completeExceptionally(id: InvocationId, e: Throwable, future: SettableFuture<Any?>?) {
            val rpcCallSite: CallSite? = callSiteMap?.get(id)
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
                        log.debug {
                            "Observation ${serverToClient.content} arrived to unknown Observable with ID ${serverToClient.id}. " +
                                    "This may be due to an observation arriving before the server was " +
                                    "notified of observable shutdown"
                        }
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
        observableContext.observableMap.asMap().forEach { (key, observable) ->
            observationExecutorPool.run(key) {
                observable?.also(Companion::closeObservable)
            }
        }
        observableContext.observableMap.invalidateAll()
        rpcReplyMap.forEach { (_, replyFuture) ->
            replyFuture.setException(ConnectionFailureException())
        }

        rpcReplyMap.clear()
        callSiteMap?.clear()

        reapObservables(notify)
        reaperExecutor?.shutdownNow()
        sendExecutor?.shutdownNow()
        // Note the ordering is important, we shut down the consumer *before* the observation executor, otherwise we may
        // leak borrowed executors.
        val observationExecutors = observationExecutorPool.close()
        observationExecutors.forEach { it.shutdownNow() }
        notificationDistributionMux.onDisconnect(null)
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
            @Suppress("TooGenericExceptionCaught")
            try {
                sendMessage(RPCApi.ClientToServer.ObservablesClosed(observableIds))
            } catch(ex: Exception) {
                log.warn("Unable to close observables", ex)
            }
        }
    }

    private fun attemptReconnect() {
        // This can be a negative number as `rpcConfiguration.maxReconnectAttempts = -1` means infinite number of re-connects
        val maxReconnectCount = rpcConfiguration.maxReconnectAttempts.times(serverLocator.staticTransportConfigurations.size)
        log.debug { "maxReconnectCount = $maxReconnectCount" }
        var reconnectAttempt = 1
        var retryInterval = rpcConfiguration.connectionRetryInterval
        val maxRetryInterval = rpcConfiguration.connectionMaxRetryInterval

        fun shouldRetry(reconnectAttempt: Int) =
                if (maxReconnectCount < 0) true else reconnectAttempt <= maxReconnectCount

        while (shouldRetry(reconnectAttempt)) {
            val transport = serverLocator.staticTransportConfigurations.let { it[(reconnectAttempt - 1) % it.size] }

            log.debug { "Trying to connect using ${transport.params}" }
            try {
                if (!serverLocator.isClosed) {
                    sessionFactory = serverLocator.createSessionFactory(transport)
                } else {
                    log.warn("Stopping reconnect attempts.")
                    log.debug { "Server locator is closed or garbage collected. Proxy may have been closed during reconnect." }
                    break
                }
            } catch (e: ActiveMQException) {
                try {
                    Thread.sleep(retryInterval.toMillis())
                } catch (e: InterruptedException) {}
                // Could not connect, try with next server transport.
                reconnectAttempt++
                retryInterval = minOf(maxRetryInterval, retryInterval.times(rpcConfiguration.connectionRetryIntervalMultiplier.toLong()))
                continue
            }

            log.debug { "Connected successfully after $reconnectAttempt attempts using ${transport.params}." }
            log.info("RPC server available.")
            sessionFactory!!.addFailoverListener(haFailoverHandler)
            initSessions()
            startSessions()
            sendingEnabled.set(true)
            notificationDistributionMux.onConnect()
            break
        }

        val maxReconnectReached = !shouldRetry(reconnectAttempt)
        if (maxReconnectReached || sessionFactory == null) {
            val errMessage = "Could not reconnect to the RPC server after trying $reconnectAttempt times." +
                    if (sessionFactory != null) "" else " It was never possible to to establish connection with any of the endpoints."
            log.error(errMessage)
            notificationDistributionMux.onPermanentFailure(IllegalStateException(errMessage))
        }
    }

    private fun initSessions() {
        producerSession = sessionFactory!!.createSession(rpcUsername, rpcPassword, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
        rpcProducer = producerSession!!.createProducer(RPCApi.RPC_SERVER_QUEUE_NAME)
        consumerSession = sessionFactory!!.createSession(rpcUsername, rpcPassword, false, true, true, false, 16384)
        clientAddress = SimpleString("${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.$rpcUsername.${random63BitValue()}")
        log.debug { "Client address: $clientAddress" }
        consumerSession!!.createQueue(QueueConfiguration(clientAddress).setAddress(clientAddress).setRoutingType(RoutingType.ANYCAST)
                .setTemporary(true).setDurable(false))
        rpcConsumer = consumerSession!!.createConsumer(clientAddress)
        rpcConsumer!!.setMessageHandler(this::artemisMessageHandler)
    }

    private fun startSessions() {
        consumerSession!!.start()
        producerSession!!.start()
    }

    private fun cleanUpOnConnectionLoss() {
        sendingEnabled.set(false)
        log.warn("Terminating observables.")
        val m = observableContext.observableMap.asMap()
        val connectionFailureException = ConnectionFailureException()
        m.keys.forEach { k ->
            observationExecutorPool.run(k) {
                try {
                    m[k]?.onError(connectionFailureException)
                } catch (e: Exception) {
                    log.error("Unexpected exception when RPC connection failure handling", e)
                }
            }
        }
        observableContext.observableMap.invalidateAll()

        rpcReplyMap.forEach { (_, replyFuture) ->
            replyFuture.setException(connectionFailureException)
        }

        log.debug { "rpcReplyMap size before clear: ${rpcReplyMap.size}" }
        rpcReplyMap.clear()
        log.debug { "callSiteMap size before clear: ${callSiteMap?.size}" }
        callSiteMap?.clear()
        notificationDistributionMux.onDisconnect(connectionFailureException)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RPCClientProxyHandler

        if (rpcUsername != other.rpcUsername) return false
        if (sessionId != other.sessionId) return false
        if (targetLegalIdentity != other.targetLegalIdentity) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rpcUsername.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + (targetLegalIdentity?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "{rpcUsername='$rpcUsername', clientAddress=$clientAddress, sessionId=$sessionId, targetLegalIdentity=$targetLegalIdentity}"
    }
}

private typealias RpcReplyMap = ConcurrentHashMap<InvocationId, SettableFuture<Any?>>

