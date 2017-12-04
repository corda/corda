package net.corda.node.services.messaging

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalListener
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimaps
import com.google.common.collect.SetMultimap
import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.client.rpc.RPCException
import net.corda.core.context.Actor
import net.corda.core.context.Actor.Id
import net.corda.core.context.InvocationContext
import net.corda.core.context.Trace
import net.corda.core.context.Trace.InvocationId
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.LazyStickyPool
import net.corda.core.internal.LifeCycle
import net.corda.core.internal.join
import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults.RPC_SERVER_CONTEXT
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.*
import net.corda.node.services.RPCUserService
import net.corda.node.services.logging.pushToLoggingContext
import net.corda.nodeapi.*
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.nodeapi.internal.config.User
import org.apache.activemq.artemis.api.core.Message
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.apache.activemq.artemis.api.core.client.ServerLocator
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.apache.activemq.artemis.api.core.management.CoreNotificationType
import org.apache.activemq.artemis.api.core.management.ManagementHelper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import rx.Notification
import rx.Observable
import rx.Subscriber
import rx.Subscription
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.time.Duration
import java.util.concurrent.*

data class RPCServerConfiguration(
        /** The number of threads to use for handling RPC requests */
        val rpcThreadPoolSize: Int,
        /** The number of consumers to handle incoming messages */
        val consumerPoolSize: Int,
        /** The maximum number of producers to create to handle outgoing messages */
        val producerPoolBound: Int,
        /** The interval of subscription reaping */
        val reapInterval: Duration
) {
    companion object {
        val default = RPCServerConfiguration(
                rpcThreadPoolSize = 4,
                consumerPoolSize = 2,
                producerPoolBound = 4,
                reapInterval = 1.seconds
        )
    }
}

/**
 * The [RPCServer] implements the complement of [RPCClient]. When an RPC request arrives it dispatches to the
 * corresponding function in [ops]. During serialisation of the reply (and later observations) the server subscribes to
 * each Observable it encounters and captures the client address to associate with these Observables. Later it uses this
 * address to forward observations arriving on the Observables.
 *
 * The way this is done is similar to that in [RPCClient], we use Kryo and add a context to stores the subscription map.
 */
class RPCServer(
        private val ops: RPCOps,
        private val rpcServerUsername: String,
        private val rpcServerPassword: String,
        private val serverLocator: ServerLocator,
        private val userService: RPCUserService,
        private val nodeLegalName: CordaX500Name,
        private val rpcConfiguration: RPCServerConfiguration = RPCServerConfiguration.default
) {
    private companion object {
        private val log = contextLogger()
    }

    private enum class State {
        UNSTARTED,
        STARTED,
        FINISHED
    }

    private sealed class BufferOrNone {
        data class Buffer(val container: MutableCollection<MessageAndContext>) : BufferOrNone()
        object None : BufferOrNone()
    }

    private data class MessageAndContext(val message: RPCApi.ServerToClient.RpcReply, val context: ObservableContext)

    private val lifeCycle = LifeCycle(State.UNSTARTED)
    /** The methodname->Method map to use for dispatching. */
    private val methodTable: Map<String, Method>
    /** The observable subscription mapping. */
    private val observableMap = createObservableSubscriptionMap()
    /** A mapping from client addresses to IDs of associated Observables */
    private val clientAddressToObservables = Multimaps.synchronizedSetMultimap(HashMultimap.create<SimpleString, InvocationId>())
    /** The scheduled reaper handle. */
    private var reaperScheduledFuture: ScheduledFuture<*>? = null

    private var observationSendExecutor: ExecutorService? = null
    private var rpcExecutor: ScheduledExecutorService? = null
    private var reaperExecutor: ScheduledExecutorService? = null

    private val sessionAndConsumers = ArrayList<ArtemisConsumer>(rpcConfiguration.consumerPoolSize)
    private val sessionAndProducerPool = LazyStickyPool(rpcConfiguration.producerPoolBound) {
        val sessionFactory = serverLocator.createSessionFactory()
        val session = sessionFactory.createSession(rpcServerUsername, rpcServerPassword, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
        session.start()
        ArtemisProducer(sessionFactory, session, session.createProducer())
    }
    private var clientBindingRemovalConsumer: ClientConsumer? = null
    private var clientBindingAdditionConsumer: ClientConsumer? = null
    private var serverControl: ActiveMQServerControl? = null

    private val responseMessageBuffer = ConcurrentHashMap<SimpleString, BufferOrNone>()

    init {
        val groupedMethods = ops.javaClass.declaredMethods.groupBy { it.name }
        groupedMethods.forEach { name, methods ->
            if (methods.size > 1) {
                throw IllegalArgumentException("Encountered more than one method called $name on ${ops.javaClass.name}")
            }
        }
        methodTable = groupedMethods.mapValues { it.value.single() }
    }

    private fun createObservableSubscriptionMap(): ObservableSubscriptionMap {
        val onObservableRemove = RemovalListener<InvocationId, ObservableSubscription> {
            log.debug { "Unsubscribing from Observable with id ${it.key} because of ${it.cause}" }
            it.value.subscription.unsubscribe()
        }
        return CacheBuilder.newBuilder().removalListener(onObservableRemove).build()
    }

    fun start(activeMqServerControl: ActiveMQServerControl) {
        try {
            lifeCycle.requireState(State.UNSTARTED)
            log.info("Starting RPC server with configuration $rpcConfiguration")
            observationSendExecutor = Executors.newFixedThreadPool(
                    1,
                    ThreadFactoryBuilder().setNameFormat("rpc-observation-sender-%d").build()
            )
            rpcExecutor = Executors.newScheduledThreadPool(
                    rpcConfiguration.rpcThreadPoolSize,
                    ThreadFactoryBuilder().setNameFormat("rpc-server-handler-pool-%d").build()
            )
            reaperExecutor = Executors.newScheduledThreadPool(
                    1,
                    ThreadFactoryBuilder().setNameFormat("rpc-server-reaper-%d").build()
            )
            reaperScheduledFuture = reaperExecutor!!.scheduleAtFixedRate(
                    this::reapSubscriptions,
                    rpcConfiguration.reapInterval.toMillis(),
                    rpcConfiguration.reapInterval.toMillis(),
                    TimeUnit.MILLISECONDS
            )
            val sessions = createConsumerSessions()
            createNotificationConsumers()
            serverControl = activeMqServerControl
            lifeCycle.transition(State.UNSTARTED, State.STARTED)
            // We delay the consumer session start because Artemis starts delivering messages immediately, so we need to be
            // fully initialised.
            sessions.forEach {
                it.start()
            }
        } catch (exception: Throwable) {
            close()
            throw exception
        }
    }

    private fun createConsumerSessions(): ArrayList<ClientSession> {
        val sessions = ArrayList<ClientSession>()
        for (i in 1..rpcConfiguration.consumerPoolSize) {
            val sessionFactory = serverLocator.createSessionFactory()
            val session = sessionFactory.createSession(rpcServerUsername, rpcServerPassword, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
            val consumer = session.createConsumer(RPCApi.RPC_SERVER_QUEUE_NAME)
            consumer.setMessageHandler(this@RPCServer::clientArtemisMessageHandler)
            sessionAndConsumers.add(ArtemisConsumer(sessionFactory, session, consumer))
            sessions.add(session)
        }
        return sessions
    }

    private fun createNotificationConsumers() {
        clientBindingRemovalConsumer = sessionAndConsumers[0].session.createConsumer(RPCApi.RPC_CLIENT_BINDING_REMOVALS)
        clientBindingRemovalConsumer!!.setMessageHandler(this::bindingRemovalArtemisMessageHandler)
        clientBindingAdditionConsumer = sessionAndConsumers[0].session.createConsumer(RPCApi.RPC_CLIENT_BINDING_ADDITIONS)
        clientBindingAdditionConsumer!!.setMessageHandler(this::bindingAdditionArtemisMessageHandler)
    }

    fun close() {
        observationSendExecutor?.join()
        reaperScheduledFuture?.cancel(false)
        rpcExecutor?.shutdownNow()
        reaperExecutor?.shutdownNow()
        sessionAndConsumers.forEach {
            it.sessionFactory.close()
        }
        observableMap.invalidateAll()
        reapSubscriptions()
        sessionAndProducerPool.close().forEach {
            it.sessionFactory.close()
        }
        lifeCycle.justTransition(State.FINISHED)
    }

    private fun bindingRemovalArtemisMessageHandler(artemisMessage: ClientMessage) {
        lifeCycle.requireState(State.STARTED)
        val notificationType = artemisMessage.getStringProperty(ManagementHelper.HDR_NOTIFICATION_TYPE)
        require(notificationType == CoreNotificationType.BINDING_REMOVED.name)
        val clientAddress = artemisMessage.getStringProperty(ManagementHelper.HDR_ROUTING_NAME)
        log.warn("Detected RPC client disconnect on address $clientAddress, scheduling for reaping")
        invalidateClient(SimpleString(clientAddress))
    }

    private fun bindingAdditionArtemisMessageHandler(artemisMessage: ClientMessage) {
        lifeCycle.requireState(State.STARTED)
        val notificationType = artemisMessage.getStringProperty(ManagementHelper.HDR_NOTIFICATION_TYPE)
        require(notificationType == CoreNotificationType.BINDING_ADDED.name)
        val clientAddress = SimpleString(artemisMessage.getStringProperty(ManagementHelper.HDR_ROUTING_NAME))
        log.debug("RPC client queue created on address $clientAddress")

        val buffer = stopBuffering(clientAddress)
        buffer?.let { drainBuffer(it) }
    }

    /**
     * Disables message buffering for [clientAddress] and returns the existing buffer
     * or `null` if no requests were ever received.
     */
    private fun stopBuffering(clientAddress: SimpleString): BufferOrNone.Buffer? {
        return responseMessageBuffer.put(clientAddress, BufferOrNone.None) as? BufferOrNone.Buffer
    }

    private fun drainBuffer(buffer: BufferOrNone.Buffer) {
        buffer.container.forEach {
            it.context.sendMessage(it.message)
        }
    }

    // Note that this function operates on the *current* view of client observables. During invalidation further
    // Observables may be serialised and thus registered.
    private fun invalidateClient(clientAddress: SimpleString) {
        lifeCycle.requireState(State.STARTED)
        val observableIds = clientAddressToObservables.removeAll(clientAddress)
        observableMap.invalidateAll(observableIds)
        responseMessageBuffer.remove(clientAddress)
    }

    private fun clientArtemisMessageHandler(artemisMessage: ClientMessage) {
        lifeCycle.requireState(State.STARTED)
        val clientToServer = RPCApi.ClientToServer.fromClientMessage(artemisMessage)
        log.debug { "-> RPC -> $clientToServer" }
        when (clientToServer) {
            is RPCApi.ClientToServer.RpcRequest -> {
                val arguments = Try.on {
                    clientToServer.serialisedArguments.deserialize<List<Any?>>(context = RPC_SERVER_CONTEXT)
                }
                val context = artemisMessage.context(clientToServer.sessionId)
                context.invocation.pushToLoggingContext()
                when (arguments) {
                    is Try.Success -> {
                        rpcExecutor!!.submit {
                            val result = invokeRpc(context, clientToServer.methodName, arguments.value)
                            sendReply(clientToServer.replyId, clientToServer.clientAddress, result)
                        }
                    }
                    is Try.Failure -> {
                        // We failed to deserialise the arguments, route back the error
                        log.warn("Inbound RPC failed", arguments.exception)
                        sendReply(clientToServer.replyId, clientToServer.clientAddress, arguments)
                    }
                }
            }
            is RPCApi.ClientToServer.ObservablesClosed -> {
                observableMap.invalidateAll(clientToServer.ids)
            }
        }
        artemisMessage.acknowledge()
    }

    private fun invokeRpc(context: RpcAuthContext, methodName: String, arguments: List<Any?>): Try<Any> {
        return Try.on {
            try {
                CURRENT_RPC_CONTEXT.set(context)
                log.debug { "Calling $methodName" }
                val method = methodTable[methodName] ?:
                        throw RPCException("Received RPC for unknown method $methodName - possible client/server version skew?")
                method.invoke(ops, *arguments.toTypedArray())
            } catch (e: InvocationTargetException) {
                throw e.cause ?: RPCException("Caught InvocationTargetException without cause")
            } finally {
                CURRENT_RPC_CONTEXT.remove()
            }
        }
    }

    private fun sendReply(replyId: InvocationId, clientAddress: SimpleString, result: Try<Any>) {
        val reply = RPCApi.ServerToClient.RpcReply(replyId, result)
        val observableContext = ObservableContext(
                replyId,
                observableMap,
                clientAddressToObservables,
                clientAddress,
                serverControl!!,
                sessionAndProducerPool,
                observationSendExecutor!!
        )

        val buffered = bufferIfQueueNotBound(clientAddress, reply, observableContext)
        if (!buffered) observableContext.sendMessage(reply)
    }

    /**
     * Buffer the message if the queue at [clientAddress] is not yet bound.
     *
     * This can happen after server restart when the client consumer session initiates failover,
     * but the client queue is not yet set up. We buffer the messages and flush the buffer only once
     * we receive a notification that the client queue bindings were added.
     */
    private fun bufferIfQueueNotBound(clientAddress: SimpleString, message: RPCApi.ServerToClient.RpcReply, context: ObservableContext): Boolean {
        val clientBuffer = responseMessageBuffer.compute(clientAddress, { _, value ->
            when (value) {
                null -> BufferOrNone.Buffer(ArrayList<MessageAndContext>()).apply {
                    container.add(MessageAndContext(message, context))
                }
                is BufferOrNone.Buffer -> value.apply {
                    container.add(MessageAndContext(message, context))
                }
                is BufferOrNone.None -> value
            }
        })
        return clientBuffer is BufferOrNone.Buffer
    }

    private fun reapSubscriptions() {
        observableMap.cleanUp()
    }

    // TODO remove this User once webserver doesn't need it
    private val nodeUser = User(NODE_USER, NODE_USER, setOf())

    private fun ClientMessage.context(sessionId: Trace.SessionId): RpcAuthContext {
        val trace = Trace.newInstance(sessionId = sessionId)
        val externalTrace = externalTrace()
        val rpcActor = actorFrom(this)
        val impersonatedActor = impersonatedActor()
        return RpcAuthContext(InvocationContext.rpc(rpcActor.first, trace, externalTrace, impersonatedActor), rpcActor.second)
    }

    private fun actorFrom(message: ClientMessage): Pair<Actor, RpcPermissions> {
        val validatedUser = message.getStringProperty(Message.HDR_VALIDATED_USER) ?: throw IllegalArgumentException("Missing validated user from the Artemis message")
        val targetLegalIdentity = message.getStringProperty(RPCApi.RPC_TARGET_LEGAL_IDENTITY)?.let(CordaX500Name.Companion::parse) ?: nodeLegalName
        // TODO switch userService based on targetLegalIdentity
        val rpcUser = userService.getUser(validatedUser)
        return if (rpcUser != null) {
            Actor(Id(rpcUser.username), userService.id, targetLegalIdentity) to RpcPermissions(rpcUser.permissions)
        } else if (CordaX500Name.parse(validatedUser) == nodeLegalName) {
            // TODO remove this after Shell and WebServer will no longer need it
            Actor(Id(nodeUser.username), userService.id, targetLegalIdentity) to RpcPermissions(nodeUser.permissions)
        } else {
            throw IllegalArgumentException("Validated user '$validatedUser' is not an RPC user nor the NODE user")
        }
    }
}

// TODO replace this by creating a new CordaRPCImpl for each request, passing the context, after we fix Shell and WebServer
@JvmField
internal val CURRENT_RPC_CONTEXT: ThreadLocal<RpcAuthContext> = CurrentRpcContext()

internal class CurrentRpcContext : ThreadLocal<RpcAuthContext>() {

    override fun remove() {
        super.remove()
        MDC.clear()
    }

    override fun set(context: RpcAuthContext?) {
        when {
            context != null -> {
                super.set(context)
                // this is needed here as well because the Shell sets the context without going through the RpcServer
                context.invocation.pushToLoggingContext()
            }
            else -> remove()
        }
    }
}

/**
 * Returns a context specific to the current RPC call. Note that trying to call this function outside of an RPC will
 * throw. If you'd like to use the context outside of the call (e.g. in another thread) then pass the returned reference
 * around explicitly.
 * The [InvocationContext] does not include permissions.
 */
internal fun context(): InvocationContext = rpcContext().invocation

/**
 * Returns a context specific to the current RPC call. Note that trying to call this function outside of an RPC will
 * throw. If you'd like to use the context outside of the call (e.g. in another thread) then pass the returned reference
 * around explicitly.
 * The [RpcAuthContext] includes permissions.
 */
fun rpcContext(): RpcAuthContext = CURRENT_RPC_CONTEXT.get()

class ObservableSubscription(
        val subscription: Subscription
)

typealias ObservableSubscriptionMap = Cache<InvocationId, ObservableSubscription>

// We construct an observable context on each RPC request. If subsequently a nested Observable is
// encountered this same context is propagated by the instrumented KryoPool. This way all
// observations rooted in a single RPC will be muxed correctly. Note that the context construction
// itself is quite cheap.
class ObservableContext(
        val invocationId: InvocationId,
        val observableMap: ObservableSubscriptionMap,
        val clientAddressToObservables: SetMultimap<SimpleString, InvocationId>,
        val clientAddress: SimpleString,
        val serverControl: ActiveMQServerControl,
        val sessionAndProducerPool: LazyStickyPool<ArtemisProducer>,
        val observationSendExecutor: ExecutorService
) {
    private companion object {
        private val log = contextLogger()
    }

    private val serializationContextWithObservableContext = RpcServerObservableSerializer.createContext(this)

    fun sendMessage(serverToClient: RPCApi.ServerToClient) {
        try {
            sessionAndProducerPool.run(invocationId) {
                val artemisMessage = it.session.createMessage(false)
                serverToClient.writeToClientMessage(serializationContextWithObservableContext, artemisMessage)
                it.producer.send(clientAddress, artemisMessage)
                log.debug("<- RPC <- $serverToClient")
            }
        } catch (throwable: Throwable) {
            log.error("Failed to send message, kicking client. Message was $serverToClient", throwable)
            serverControl.closeConsumerConnectionsForAddress(clientAddress.toString())
        }
    }
}

object RpcServerObservableSerializer : Serializer<Observable<*>>() {
    private object RpcObservableContextKey

    private val log = LoggerFactory.getLogger(javaClass)
    fun createContext(observableContext: ObservableContext): SerializationContext {
        return RPC_SERVER_CONTEXT.withProperty(RpcServerObservableSerializer.RpcObservableContextKey, observableContext)
    }

    override fun read(kryo: Kryo?, input: Input?, type: Class<Observable<*>>?): Observable<Any> {
        throw UnsupportedOperationException()
    }

    override fun write(kryo: Kryo, output: Output, observable: Observable<*>) {
        val observableId = InvocationId.newInstance()
        val observableContext = kryo.context[RpcObservableContextKey] as ObservableContext
        output.writeInvocationId(observableId)
        val observableWithSubscription = ObservableSubscription(
                // We capture [observableContext] in the subscriber. Note that all synchronisation/kryo borrowing
                // must be done again within the subscriber
                subscription = observable.materialize().subscribe(
                        object : Subscriber<Notification<*>>() {
                            override fun onNext(observation: Notification<*>) {
                                if (!isUnsubscribed) {
                                    observableContext.observationSendExecutor.submit {
                                        observableContext.sendMessage(RPCApi.ServerToClient.Observation(observableId, observation))
                                    }
                                }
                            }

                            override fun onError(exception: Throwable) {
                                log.error("onError called in materialize()d RPC Observable", exception)
                            }

                            override fun onCompleted() {
                            }
                        }
                )
        )
        observableContext.clientAddressToObservables.put(observableContext.clientAddress, observableId)
        observableContext.observableMap.put(observableId, observableWithSubscription)
    }

    private fun Output.writeInvocationId(id: InvocationId) {

        writeString(id.value)
        writeLong(id.timestamp.toEpochMilli())
    }
}
