package net.corda.node.internal.serialization

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.collect.LinkedHashMultimap
import junit.framework.TestFailure
import net.corda.core.context.Trace
import net.corda.core.serialization.SerializationContext
import net.corda.node.internal.serialization.testutils.*
import net.corda.node.serialization.amqp.RpcServerObservableSerializer
import net.corda.node.services.messaging.ObservableSubscription
import net.corda.node.services.messaging.ObservableSubscriptionMap
import net.corda.nodeapi.internal.serialization.AMQP_RPC_SERVER_CONTEXT
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.SerializationContextImpl
import net.corda.nodeapi.internal.serialization.amqp.SerializationOutput
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import net.corda.nodeapi.internal.serialization.amqp.amqpMagic

import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Test
import rx.Observable
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RpcServerObservableSerializerTests {

    private fun subscriptionMap() : ObservableSubscriptionMap {
        val subMap: ObservableSubscriptionMap = Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(100)
                .build()

        subMap.put(Trace.InvocationId("test1", Instant.now()), ObservableSubscription(TestSubscription()))

        return subMap
    }

    @Test
    fun canSerializerBeRegistered() {
        val sf = SerializerFactory(cl = javaClass.classLoader, whitelist = AllWhitelist)

        try {
            sf.register(RpcServerObservableSerializer())
        }
        catch (e: Exception) {
            throw Error("Observable serializer must be registerable with factory, unexpected exception - ${e.message}")
        }
    }

    @Test
    fun canAssociateWithContext() {
        val observable = TestObservableContext(
                subscriptionMap(),
                clientAddressToObservables = LinkedHashMultimap.create(),
                deduplicationIdentity = "thisIsATest",
                clientAddress = SimpleString ("clientAddress"))

        val newContext = RpcServerObservableSerializer.createContext(observable, serializationContext)

        assertEquals(1, newContext.properties.size)
        assertTrue(newContext.properties.containsKey(RpcServerObservableSerializer.RpcObservableContextKey))
        assertEquals(observable, newContext.properties[RpcServerObservableSerializer.RpcObservableContextKey])
    }

    @Test
    fun serialiseFakeObservable() {
        val testClientAddress = "clientAddres"
        val observable = TestObservableContext(
                subscriptionMap(),
                clientAddressToObservables = LinkedHashMultimap.create(),
                deduplicationIdentity = "thisIsATest",
                clientAddress = SimpleString (testClientAddress))

        val sf = SerializerFactory(
                cl = javaClass.classLoader,
                whitelist = AllWhitelist
        ).apply {
            register(RpcServerObservableSerializer())
        }

        val obs = Observable.create<Int>( { 12 })
        val newContext = RpcServerObservableSerializer.createContext(observable, serializationContext)

        try {
            SerializationOutput(sf).serializeAndReturnSchema(obs, newContext)
        }
        catch (e: Exception) {
            throw Error ("Serialization of observable should not throw - ${e.message}")
        }
    }
}