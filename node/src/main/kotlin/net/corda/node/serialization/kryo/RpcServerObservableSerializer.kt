package net.corda.node.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.context.Trace
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.node.services.messaging.ObservableSubscription
import net.corda.node.services.messaging.RPCServer
import net.corda.nodeapi.RPCApi
import org.slf4j.LoggerFactory
import rx.Notification
import rx.Observable
import rx.Subscriber

object RpcServerObservableSerializer : Serializer<Observable<*>>() {
    private object RpcObservableContextKey

    private val log = LoggerFactory.getLogger(javaClass)
    
    fun createContext(observableContext: RPCServer.ObservableContext): SerializationContext {
        return SerializationDefaults.RPC_SERVER_CONTEXT.withProperty(RpcObservableContextKey, observableContext)
    }

    override fun read(kryo: Kryo?, input: Input?, type: Class<Observable<*>>?): Observable<Any> {
        throw UnsupportedOperationException()
    }

    override fun write(kryo: Kryo, output: Output, observable: Observable<*>) {
        val observableId = Trace.InvocationId.newInstance()
        val observableContext = kryo.context[RpcObservableContextKey] as RPCServer.ObservableContext
        output.writeInvocationId(observableId)
        val observableWithSubscription = ObservableSubscription(
                // We capture [observableContext] in the subscriber. Note that all synchronisation/kryo borrowing
                // must be done again within the subscriber
                subscription = observable.materialize().subscribe(
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

    private fun Output.writeInvocationId(id: Trace.InvocationId) {
        writeString(id.value)
        writeLong(id.timestamp.toEpochMilli())
    }
}
