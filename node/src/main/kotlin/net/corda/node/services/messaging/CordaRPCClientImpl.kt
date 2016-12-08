package net.corda.node.services.messaging

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.common.cache.CacheBuilder
import net.corda.core.ErrorOr
import net.corda.core.bufferUntilSubscribed
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.RPCReturnsObservables
import net.corda.core.random63BitValue
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.debug
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
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.*
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

    @GuardedBy("sessionLock")
    private val addressToQueueObservables = CacheBuilder.newBuilder().build<String, QueuedObservable>()

    private var producer: ClientProducer? = null

    private inner class ObservableDeserializer(private val qName: String,
                                               private val rpcName: String,
                                               private val rpcLocation: Throwable) : Serializer<Observable<Any>>() {
        override fun read(kryo: Kryo, input: Input, type: Class<Observable<Any>>): Observable<Any> {
            val handle = input.readInt(true)
            val ob = sessionLock.withLock {
                addressToQueueObservables.getIfPresent(qName) ?: QueuedObservable(qName, rpcName, rpcLocation, this).apply {
                    addressToQueueObservables.put(qName, this)
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

            // sendRequest may return a reconfigured Kryo if the method returns observables.
            val kryo: Kryo = sendRequest(args, location, method) ?: createRPCKryo()
            val next: ErrorOr<*> = receiveResponse(kryo, method, timeout)
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

        private fun sendRequest(args: Array<out Any>?, location: Throwable, method: Method): Kryo? {
            // We could of course also check the return type of the method to see if it's Observable, but I'd
            // rather haved the annotation be used consistently.
            val returnsObservables = method.isAnnotationPresent(RPCReturnsObservables::class.java)

            sessionLock.withLock {
                val msg: ClientMessage = createMessage(method)
                val kryo = if (returnsObservables) maybePrepareForObservables(location, method, msg) else null
                val serializedArgs = try {
                    (args ?: emptyArray<Any?>()).serialize(createRPCKryo())
                } catch (e: KryoException) {
                    throw RPCException("Could not serialize RPC arguments", e)
                }
                msg.writeBodyBufferBytes(serializedArgs.bytes)
                producer!!.send(ArtemisMessagingComponent.RPC_REQUESTS_QUEUE, msg)
                return kryo
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
            return createRPCKryo(observableSerializer = ObservableDeserializer(observationsQueueName, method.name, location))
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
                                         private val rpcLocation: Throwable,
                                         private val observableDeserializer: ObservableDeserializer) {
        private val root = PublishSubject.create<MarshalledObservation>()
        private val rootShared = root.doOnUnsubscribe { close() }.share()

        // This could be made more efficient by using a specialised IntMap
        private val observables = HashMap<Int, Observable<Any>>()

        private var consumer: ClientConsumer? = sessionLock.withLock { session.createConsumer(qName) }.setMessageHandler { deliver(it) }

        @Synchronized
        fun getForHandle(handle: Int): Observable<Any> {
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
                 */
                rootShared.filter { it.forHandle == handle }.map { it.what }.bufferUntilSubscribed().dematerialize<Any>().share()
            }
        }

        private fun deliver(msg: ClientMessage) {
            msg.acknowledge()
            val kryo = createRPCKryo(observableSerializer = observableDeserializer)
            val received: MarshalledObservation = msg.deserialize(kryo)
            rpcLog.debug { "<- Observable [$rpcName] <- Received $received" }
            synchronized(this) {
                // Force creation of the buffer if it doesn't already exist.
                getForHandle(received.forHandle)
                root.onNext(received)
            }
        }

        @Synchronized
        fun close() {
            rpcLog.debug("Closing queue observable for call to $rpcName : $qName")
            consumer?.close()
            consumer = null
            sessionLock.withLock { session.deleteQueue(qName) }
        }

        @Suppress("UNUSED")
        fun finalize() {
            val c = synchronized(this) { consumer }
            if (c != null) {
                rpcLog.warn("A hot observable returned from an RPC ($rpcName) was never subscribed to or explicitly closed. " +
                        "This wastes server-side resources because it was queueing observations for retrieval. " +
                        "It is being closed now, but please adjust your code to cast the observable to AutoCloseable and then close it explicitly.", rpcLocation)
                c.close()
            }
        }
    }
    //endregion
}
