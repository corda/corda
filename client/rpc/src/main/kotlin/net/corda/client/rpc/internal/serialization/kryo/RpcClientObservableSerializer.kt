package net.corda.client.rpc.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.client.rpc.internal.ObservableContext
import net.corda.core.context.Trace
import net.corda.core.serialization.SerializationContext
import net.corda.nodeapi.RPCApi
import rx.Notification
import rx.Observable
import rx.subjects.UnicastSubject
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

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
        val observableId = input.readInvocationId() ?: throw IllegalStateException("Unable to read invocationId from Input.")
        val observable = UnicastSubject.create<Notification<*>>()
        require(observableContext.observableMap.getIfPresent(observableId) == null) {
            "Multiple Observables arrived with the same ID $observableId"
        }
        val rpcCallSite = getRpcCallSite(kryo, observableContext)
        observableContext.observableMap.put(observableId, observable)
        observableContext.callSiteMap?.put(observableId, rpcCallSite)
        // We pin all Observables into a hard reference store (rooted in the RPC proxy) on subscription so that users
        // don't need to store a reference to the Observables themselves.
        return pinInSubscriptions(observable, observableContext.hardReferenceStore).doOnUnsubscribe {
            // This causes Future completions to give warnings because the corresponding OnComplete sent from the server
            // will arrive after the client unsubscribes from the observable and consequently invalidates the mapping.
            // The unsubscribe is due to [ObservableToFuture]'s use of first().
            observableContext.observableMap.invalidate(observableId)
        }.dematerialize()
    }

    private fun Input.readInvocationId(): Trace.InvocationId? {

        val value = readString() ?: return null
        val timestamp = readLong()
        return Trace.InvocationId(value, Instant.ofEpochMilli(timestamp))
    }

    override fun write(kryo: Kryo, output: Output, observable: Observable<*>) {
        throw UnsupportedOperationException("Cannot serialise Observables on the client side")
    }

    private fun getRpcCallSite(kryo: Kryo, observableContext: ObservableContext): Throwable? {
        val rpcRequestOrObservableId = kryo.context[RPCApi.RpcRequestOrObservableIdKey] as Trace.InvocationId
        return observableContext.callSiteMap?.get(rpcRequestOrObservableId)
    }
}
