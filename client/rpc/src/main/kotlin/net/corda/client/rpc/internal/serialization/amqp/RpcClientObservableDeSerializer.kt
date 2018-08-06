package net.corda.client.rpc.internal.serialization.amqp


import net.corda.client.rpc.internal.ObservableContext
import net.corda.core.context.Trace
import net.corda.core.serialization.SerializationContext
import net.corda.nodeapi.RPCApi
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.codec.Data
import rx.Notification
import rx.Observable
import rx.subjects.UnicastSubject
import java.io.NotSerializableException
import java.lang.reflect.Type
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.transaction.NotSupportedException

/**
 * De-serializer for Rx[Observable] instances for the RPC Client library. Can only be used to deserialize such objects,
 * just as the corresponding RPC server side code ([RpcServerObservableSerializer]) can only serialize them. Observables are only notionally serialized,
 * what is actually sent is a reference to the observable that can then be subscribed to.
 */
object RpcClientObservableDeSerializer : CustomSerializer.Implements<Observable<*>>(Observable::class.java) {
    private object RpcObservableContextKey

    fun createContext(
            serializationContext: SerializationContext,
            observableContext: ObservableContext
    ) = serializationContext.withProperty(RpcObservableContextKey, observableContext)

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

    override val schemaForDocumentation = Schema(
            listOf(
                    CompositeType(
                            name = type.toString(),
                            label = "",
                            provides = emptyList(),
                            descriptor = descriptor,
                            fields = listOf(
                                    Field(
                                            name = "observableId",
                                            type = "string",
                                            requires = emptyList(),
                                            default = null,
                                            label = null,
                                            mandatory = true,
                                            multiple = false),
                                    Field(
                                            name = "observableInstant",
                                            type = "long",
                                            requires = emptyList(),
                                            default = null,
                                            label = null,
                                            mandatory = true,
                                            multiple = false)
                            ))))

    /**
     * Converts the serialized form, a blob, back into an Observable
     */
    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ): Observable<*> {
        if (RpcObservableContextKey !in context.properties) {
            throw NotSerializableException("Missing Observable Context Key on Client Context")
        }

        val observableContext =
                context.properties[RpcClientObservableDeSerializer.RpcObservableContextKey] as ObservableContext

        if (obj !is List<*>) throw NotSerializableException("Input must be a serialised list")
        if (obj.size != 2) throw NotSerializableException("Expecting two elements, have ${obj.size}")

        val observableId: Trace.InvocationId = Trace.InvocationId((obj[0] as String), Instant.ofEpochMilli((obj[1] as Long)))
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

    override fun writeDescribedObject(
            obj: Observable<*>,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext
    ) {
        throw NotSupportedException()
    }
}