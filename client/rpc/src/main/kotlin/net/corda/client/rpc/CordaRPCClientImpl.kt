package net.corda.client.rpc

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.pool.KryoPool
import com.google.common.cache.CacheBuilder
import net.corda.core.ErrorOr
import net.corda.core.bufferUntilSubscribed
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.RPCReturnsObservables
import net.corda.core.random63BitValue
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.debug
import net.corda.nodeapi.*
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException
import org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import rx.Observable
import rx.subjects.PublishSubject
import java.io.Closeable
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.concurrent.GuardedBy
import javax.annotation.concurrent.ThreadSafe
import kotlin.concurrent.withLock
import kotlin.reflect.jvm.javaMethod

/**
 * Core RPC engine implementation, to learn how to use RPC you should be looking at [CordaRPCClient].
 *
 * # Design notes
 *
 * The way RPCs are handled is fairly standard except for the handling of observables. When an RPC might return
 * an [Observable] it is specially tagged. This causes the client to create a new transient queue for the
 * receiving of observables and their observations with a random ID in the name. This ID is sent to the server in
 * a message header. All observations are sent via this single queue.
 *
 * The reason for doing it this way and not the more obvious approach of one-queue-per-observable is that we want
 * the queues to be *transient*, meaning their lifetime in the broker is tied to the session that created them.
 * A server side observable and its associated queue is not a cost-free thing, let alone the memory and resources
 * needed to actually generate the observations themselves, therefore we want to ensure these cannot leak. A
 * transient queue will be deleted automatically if the client session terminates, which by default happens on
 * disconnect but can also be configured to happen after a short delay (this allows clients to e.g. switch IP
 * address). On the server the deletion of the observations queue triggers unsubscription from the associated
 * observables, which in turn may then be garbage collected.
 *
 * Creating a transient queue requires a roundtrip to the broker and thus doing an RPC that could return
 * observables takes two server roundtrips instead of one. That's why we require RPCs to be marked with
 * [RPCReturnsObservables] as needing this special treatment instead of always doing it.
 *
 * If the Artemis/JMS APIs allowed us to create transient queues assigned to someone else then we could
 * potentially use a different design in which the node creates new transient queues (one per observable) on the
 * fly. The client would then have to watch out for this and start consuming those queues as they were created.
 *
 * We use one queue per RPC because we don't know ahead of time how many observables the server might return and
 * often the server doesn't know either, which pushes towards a single queue design, but at the same time the
 * processing of observations returned by an RPC might be striped across multiple threads and we'd like
 * backpressure management to not be scoped per client process but with more granularity. So we end up with
 * a compromise where the unit of backpressure management is the response to a single RPC.
 *
 * TODO: Backpressure isn't propagated all the way through the MQ broker at the moment.
 */
class CordaRPCClientImpl(private val session: ClientSession,
                         private val sessionLock: ReentrantLock,
                         private val username: String) {
    companion object {
        private val closeableCloseMethod = Closeable::close.javaMethod
        private val autocloseableCloseMethod = AutoCloseable::close.javaMethod
    }

    /**
     * Builds a proxy for the given type, which must descend from [RPCOps].
     *
     * @see CordaRPCClient.proxy for more information about how to use the proxies.
     */
    fun <T : RPCOps> proxyFor(rpcInterface: Class<T>, timeout: Duration? = null, minVersion: Int = 0): T {
        sessionLock.withLock {
            if (producer == null)
                producer = session.createProducer()
        }
        val proxyImpl = RPCProxyHandler(timeout)
        @Suppress("UNCHECKED_CAST")
        val proxy = Proxy.newProxyInstance(rpcInterface.classLoader, arrayOf(rpcInterface, Closeable::class.java), proxyImpl) as T
        proxyImpl.serverProtocolVersion = proxy.protocolVersion
        if (minVersion > proxyImpl.serverProtocolVersion)
            throw RPCException("Requested minimum protocol version $minVersion is higher than the server's supported protocol version (${proxyImpl.serverProtocolVersion})")
        return proxy
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //region RPC engine
    //
    // You can find docs on all this in the api doc for the proxyFor method, and in the docsite.

    // Utility to quickly suck out the contents of an Artemis message. There's probably a more efficient way to
    // do this.
    private fun <T : Any> ClientMessage.deserialize(kryo: Kryo): T = ByteArray(bodySize).apply { bodyBuffer.readBytes(this) }.deserialize(kryo)

    // We by default use a weak reference so GC can happen, otherwise they persist for the life of the client.
    @GuardedBy("sessionLock")
    private val addressToQueuedObservables = CacheBuilder.newBuilder().weakValues().build<String, QueuedObservable>()
    // This is used to hold a reference counted hard reference when we know there are subscribers.
    private val hardReferencesToQueuedObservables = Collections.synchronizedSet(mutableSetOf<QueuedObservable>())

    private var producer: ClientProducer? = null

    class ObservableDeserializer : Serializer<Observable<Any>>() {
        override fun read(kryo: Kryo, input: Input, type: Class<Observable<Any>>): Observable<Any> {
            val qName = kryo.context[RPCKryoQNameKey] as String
            val rpcName = kryo.context[RPCKryoMethodNameKey] as String
            val rpcLocation = kryo.context[RPCKryoLocationKey] as Throwable
            val rpcClient = kryo.context[RPCKryoClientKey] as CordaRPCClientImpl
            val handle = input.readInt(true)
            val ob = rpcClient.sessionLock.withLock {
                rpcClient.addressToQueuedObservables.getIfPresent(qName) ?: rpcClient.QueuedObservable(qName, rpcName, rpcLocation).apply {
                    rpcClient.addressToQueuedObservables.put(qName, this)
                }
            }
            val result = ob.getForHandle(handle)
            rpcLog.debug { "Deserializing and connecting a new observable for $rpcName on $qName: $result" }
            return result
        }

        override fun write(kryo: Kryo, output: Output, `object`: Observable<Any>) {
            throw UnsupportedOperationException("not implemented")
        }
    }

    /**
     * The proxy class returned to the client is auto-generated on the fly by the java.lang.reflect Proxy
     * infrastructure. The JDK Proxy class writes bytecode into memory for a class that implements the requested
     * interfaces and then routes all method calls to the invoke method below in a conveniently reified form.
     * We can then easily take the data about the method call and turn it into an RPC. This avoids the need
     * for the compile-time code generation which is so common in RPC systems.
     */
    @ThreadSafe
    private inner class RPCProxyHandler(private val timeout: Duration?) : InvocationHandler, Closeable {
        private val proxyId = random63BitValue()
        private val consumer: ClientConsumer

        var serverProtocolVersion = 0

        init {
            val proxyAddress = constructAddress(proxyId)
            consumer = sessionLock.withLock {
                session.createTemporaryQueue(proxyAddress, proxyAddress)
                session.createConsumer(proxyAddress)
            }
        }

        private fun constructAddress(addressId: Long) = "${ArtemisMessagingComponent.CLIENTS_PREFIX}$username.rpc.$addressId"

        @Synchronized
        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            if (isCloseInvocation(method)) {
                close()
                return null
            }
            if (method.name == "toString" && args == null)
                return "Client RPC proxy"

            if (consumer.isClosed)
                throw RPCException("RPC Proxy is closed")

            // All invoked methods on the proxy end up here.
            val location = Throwable()
            rpcLog.debug {
                val argStr = args?.joinToString() ?: ""
                "-> RPC -> ${method.name}($argStr): ${method.returnType}"
            }

            checkMethodVersion(method)

            val msg: ClientMessage = createMessage(method)
            // We could of course also check the return type of the method to see if it's Observable, but I'd
            // rather haved the annotation be used consistently.
            val returnsObservables = method.isAnnotationPresent(RPCReturnsObservables::class.java)
            val kryo = if (returnsObservables) maybePrepareForObservables(location, method, msg) else createRPCKryoForDeserialization(this@CordaRPCClientImpl)
            val next: ErrorOr<*> = try {
                sendRequest(args, msg)
                receiveResponse(kryo, method, timeout)
            } finally {
                releaseRPCKryoForDeserialization(kryo)
            }
            rpcLog.debug { "<- RPC <- ${method.name} = $next" }
            return unwrapOrThrow(next)
        }

        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private fun unwrapOrThrow(next: ErrorOr<*>): Any? {
            val ex = next.error
            if (ex != null) {
                // Replace the stack trace because that's an implementation detail of the server that isn't so
                // helpful to the user who wants to see where the error was on their side, and serialising stack
                // frame objects is a bit annoying. We slice it here to avoid the invoke() machinery being exposed.
                // The resulting exception looks like it was thrown from inside the called method.
                (ex as java.lang.Throwable).stackTrace = java.lang.Throwable().stackTrace.let { it.sliceArray(1..it.size - 1) }
                throw ex
            } else {
                return next.value
            }
        }

        private fun receiveResponse(kryo: Kryo, method: Method, timeout: Duration?): ErrorOr<*> {
            val artemisMessage: ClientMessage =
                    if (timeout == null)
                        consumer.receive() ?: throw ActiveMQObjectClosedException()
                    else
                        consumer.receive(timeout.toMillis()) ?: throw RPCException.DeadlineExceeded(method.name)
            artemisMessage.acknowledge()
            val next = artemisMessage.deserialize<ErrorOr<*>>(kryo)
            return next
        }

        private fun sendRequest(args: Array<out Any>?, msg: ClientMessage) {
            sessionLock.withLock {
                val argsKryo = createRPCKryoForDeserialization(this@CordaRPCClientImpl)
                val serializedArgs = try {
                    (args ?: emptyArray<Any?>()).serialize(argsKryo)
                } catch (e: KryoException) {
                    throw RPCException("Could not serialize RPC arguments", e)
                } finally {
                    releaseRPCKryoForDeserialization(argsKryo)
                }
                msg.writeBodyBufferBytes(serializedArgs.bytes)
                producer!!.send(ArtemisMessagingComponent.RPC_REQUESTS_QUEUE, msg)
            }
        }

        private fun maybePrepareForObservables(location: Throwable, method: Method, msg: ClientMessage): Kryo {
            // Create a temporary queue just for the emissions on any observables that are returned.
            val observationsId = random63BitValue()
            val observationsQueueName = constructAddress(observationsId)
            session.createTemporaryQueue(observationsQueueName, observationsQueueName)
            msg.putLongProperty(ClientRPCRequestMessage.OBSERVATIONS_TO, observationsId)
            // And make sure that we deserialise observable handles so that they're linked to the right
            // queue. Also record a bit of metadata for debugging purposes.
            return createRPCKryoForDeserialization(this@CordaRPCClientImpl, observationsQueueName, method.name, location)
        }

        private fun createMessage(method: Method): ClientMessage {
            return session.createMessage(false).apply {
                putStringProperty(ClientRPCRequestMessage.METHOD_NAME, method.name)
                putLongProperty(ClientRPCRequestMessage.REPLY_TO, proxyId)
                // Use the magic deduplication property built into Artemis as our message identity too
                putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
            }
        }

        private fun checkMethodVersion(method: Method) {
            val methodVersion = method.getAnnotation(RPCSinceVersion::class.java)?.version ?: 0
            if (methodVersion > serverProtocolVersion)
                throw UnsupportedOperationException("Method ${method.name} was added in RPC protocol version $methodVersion but the server is running $serverProtocolVersion")
        }

        private fun isCloseInvocation(method: Method) = method == closeableCloseMethod || method == autocloseableCloseMethod

        override fun close() {
            consumer.close()
            sessionLock.withLock { session.deleteQueue(constructAddress(proxyId)) }
        }

        override fun toString() = "Corda RPC Proxy listening on queue ${constructAddress(proxyId)}"
    }

    /**
     * When subscribed to, starts consuming from the given queue name and demultiplexing the observables being
     * sent to it. The server queue is moved into in-memory buffers (one per attached server-side observable)
     * until drained through a subscription. When the subscriptions are all gone, the server-side queue is deleted.
     */
    @ThreadSafe
    private inner class QueuedObservable(private val qName: String,
                                         private val rpcName: String,
                                         private val rpcLocation: Throwable) {
        private val root = PublishSubject.create<MarshalledObservation>()
        private val rootShared = root.doOnUnsubscribe { close() }.share()

        // This could be made more efficient by using a specialised IntMap
        // When handling this map we don't synchronise on [this], otherwise there is a race condition between close() and deliver()
        private val observables = Collections.synchronizedMap(HashMap<Int, Observable<Any>>())

        @GuardedBy("sessionLock")
        private var consumer: ClientConsumer? = null

        private val referenceCount = AtomicInteger(0)

        // We have to create a weak reference, otherwise we cannot be GC'd.
        init {
            val weakThis = WeakReference<QueuedObservable>(this)
            consumer = sessionLock.withLock { session.createConsumer(qName) }.setMessageHandler { weakThis.get()?.deliver(it) }
        }

        /**
         * We have to reference count subscriptions to the returned [Observable]s to prevent early GC because we are
         * weak referenced.
         *
         * Derived [Observables] (e.g. filtered etc) hold a strong reference to the original, but for example, if
         * the pattern as follows is used, the original passes out of scope and the direction of reference is from the
         * original to the [Observer].  We use the reference counting to allow for this pattern.
         *
         * val observationsSubject = PublishSubject.create<Observation>()
         * originalObservable.subscribe(observationsSubject)
         * return observationsSubject
         */
        private fun refCountUp() {
            if (referenceCount.andIncrement == 0) {
                hardReferencesToQueuedObservables.add(this)
            }
        }

        private fun refCountDown() {
            if (referenceCount.decrementAndGet() == 0) {
                hardReferencesToQueuedObservables.remove(this)
            }
        }

        fun getForHandle(handle: Int): Observable<Any> {
            synchronized(observables) {
                return observables.getOrPut(handle) {
                    /**
                     * Note that the order of bufferUntilSubscribed() -> dematerialize() is very important here.
                     *
                     * In particular doing it the other way around may result in the following edge case:
                     * The RPC returns two (or more) Observables. The first Observable unsubscribes *during serialisation*,
                     * before the second one is hit, causing the [rootShared] to unsubscribe and consequently closing
                     * the underlying artemis queue, even though the second Observable was not even registered.
                     *
                     * The buffer -> dematerialize order ensures that the Observable may not unsubscribe until the caller
                     * subscribes, which must be after full deserialisation and registering of all top level Observables.
                     *
                     * In addition, when subscribe and unsubscribe is called on the [Observable] returned here, we
                     * reference count a hard reference to this [QueuedObservable] to prevent premature GC.
                     */
                    rootShared.filter { it.forHandle == handle }.map { it.what }.bufferUntilSubscribed().dematerialize<Any>().doOnSubscribe { refCountUp() }.doOnUnsubscribe { refCountDown() }.share()
                }
            }
        }

        private fun deliver(msg: ClientMessage) {
            sessionLock.withLock { msg.acknowledge() }
            val kryo = createRPCKryoForDeserialization(this@CordaRPCClientImpl, qName, rpcName, rpcLocation)
            val received: MarshalledObservation = try {
                msg.deserialize(kryo)
            } finally {
                releaseRPCKryoForDeserialization(kryo)
            }
            rpcLog.debug { "<- Observable [$rpcName] <- Received $received" }
            synchronized(observables) {
                // Force creation of the buffer if it doesn't already exist.
                getForHandle(received.forHandle)
                root.onNext(received)
            }
        }

        fun close() {
            sessionLock.withLock {
                if (consumer != null) {
                    rpcLog.debug("Closing queue observable for call to $rpcName : $qName")
                    consumer?.close()
                    consumer = null
                    session.deleteQueue(qName)
                }
            }
        }

        @Suppress("UNUSED")
        fun finalize() {
            val closed = sessionLock.withLock {
                if (consumer != null) {
                    consumer!!.close()
                    consumer = null
                    true
                } else
                    false
            }
            if (closed) {
                rpcLog.warn("""A hot observable returned from an RPC ($rpcName) was never subscribed to.
                               This wastes server-side resources because it was queueing observations for retrieval.
                               It is being closed now, but please adjust your code to call .notUsed() on the observable
                               to close it explicitly. (Java users: subscribe to it then unsubscribe). This warning
                               will appear less frequently in future versions of the platform and you can ignore it
                               if you want to.
                            """.trimIndent().replace('\n', ' '), rpcLocation)
            }
        }
    }
    //endregion
}

private val rpcDesKryoPool = KryoPool.Builder { RPCKryo(CordaRPCClientImpl.ObservableDeserializer()) }.build()

fun createRPCKryoForDeserialization(rpcClient: CordaRPCClientImpl, qName: String? = null, rpcName: String? = null, rpcLocation: Throwable? = null): Kryo {
    val kryo = rpcDesKryoPool.borrow()
    kryo.context.put(RPCKryoClientKey, rpcClient)
    kryo.context.put(RPCKryoQNameKey, qName)
    kryo.context.put(RPCKryoMethodNameKey, rpcName)
    kryo.context.put(RPCKryoLocationKey, rpcLocation)
    return kryo
}

fun releaseRPCKryoForDeserialization(kryo: Kryo) {
    rpcDesKryoPool.release(kryo)
}
