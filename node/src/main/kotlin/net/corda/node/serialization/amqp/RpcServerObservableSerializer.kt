package net.corda.node.serialization.amqp

import net.corda.core.context.Trace
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.utilities.loggerFor
import net.corda.node.services.messaging.ObservableContextInterface
import net.corda.node.services.messaging.ObservableSubscription
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.serialization.amqp.*
import org.apache.qpid.proton.codec.Data

import rx.Notification
import rx.Observable
import rx.Subscriber
import java.io.NotSerializableException

import java.lang.reflect.Type

class RpcServerObservableSerializer : CustomSerializer.Implements<Observable<*>>(
        Observable::class.java
) {
    // Would be great to make this private, but then it's so much harder to unit test
    object RpcObservableContextKey

    companion object {
        fun createContext(
                observableContext: ObservableContextInterface,
                serializationContext: SerializationContext
        ) = serializationContext.withProperty(
                    RpcServerObservableSerializer.RpcObservableContextKey, observableContext)
    }

    override val schemaForDocumentation: Schema
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun readObject(
            obj: Any, schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext
    ) : Observable<*> {
        throw UnsupportedOperationException()
    }

    override fun writeDescribedObject(
            obj: Observable<*>,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext
    ) {
        val observableId = Trace.InvocationId.newInstance()
        if (RpcServerObservableSerializer.RpcObservableContextKey !in context.properties) {
            throw NotSerializableException ("Missing Observable Key on serialization context - $type")
        }

        val observableContext = context.properties[RpcServerObservableSerializer.RpcObservableContextKey]
                as ObservableContextInterface

        data.withList {
            data.putString(observableId.value)
            data.putLong(observableId.timestamp.toEpochMilli())
        }

        val observableWithSubscription = ObservableSubscription(
                // We capture [observableContext] in the subscriber. Note that all synchronisation/kryo borrowing
                // must be done again within the subscriber
                subscription = obj.materialize().subscribe(
                        object : Subscriber<Notification<*>>() {
                            override fun onNext(observation: Notification<*>) {
                                if (!isUnsubscribed) {
                                    val message = RPCApi.ServerToClient.Observation(
                                            id = observableId,
                                            content = observation,
                                            deduplicationIdentity = observableContext.deduplicationIdentity
                                    )
                                    observableContext.sendMessage(message)
                                }
                            }

                            override fun onError(exception: Throwable) {
                                loggerFor<RpcServerObservableSerializer>().error(
                                        "onError called in materialize()d RPC Observable", exception)
                            }

                            override fun onCompleted() {

                            }
                        }
                )
        )

        observableContext.clientAddressToObservables.put(observableContext.clientAddress, observableId)
        observableContext.observableMap.put(observableId, observableWithSubscription)
    }
}