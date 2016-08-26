package com.r3corda.node.services.messaging

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.common.collect.HashMultimap
import com.r3corda.core.ErrorOr
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.core.utilities.debug
import com.r3corda.node.utilities.AffinityExecutor
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import rx.Notification
import rx.Observable
import rx.Subscription
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicInteger

// TODO: Exposing the authenticated message sender.

/**
 * Intended to service transient clients only (not p2p nodes) for short-lived, transient request/response pairs.
 * If you need robustness, this is the wrong system. If you don't want a response, this is probably the
 * wrong system (you could just send a message). If you want complex customisation of how requests/responses
 * are handled, this is probably the wrong system.
 */
abstract class RPCDispatcher(val target: Any) {
    private val methodTable = target.javaClass.declaredMethods.associateBy { it.name }
    private val queueToSubscription = HashMultimap.create<String, Subscription>()

    // Created afresh for every RPC that is annotated as returning observables. Every time an observable is
    // encountered either in the RPC response or in an object graph that is being emitted by one of those
    // observables, the handle counter is incremented and the server-side observable is subscribed to. The
    // materialized observations are then sent to the queue the client created where they can be picked up.
    //
    // When the observables are deserialised on the client side, the handle is read from the byte stream and
    // the queue is filtered to extract just those observations.
    private inner class ObservableSerializer(private val toQName: String) : Serializer<Observable<Any>>() {
        private val handleCounter = AtomicInteger()

        override fun read(kryo: Kryo, input: Input, type: Class<Observable<Any>>): Observable<Any> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun write(kryo: Kryo, output: Output, obj: Observable<Any>) {
            val handle = handleCounter.andIncrement
            output.writeInt(handle, true)
            // Observables can do three kinds of callback: "next" with a content object, "completed" and "error".
            // Materializing the observable converts these three kinds of callback into a single stream of objects
            // representing what happened, which is useful for us to send over the wire.
            val subscription = obj.materialize().subscribe { materialised: Notification<out Any> ->
                val newKryo = createRPCKryo(observableSerializer = this@ObservableSerializer)
                val bits = MarshalledObservation(handle, materialised).serialize(newKryo)
                rpcLog.debug("RPC sending observation: $materialised")
                send(bits, toQName)
            }
            synchronized(queueToSubscription) {
                queueToSubscription.put(toQName, subscription)
            }
        }
    }

    fun dispatch(msg: ClientRPCRequestMessage) {
        val (argBytes, replyTo, observationsTo, name) = msg
        val maybeArgs = argBytes.deserialize<Array<Any>>()

        rpcLog.debug { "-> RPC -> $name(${maybeArgs.joinToString()})    [reply to $replyTo]" }
        val response: ErrorOr<Any?> = ErrorOr.catch {
            val method = methodTable[name] ?: throw RPCException("Received RPC for unknown method $name - possible client/server version skew?")

            if (method.isAnnotationPresent(RPCReturnsObservables::class.java) && observationsTo == null)
                throw RPCException("Received RPC without any destination for observations, but the RPC returns observables")

            try {
                method.invoke(target, *maybeArgs)
            } catch (e: InvocationTargetException) {
                throw e.cause!!
            }
        }
        rpcLog.debug { "<- RPC <- $name = $response " }

        val kryo = createRPCKryo(observableSerializer = if (observationsTo != null) ObservableSerializer(observationsTo) else null)

        // Serialise, or send back a simple serialised ErrorOr structure if we couldn't do it.
        val responseBits = try {
            response.serialize(kryo)
        } catch (e: KryoException) {
            rpcLog.error("Failed to respond to inbound RPC $name", e)
            ErrorOr.of(e).serialize(kryo)
        }
        send(responseBits, replyTo)
    }

    abstract fun send(bits: SerializedBytes<*>, toAddress: String)

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
                    dispatch(rpcMessage)
                } catch(e: RPCException) {
                    rpcLog.warn("Received malformed client RPC message: ${e.message}")
                    rpcLog.trace("RPC exception", e)
                } catch(e: Throwable) {
                    rpcLog.error("Uncaught exception when dispatching client RPC", e)
                }
            }
        }
    }
}