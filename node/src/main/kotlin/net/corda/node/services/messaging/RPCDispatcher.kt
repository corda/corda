package net.corda.node.services.messaging

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.pool.KryoPool
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.HashMultimap
import net.corda.core.ErrorOr
import net.corda.core.crypto.commonName
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.RPCReturnsObservables
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.debug
import net.corda.node.services.RPCUserService
import net.corda.node.utilities.AffinityExecutor
import net.corda.nodeapi.*
import net.corda.nodeapi.ArtemisMessagingComponent.Companion.NODE_USER
import org.apache.activemq.artemis.api.core.Message
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.bouncycastle.asn1.x500.X500Name
import rx.Notification
import rx.Observable
import rx.Subscription
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Intended to service transient clients only (not p2p nodes) for short-lived, transient request/response pairs.
 * If you need robustness, this is the wrong system. If you don't want a response, this is probably the
 * wrong system (you could just send a message). If you want complex customisation of how requests/responses
 * are handled, this is probably the wrong system.
 */
// TODO remove the nodeLegalName parameter once the webserver doesn't need special privileges
abstract class RPCDispatcher(val ops: RPCOps, val userService: RPCUserService, val nodeLegalName: String) {
    // Throw an exception if there are overloaded methods
    private val methodTable = ops.javaClass.declaredMethods.groupBy { it.name }.mapValues { it.value.single() }

    private val queueToSubscription = HashMultimap.create<String, Subscription>()

    private val handleCounter = AtomicInteger()

    // Created afresh for every RPC that is annotated as returning observables. Every time an observable is
    // encountered either in the RPC response or in an object graph that is being emitted by one of those
    // observables, the handle counter is incremented and the server-side observable is subscribed to. The
    // materialized observations are then sent to the queue the client created where they can be picked up.
    //
    // When the observables are deserialised on the client side, the handle is read from the byte stream and
    // the queue is filtered to extract just those observations.
    class ObservableSerializer : Serializer<Observable<Any>>() {
        private fun toQName(kryo: Kryo): String = kryo.context[RPCKryoQNameKey] as String
        private fun toDispatcher(kryo: Kryo): RPCDispatcher = kryo.context[RPCKryoDispatcherKey] as RPCDispatcher

        override fun read(kryo: Kryo, input: Input, type: Class<Observable<Any>>): Observable<Any> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun write(kryo: Kryo, output: Output, obj: Observable<Any>) {
            val qName = toQName(kryo)
            val dispatcher = toDispatcher(kryo)
            val handle = dispatcher.handleCounter.andIncrement
            output.writeInt(handle, true)
            // Observables can do three kinds of callback: "next" with a content object, "completed" and "error".
            // Materializing the observable converts these three kinds of callback into a single stream of objects
            // representing what happened, which is useful for us to send over the wire.
            val subscription = obj.materialize().subscribe { materialised: Notification<out Any> ->
                val newKryo = createRPCKryoForSerialization(qName, dispatcher)
                val bits = try {
                    MarshalledObservation(handle, materialised).serialize(newKryo)
                } finally {
                    releaseRPCKryoForSerialization(newKryo)
                }
                rpcLog.debug("RPC sending observation: $materialised")
                dispatcher.send(bits, qName)
            }
            synchronized(dispatcher.queueToSubscription) {
                dispatcher.queueToSubscription.put(qName, subscription)
            }
        }
    }

    fun dispatch(msg: ClientRPCRequestMessage) {
        val (argsBytes, replyTo, observationsTo, methodName) = msg

        val response: ErrorOr<Any> = ErrorOr.catch {
            val method = methodTable[methodName] ?: throw RPCException("Received RPC for unknown method $methodName - possible client/server version skew?")
            if (method.isAnnotationPresent(RPCReturnsObservables::class.java) && observationsTo == null)
                throw RPCException("Received RPC without any destination for observations, but the RPC returns observables")

            val kryo = createRPCKryoForSerialization(observationsTo, this)
            val args = try {
                argsBytes.deserialize(kryo)
            } finally {
                releaseRPCKryoForSerialization(kryo)
            }

            rpcLog.debug { "-> RPC -> $methodName(${args.joinToString()})    [reply to $replyTo]" }

            try {
                method.invoke(ops, *args)
            } catch (e: InvocationTargetException) {
                throw e.cause!!
            }
        }
        rpcLog.debug { "<- RPC <- $methodName = $response " }

        // Serialise, or send back a simple serialised ErrorOr structure if we couldn't do it.
        val kryo = createRPCKryoForSerialization(observationsTo, this)
        val responseBits = try {
            response.serialize(kryo)
        } catch (e: KryoException) {
            rpcLog.error("Failed to respond to inbound RPC $methodName", e)
            ErrorOr.of(e).serialize(kryo)
        } finally {
            releaseRPCKryoForSerialization(kryo)
        }
        send(responseBits, replyTo)
    }

    abstract fun send(data: SerializedBytes<*>, toAddress: String)

    fun start(rpcConsumer: ClientConsumer, rpcNotificationConsumer: ClientConsumer?, onExecutor: AffinityExecutor) {
        rpcNotificationConsumer?.setMessageHandler { msg ->
            val qName = msg.getStringProperty("_AMQ_RoutingName")
            val subscriptions = synchronized(queueToSubscription) {
                queueToSubscription.removeAll(qName)
            }
            if (subscriptions.isNotEmpty()) {
                rpcLog.debug("Observable queue was deleted, unsubscribing: $qName")
                subscriptions.forEach { it.unsubscribe() }
            }
        }
        rpcConsumer.setMessageHandler { msg ->
            msg.acknowledge()
            // All RPCs run on the main server thread, in order to avoid running concurrently with
            // potentially state changing requests from other nodes and each other. If we need to
            // give better latency to client RPCs in future we could use an executor that supports
            // job priorities.
            onExecutor.execute {
                try {
                    val rpcMessage = msg.toRPCRequestMessage()
                    CURRENT_RPC_USER.set(rpcMessage.user)
                    dispatch(rpcMessage)
                } catch(e: RPCException) {
                    rpcLog.warn("Received malformed client RPC message: ${e.message}")
                    rpcLog.trace("RPC exception", e)
                } catch(e: Throwable) {
                    rpcLog.error("Uncaught exception when dispatching client RPC", e)
                } finally {
                    CURRENT_RPC_USER.remove()
                }
            }
        }
    }

    private fun ClientMessage.requiredString(name: String): String {
        return getStringProperty(name) ?: throw RPCException("missing $name property")
    }

    /** Convert an Artemis [ClientMessage] to a MQ-neutral [ClientRPCRequestMessage]. */
    private fun ClientMessage.toRPCRequestMessage(): ClientRPCRequestMessage {
        val user = getUser(this)
        val replyTo = getReturnAddress(user, ClientRPCRequestMessage.REPLY_TO, true)!!
        val observationsTo = getReturnAddress(user, ClientRPCRequestMessage.OBSERVATIONS_TO, false)
        val argBytes = ByteArray(bodySize).apply { bodyBuffer.readBytes(this) }
        if (argBytes.isEmpty()) {
            throw RPCException("empty serialized args")
        }
        val methodName = requiredString(ClientRPCRequestMessage.METHOD_NAME)
        return ClientRPCRequestMessage(SerializedBytes(argBytes), replyTo, observationsTo, methodName, user)
    }

    // TODO remove this User once webserver doesn't need it
    private val nodeUser = User(NODE_USER, NODE_USER, setOf())

    @VisibleForTesting
    protected open fun getUser(message: ClientMessage): User {
        val validatedUser = message.requiredString(Message.HDR_VALIDATED_USER.toString())
        val rpcUser = userService.getUser(validatedUser)
        if (rpcUser != null) {
            return rpcUser
        } else {
            try {
                if (X500Name(validatedUser) == X500Name(nodeLegalName)) {
                    return nodeUser
                }
            } catch(ex: IllegalArgumentException) {
                // Can't parse the validated user as an X500 name, so can't match the node legal name.
                // Fall through to exception below.
            }
            throw IllegalArgumentException("Validated user '$validatedUser' is not an RPC user nor the NODE user")
        }
    }

    private fun ClientMessage.getReturnAddress(user: User, property: String, required: Boolean): String? {
        return if (containsProperty(property)) {
            "${ArtemisMessagingComponent.CLIENTS_PREFIX}${user.username}.rpc.${getLongProperty(property)}"
        } else {
            if (required) throw RPCException("missing $property property") else null
        }
    }
}

private val rpcSerKryoPool = KryoPool.Builder { RPCKryo(RPCDispatcher.ObservableSerializer()) }.build()

fun createRPCKryoForSerialization(qName: String? = null, dispatcher: RPCDispatcher? = null): Kryo {
    val kryo = rpcSerKryoPool.borrow()
    kryo.context.put(RPCKryoQNameKey, qName)
    kryo.context.put(RPCKryoDispatcherKey, dispatcher)
    return kryo
}

fun releaseRPCKryoForSerialization(kryo: Kryo) {
    rpcSerKryoPool.release(kryo)
}
