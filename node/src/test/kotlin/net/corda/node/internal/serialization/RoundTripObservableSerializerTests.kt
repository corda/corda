package net.corda.node.internal.serialization


import net.corda.client.rpc.internal.ObservableContext as ClientObservableContext
import net.corda.client.rpc.internal.RpcObservableMap
import net.corda.core.serialization.SerializationContext
import net.corda.core.internal.ThreadBox
import net.corda.core.context.Trace
import net.corda.node.internal.serialization.testutils.TestSubscription
import net.corda.node.internal.serialization.testutils.AMQPRoundTripRPCSerializationScheme
import net.corda.node.internal.serialization.testutils.TestObservableContext as ServerObservableContext
import net.corda.node.services.messaging.ObservableSubscription
import net.corda.node.services.messaging.ObservableSubscriptionMap
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.SerializationContextImpl
import net.corda.nodeapi.internal.serialization.amqp.DeserializationInput
import net.corda.nodeapi.internal.serialization.amqp.SerializationOutput
import net.corda.nodeapi.internal.serialization.amqp.amqpMagic

import co.paralleluniverse.common.util.SameThreadExecutor
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.RemovalListener
import com.google.common.collect.LinkedHashMultimap
import net.corda.node.internal.serialization.testutils.serializationContext
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Test
import rx.Notification
import rx.Observable
import rx.subjects.UnicastSubject
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

class RoundTripObservableSerializerTests {

    private fun getID() = Trace.InvocationId("test1", Instant.now())

    private fun subscriptionMap(
            id: Trace.InvocationId
    ) : ObservableSubscriptionMap {
        val subMap: ObservableSubscriptionMap = Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(100)
                .build()

        subMap.put(id, ObservableSubscription(TestSubscription()))

        return subMap
    }

    private val observablesToReap = ThreadBox(object {
        var observables = ArrayList<Trace.InvocationId>()
    })

    private fun createRpcObservableMap(): RpcObservableMap {
        val onObservableRemove = RemovalListener<Trace.InvocationId, UnicastSubject<Notification<*>>> { key, value, cause ->
            val observableId = key!!
//            val rpcCallSite = /* callSiteMap?.remove(observableId) */null
            if (cause == RemovalCause.COLLECTED) {

            }
            observablesToReap.locked { observables.add(observableId) }
        }

        return Caffeine.newBuilder().weakValues().removalListener(onObservableRemove).executor(SameThreadExecutor.getExecutor()).build()
    }

    @Test
    fun roundTripTest1() {
        val serializationScheme = AMQPRoundTripRPCSerializationScheme(serializationContext)

        // Fake up a message ID, needs to be used on both "sides". The server setting it in the subscriptionMap,
        // the client as a property of the deserializer which, in the actual RPC client, is pulled off of
        // the received message
        val id : Trace.InvocationId = getID()

        val serverObservableContext = ServerObservableContext(
                subscriptionMap(id),
                clientAddressToObservables = LinkedHashMultimap.create(),
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
        val obs = Observable.create<Int>({ 12 })

        // So this doesn't crash... but does it work?
        val blob = SerializationOutput(serverSerializer).serialize(obs)
        val obs2 = DeserializationInput(clientSerializer).deserialize(blob)
    }
}