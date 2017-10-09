package net.corda.client.rpc.internal

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalCause
import com.google.common.cache.RemovalListener
import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.client.rpc.RPCException
import net.corda.client.rpc.RPCSinceVersion
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.LazyPool
import net.corda.core.internal.LazyStickyPool
import net.corda.core.internal.LifeCycle
import net.corda.core.internal.ThreadBox
import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.serialize
import net.corda.core.utilities.Try
import net.corda.core.utilities.debug
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.*
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ServerLocator
import rx.Notification
import rx.Observable
import rx.subjects.UnicastSubject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
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
 */
class RPCClientProxyHandler(
        private val rpcConfiguration: RPCClientConfiguration,
        private val rpcUsername: String,
        private val rpcPassword: String,
        private val serverLocator: ServerLocator,
        private val clientAddress: SimpleString,
        private val rpcOpsClass: Class<out RPCOps>,
        serializationContext: SerializationContext
) : InvocationHandler {

    private enum class State {
        UNSTARTED,
        SERVER_VERSION_NOT_SET,
        STARTED,
        FINISHED
    }

    private val lifeCycle = LifeCycle(State.UNSTARTED)

    private companion object {
        val log = loggerFor<RPCClientProxyHandler>()
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
        var observables = ArrayList<RPCApi.ObservableId>()
    })
    private val serializationContextWithObservableContext = RpcClientObservableSerializer.createContext(serializationContext, observableContext)

    private fun createRpcObservableMap(): RpcObservableMap {
        val onObservableRemove = RemovalListener<RPCApi.ObservableId, UnicastSubject<Notification<*>>> {
            val observableId = it.key!!
            val rpcCallSite = callSiteMap?.remove(observableId.toLong)
            if (it.cause == RemovalCause.COLLECTED) {
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
        return CacheBuilder.newBuilder().
                weakValues().
                removalListener(onObservableRemove).
                concurrencyLevel(rpcConfiguration.cacheConcurrencyLevel).
                build()
    }

    // We cannot pool consumers as we need to preserve the original muxed message order.
    // TODO We may need to pool these somehow anyway, otherwise if the server sends many big messages in parallel a
    // single consumer may be starved for flow control credits. Recheck this once Artemis's large message streaming is
    // integrated properly.
    private var sessionAndConsumer: ArtemisConsumer? = null
    // Pool producers to reduce contention on the client side.
    private val sessionAndProducerPool = LazyPool(bound = rpcConfiguration.producerPoolBound) {
        // Note how we create new sessions *and* session factories per producer.
        // We cannot simply pool producers on one session because sessions are single threaded.
        // We cannot simply pool sessions on one session factory because flow control credits are tied to factories, so
        // sessions tend to starve each other when used concurrently.
        val sessionFactory = serverLocator.createSessionFactory()
        val session = sessionFactory.createSession(rpcUsername, rpcPassword, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
        session.start()
        ArtemisProducer(sessionFactory, session, session.createProducer(RPCApi.RPC_SERVER_QUEUE_NAME))
    }

    /**
     * Start the client. This creates the per-client queue, starts the consumer session and the reaper.
     */
    fun start() {
        lifeCycle.requireState(State.UNSTARTED)
        reaperExecutor = Executors.newScheduledThreadPool(
                1,
                ThreadFactoryBuilder().setNameFormat("rpc-client-reaper-%d").setDaemon(true).build()
        )
        reaperScheduledFuture = reaperExecutor!!.scheduleAtFixedRate(
                this::reapObservablesAndNotify,
                rpcConfiguration.reapInterval.toMillis(),
                rpcConfiguration.reapInterval.toMillis(),
                TimeUnit.MILLISECONDS
        )
        sessionAndProducerPool.run {
            it.session.createTemporaryQueue(clientAddress, ActiveMQDefaultConfiguration.getDefaultRoutingType(), clientAddress)
        }
        val sessionFactory = serverLocator.createSessionFactory()
        val session = sessionFactory.createSession(rpcUsername, rpcPassword, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
        val consumer = session.createConsumer(clientAddress)
        consumer.setMessageHandler(this@RPCClientProxyHandler::artemisMessageHandler)
        sessionAndConsumer = ArtemisConsumer(sessionFactory, session, consumer)
        lifeCycle.transition(State.UNSTARTED, State.SERVER_VERSION_NOT_SET)
        session.start()
    }

    // This is the general function that transforms a client side RPC to internal Artemis messages.
    override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
        lifeCycle.requireState { it == State.STARTED || it == State.SERVER_VERSION_NOT_SET }
        checkProtocolVersion(method)
        if (method == toStringMethod) {
            return "Client RPC proxy for $rpcOpsClass"
        }
        if (sessionAndConsumer!!.session.isClosed) {
            throw RPCException("RPC Proxy is closed")
        }
        val rpcId = RPCApi.RpcRequestId(random63BitValue())
        callSiteMap?.set(rpcId.toLong, Throwable("<Call site of root RPC '${method.name}'>"))
        try {
            val serialisedArguments = (arguments?.toList() ?: emptyList()).serialize(context = serializationContextWithObservableContext)
            val request = RPCApi.ClientToServer.RpcRequest(clientAddress, rpcId, method.name, serialisedArguments.bytes)
            val replyFuture = SettableFuture.create<Any>()
            sessionAndProducerPool.run {
                val message = it.session.createMessage(false)
                request.writeToClientMessage(message)

                log.debug {
                    val argumentsString = arguments?.joinToString() ?: ""
                    "-> RPC($rpcId) -> ${method.name}($argumentsString): ${method.returnType}"
                }

                require(rpcReplyMap.put(rpcId, replyFuture) == null) {
                    "Generated several RPC requests with same ID $rpcId"
                }
                it.producer.send(message)
                it.session.commit()
            }
            return replyFuture.getOrThrow()
        } catch (e: RuntimeException) {
            // Already an unchecked exception, so just rethrow it
            throw e
        } catch (e: Exception) {
            // This must be a checked exception, so wrap it
            throw RPCException(e.message ?: "", e)
        } finally {
            callSiteMap?.remove(rpcId.toLong)
        }
    }

    // The handler for Artemis messages.
    private fun artemisMessageHandler(message: ClientMessage) {
        val serverToClient = RPCApi.ServerToClient.fromClientMessage(serializationContextWithObservableContext, message)
        log.debug { "Got message from RPC server $serverToClient" }
        when (serverToClient) {
            is RPCApi.ServerToClient.RpcReply -> {
                val replyFuture = rpcReplyMap.remove(serverToClient.id)
                if (replyFuture == null) {
                    log.error("RPC reply arrived to unknown RPC ID ${serverToClient.id}, this indicates an internal RPC error.")
                } else {
                    val result = serverToClient.result
                    when (result) {
                        is Try.Success -> replyFuture.set(result.value)
                        is Try.Failure -> {
                            val rpcCallSite = callSiteMap?.get(serverToClient.id.toLong)
                            if (rpcCallSite != null) addRpcCallSiteToThrowable(result.exception, rpcCallSite)
                            replyFuture.setException(result.exception)
                        }
                    }
                }
            }
            is RPCApi.ServerToClient.Observation -> {
                val observable = observableContext.observableMap.getIfPresent(serverToClient.id)
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
                                val rpcCallSite = callSiteMap?.get(serverToClient.id.toLong)
                                if (rpcCallSite != null) addRpcCallSiteToThrowable(content.throwable, rpcCallSite)
                            }
                            observable.onNext(content)
                        }
                    }
                }
            }
        }
        message.acknowledge()
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
        sessionAndConsumer?.sessionFactory?.close()
        reaperScheduledFuture?.cancel(false)
        observableContext.observableMap.invalidateAll()
        reapObservables(notify)
        reaperExecutor?.shutdownNow()
        sessionAndProducerPool.close().forEach {
            it.sessionFactory.close()
        }
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
            sessionAndProducerPool.run {
                val message = it.session.createMessage(false)
                RPCApi.ClientToServer.ObservablesClosed(observableIds).writeToClientMessage(message)
                it.producer.send(message)
            }
        }
    }
}

private typealias RpcObservableMap = Cache<RPCApi.ObservableId, UnicastSubject<Notification<*>>>
private typealias RpcReplyMap = ConcurrentHashMap<RPCApi.RpcRequestId, SettableFuture<Any?>>
private typealias CallSiteMap = ConcurrentHashMap<Long, Throwable?>

/**
 * Holds a context available during Kryo deserialisation of messages that are expected to contain Observables.
 *
 * @param observableMap holds the Observables that are ultimately exposed to the user.
 * @param hardReferenceStore holds references to Observables we want to keep alive while they are subscribed to.
 */
data class ObservableContext(
        val callSiteMap: CallSiteMap?,
        val observableMap: RpcObservableMap,
        val hardReferenceStore: MutableSet<Observable<*>>
)

/**
 * A [Serializer] to deserialise Observables once the corresponding Kryo instance has been provided with an [ObservableContext].
 */
object RpcClientObservableSerializer : Serializer<Observable<*>>() {
    private object RpcObservableContextKey

    fun createContext(serializationContext: SerializationContext, observableContext: ObservableContext): SerializationContext {
        return serializationContext.withProperty(RpcObservableContextKey, observableContext)
    }

    private fun <T> pinInSubscriptions(observable: Observable<T>, hardReferenceStore: MutableSet<Observable<*>>): Observable<T> {
        val refCount = AtomicInteger(0)
        return observable.doOnSubscribe {
            if (refCount.getAndIncrement() == 0) {
                require(hardReferenceStore.add(observable)) { "Reference store already contained reference $this on add" }
            }
        }.doOnUnsubscribe {
            if (refCount.decrementAndGet() == 0) {
                require(hardReferenceStore.remove(observable)) { "Reference store did not contain reference $this on remove" }
            }
        }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Observable<*>>): Observable<Any> {
        val observableContext = kryo.context[RpcObservableContextKey] as ObservableContext
        val observableId = RPCApi.ObservableId(input.readLong(true))
        val observable = UnicastSubject.create<Notification<*>>()
        require(observableContext.observableMap.getIfPresent(observableId) == null) {
            "Multiple Observables arrived with the same ID $observableId"
        }
        val rpcCallSite = getRpcCallSite(kryo, observableContext)
        observableContext.observableMap.put(observableId, observable)
        observableContext.callSiteMap?.put(observableId.toLong, rpcCallSite)
        // We pin all Observables into a hard reference store (rooted in the RPC proxy) on subscription so that users
        // don't need to store a reference to the Observables themselves.
        return pinInSubscriptions(observable, observableContext.hardReferenceStore).doOnUnsubscribe {
            // This causes Future completions to give warnings because the corresponding OnComplete sent from the server
            // will arrive after the client unsubscribes from the observable and consequently invalidates the mapping.
            // The unsubscribe is due to [ObservableToFuture]'s use of first().
            observableContext.observableMap.invalidate(observableId)
        }.dematerialize()
    }

    override fun write(kryo: Kryo, output: Output, observable: Observable<*>) {
        throw UnsupportedOperationException("Cannot serialise Observables on the client side")
    }

    private fun getRpcCallSite(kryo: Kryo, observableContext: ObservableContext): Throwable? {
        val rpcRequestOrObservableId = kryo.context[RPCApi.RpcRequestOrObservableIdKey] as Long
        return observableContext.callSiteMap?.get(rpcRequestOrObservableId)
    }
}