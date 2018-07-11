package net.corda.node.internal.serialization

import co.paralleluniverse.common.util.SameThreadExecutor
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalListener
import com.nhaarman.mockito_kotlin.mock
import net.corda.client.rpc.internal.serialization.amqp.RpcClientObservableSerializer
import net.corda.core.context.Trace
import net.corda.core.internal.ThreadBox
import net.corda.node.internal.serialization.testutils.AMQPRoundTripRPCSerializationScheme
import net.corda.node.internal.serialization.testutils.serializationContext
import net.corda.node.serialization.amqp.RpcServerObservableSerializer
import net.corda.node.services.messaging.ObservableSubscription
import net.corda.nodeapi.RPCApi
import net.corda.serialization.internal.amqp.AccessOrderLinkedHashMap
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.SerializationOutput
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Test
import rx.Notification
import rx.Observable
import rx.subjects.UnicastSubject
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import net.corda.client.rpc.internal.ObservableContext as ClientObservableContext
import net.corda.node.internal.serialization.testutils.TestObservableContext as ServerObservableContext

class RoundTripObservableSerializerTests {
    private fun getID() = Trace.InvocationId("test1", Instant.now())

    private fun subscriptionMap(
            id: Trace.InvocationId
    ) : Cache<Trace.InvocationId, ObservableSubscription> {
        val subMap: Cache<Trace.InvocationId, ObservableSubscription> = Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(100)
                .build()

        subMap.put(id, ObservableSubscription(mock()))

        return subMap
    }

    private val observablesToReap = ThreadBox(object {
        var observables = ArrayList<Trace.InvocationId>()
    })

    private fun createRpcObservableMap(): Cache<Trace.InvocationId, UnicastSubject<Notification<*>>> {
        val onObservableRemove = RemovalListener<Trace.InvocationId, UnicastSubject<Notification<*>>> { key, _, _ ->
            val observableId = key!!

            observablesToReap.locked { observables.add(observableId) }
        }

        return Caffeine.newBuilder().weakValues().removalListener(onObservableRemove).executor(SameThreadExecutor.getExecutor()).build()
    }

    @Test
    fun roundTripTest1() {
        val serializationScheme = AMQPRoundTripRPCSerializationScheme(
                serializationContext, emptySet(), AccessOrderLinkedHashMap { 128 })

        // Fake up a message ID, needs to be used on both "sides". The server setting it in the subscriptionMap,
        // the client as a property of the deserializer which, in the actual RPC client, is pulled off of
        // the received message
        val id : Trace.InvocationId = getID()

        val serverObservableContext = ServerObservableContext(
                subscriptionMap(id),
                clientAddressToObservables = ConcurrentHashMap(),
                deduplicationIdentity = "thisIsATest",
                clientAddress = SimpleString("clientAddress"))

        val serverSerializer = serializationScheme.rpcServerSerializerFactory(serverObservableContext)

        val clientObservableContext = ClientObservableContext(
                callSiteMap = null,
                observableMap = createRpcObservableMap(),
                hardReferenceStore = Collections.synchronizedSet(mutableSetOf<Observable<*>>())
        )

        val clientSerializer = serializationScheme.rpcClientSerializerFactory(clientObservableContext, id)


        // What we're actually going to serialize then deserialize
        val obs = Observable.create<Int> { Math.random() }

        val serverSerializationContext = RpcServerObservableSerializer.createContext(
                serializationContext, serverObservableContext)

        val clientSerializationContext = RpcClientObservableSerializer.createContext(
                serializationContext, clientObservableContext).withProperty(RPCApi.RpcRequestOrObservableIdKey, id)


        val blob = SerializationOutput(serverSerializer).serialize(obs, serverSerializationContext)
        DeserializationInput(clientSerializer).deserialize(blob, clientSerializationContext)
    }
}
