package net.corda.client.rpc.internal.serialization.amqp


import net.corda.client.rpc.internal.ObservableContext

import net.corda.core.context.Trace
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.serialization.amqp.*
import org.apache.qpid.proton.codec.Data
import rx.Notification
import rx.Observable
import rx.subjects.UnicastSubject
import java.io.NotSerializableException
import java.lang.reflect.Type
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.transaction.NotSupportedException

class RpcClientObservableSerializer : CustomSerializer.Implements<Observable<*>>(Observable::class.java){
    private object RpcObservableContextKey

    companion object {
        fun createContext(
                observableContext: ObservableContext,
                context: SerializationContext = SerializationDefaults.RPC_CLIENT_CONTEXT
        ) = context.withProperty (
                    RpcClientObservableSerializer.RpcObservableContextKey, observableContext)
    }

    private fun <T> pinInSubscriptions(observable: Observable<T>, hardReferenceStore: MutableSet<Observable<*>>): Observable<T> {
        val refCount = AtomicInteger(0)
        return observable.doOnSubscribe {
            if (refCount.getAndIncrement() == 0) {
                require(hardReferenceStore.add(observable)) {
                    "Reference store already contained reference $this on add"
                }
            }
        }.doOnUnsubscribe {
            if (refCount.decrementAndGet() == 0) {
                require(hardReferenceStore.remove(observable)) {
                    "Reference store did not contain reference $this on remove"
                }
            }
        }
    }

    override val schemaForDocumentation: Schema
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ) : Observable<*> {
        val observableContext = context.properties[RpcClientObservableSerializer.RpcObservableContextKey]
                as ObservableContext

        if (obj !is List<*>) throw NotSerializableException ("Input must be a serialised list")
        if (obj.size != 2) throw NotSerializableException ("Expecting two elements, have ${obj.size}")

        val observableId : Trace.InvocationId = Trace.InvocationId((obj[0] as String), Instant.ofEpochMilli((obj[1] as Long)))
        val observable = UnicastSubject.create<Notification<*>>()

        require(observableContext.observableMap.getIfPresent(observableId) == null) {
            "Multiple Observables arrived with the same ID $observableId"
        }

        val rpcCallSite = getRpcCallSite(context, observableContext)

        observableContext.observableMap.put(observableId, observable)
        observableContext.callSiteMap?.put(observableId, rpcCallSite)

        // We pin all Observables into a hard reference store (rooted in the RPC proxy) on subscription so that users
        // don't need to store a reference to the Observables themselves.
        return pinInSubscriptions(observable, observableContext.hardReferenceStore).doOnUnsubscribe {
            // This causes Future completions to give warnings because the corresponding OnComplete sent from the server
            // will arrive after the client unsubscribes from the observable and consequently invalidates the mapping.
            // The unsubscribe is due to [ObservableToFuture]'s use of first().
            observableContext.observableMap.invalidate(observableId)
        }.dematerialize<Any>()
    }

    private fun getRpcCallSite(context: SerializationContext, observableContext: ObservableContext): Throwable? {
        val rpcRequestOrObservableId = context.properties[RPCApi.RpcRequestOrObservableIdKey] as Trace.InvocationId
        return observableContext.callSiteMap?.get(rpcRequestOrObservableId)
    }

    override fun writeDescribedObject(obj: Observable<*>, data: Data, type: Type, output: SerializationOutput,
                                      context: SerializationContext
    ) {
       throw NotSupportedException()
    }
}