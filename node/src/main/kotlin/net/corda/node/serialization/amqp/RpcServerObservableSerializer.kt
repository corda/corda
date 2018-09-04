package net.corda.node.serialization.amqp

import net.corda.core.context.Trace
import net.corda.core.serialization.SerializationContext
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.loggerFor
import net.corda.node.services.messaging.ObservableContextInterface
import net.corda.node.services.messaging.ObservableSubscription
import net.corda.nodeapi.RPCApi
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.codec.Data
import rx.Notification
import rx.Observable
import rx.Subscriber
import java.io.NotSerializableException
import java.lang.reflect.Type

/**
 * Server side serializer that notionally serializes RxObservables when used by the RPC
 * framework for event subscriptions. Notional in the sense that the actual observable
 * isn't serialized, rather a reference to the observable is, this is then used by
 * the client side RPC handler to subscribe to the observable stream.
 */
class RpcServerObservableSerializer : CustomSerializer.Implements<Observable<*>>(
        Observable::class.java
) {
    // Would be great to make this private, but then it's so much harder to unit test
    object RpcObservableContextKey

    companion object {
        fun createContext(
                serializationContext: SerializationContext,
                observableContext: ObservableContextInterface
        ) = serializationContext.withProperty(RpcServerObservableSerializer.RpcObservableContextKey, observableContext)

        val log = contextLogger()
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

    override fun readObject(
            obj: Any, schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext
    ): Observable<*> {
        // Note: this type of server Serializer is never meant to read postings arriving from clients.
        // I.e. Observables cannot be used as parameters for RPC methods and can only be used as return values.
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
            throw NotSerializableException("Missing Observable Key on serialization context - $type")
        }

        val observableContext = context.properties[RpcServerObservableSerializer.RpcObservableContextKey]
                as ObservableContextInterface

        data.withList {
            data.putString(observableId.value)
            data.putLong(observableId.timestamp.toEpochMilli())
        }

        val observableWithSubscription = ObservableSubscription(
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
                                observableContext.clientAddressToObservables.compute(observableContext.clientAddress) { _, observables ->
                                    if (observables != null) {
                                        observables.remove(observableId)
                                        if (observables.isEmpty()) {
                                            null
                                        } else {
                                            observables
                                        }
                                    } else {
                                        null
                                    }
                                }
                            }
                        }
                )
        )

        observableContext.clientAddressToObservables.compute(observableContext.clientAddress) { _, observables ->
            if (observables == null) {
                hashSetOf(observableId)
            } else {
                observables.add(observableId)
                observables
            }
        }
        observableContext.observableMap.put(observableId, observableWithSubscription)
        log.trace("Serialized observable $observableId of type $obj")
    }
}