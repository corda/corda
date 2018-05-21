package net.corda.node.internal.serialization

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.nhaarman.mockito_kotlin.mock
import net.corda.core.context.Trace
import net.corda.node.internal.serialization.testutils.TestObservableContext
import net.corda.node.internal.serialization.testutils.serializationContext
import net.corda.node.serialization.amqp.RpcServerObservableSerializer
import net.corda.node.services.messaging.ObservableSubscription
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.AllWhitelist
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Test
import rx.Observable
import rx.Subscription
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RpcServerObservableSerializerTests {

    private fun subscriptionMap(): Cache<Trace.InvocationId, ObservableSubscription> {
        val subMap: Cache<Trace.InvocationId, ObservableSubscription> = Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(100)
                .build()

        subMap.put(Trace.InvocationId("test1", Instant.now()), ObservableSubscription(mock<Subscription>()))

        return subMap
    }

    @Test
    fun canSerializerBeRegistered() {
        val sf = SerializerFactory(AllWhitelist, javaClass.classLoader)

        try {
            sf.register(RpcServerObservableSerializer())
        } catch (e: Exception) {
            throw Error("Observable serializer must be registerable with factory, unexpected exception - ${e.message}")
        }
    }

    @Test
    fun canAssociateWithContext() {
        val observable = TestObservableContext(
                subscriptionMap(),
                clientAddressToObservables = ConcurrentHashMap(),
                deduplicationIdentity = "thisIsATest",
                clientAddress = SimpleString("clientAddress"))

        val newContext = RpcServerObservableSerializer.createContext(serializationContext, observable)

        assertEquals(1, newContext.properties.size)
        assertTrue(newContext.properties.containsKey(RpcServerObservableSerializer.RpcObservableContextKey))
        assertEquals(observable, newContext.properties[RpcServerObservableSerializer.RpcObservableContextKey])
    }

    @Test
    fun serialiseFakeObservable() {
        val testClientAddress = "clientAddres"
        val observable = TestObservableContext(
                subscriptionMap(),
                clientAddressToObservables = ConcurrentHashMap(),
                deduplicationIdentity = "thisIsATest",
                clientAddress = SimpleString(testClientAddress))

        val sf = SerializerFactory(AllWhitelist, javaClass.classLoader).apply {
            register(RpcServerObservableSerializer())
        }

        val obs = Observable.create<Int>({ 12 })
        val newContext = RpcServerObservableSerializer.createContext(serializationContext, observable)

        try {
            SerializationOutput(sf).serializeAndReturnSchema(obs, newContext)
        } catch (e: Exception) {
            throw Error("Serialization of observable should not throw - ${e.message}")
        }
    }
}