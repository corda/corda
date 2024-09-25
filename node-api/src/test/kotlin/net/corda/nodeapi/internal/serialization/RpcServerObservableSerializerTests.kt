package net.corda.nodeapi.internal.serialization

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.mockito.kotlin.mock
import net.corda.core.context.Trace
import net.corda.nodeapi.internal.serialization.testutils.TestObservableContext
import net.corda.nodeapi.internal.serialization.testutils.serializationContext
import net.corda.nodeapi.internal.rpc.ObservableSubscription
import net.corda.nodeapi.internal.serialization.amqp.RpcServerObservableSerializer
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializerFactoryBuilder
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Test
import rx.Observable
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

        subMap.put(Trace.InvocationId("test1", Instant.now()), ObservableSubscription(mock()))

        return subMap
    }

    @Test(timeout=300_000)
	fun canSerializerBeRegistered() {
        val sf = SerializerFactoryBuilder.build(AllWhitelist, javaClass.classLoader)

        try {
            sf.register(RpcServerObservableSerializer())
        } catch (e: Exception) {
            throw Error("Observable serializer must be registerable with factory, unexpected exception - ${e.message}")
        }
    }

    @Test(timeout=300_000)
	fun canAssociateWithContext() {
        val observable = TestObservableContext(
                subscriptionMap(),
                clientAddressToObservables = ConcurrentHashMap(),
                deduplicationIdentity = "thisIsATest",
                clientAddress = SimpleString.of("clientAddress"))

        val newContext = RpcServerObservableSerializer.createContext(serializationContext, observable)

        assertEquals(1, newContext.properties.size)
        assertTrue(newContext.properties.containsKey(RpcServerObservableSerializer.RpcObservableContextKey))
        assertEquals(observable, newContext.properties[RpcServerObservableSerializer.RpcObservableContextKey])
    }

    @Test(timeout=300_000)
	fun serialiseFakeObservable() {
        val testClientAddress = "clientAddres"
        val observable = TestObservableContext(
                subscriptionMap(),
                clientAddressToObservables = ConcurrentHashMap(),
                deduplicationIdentity = "thisIsATest",
                clientAddress = SimpleString.of(testClientAddress))

        val sf = SerializerFactoryBuilder.build(AllWhitelist, javaClass.classLoader).apply {
            register(RpcServerObservableSerializer())
        }

        val obs = Observable.unsafeCreate<Int> { Math.random() }
        val newContext = RpcServerObservableSerializer.createContext(serializationContext, observable)

        try {
            SerializationOutput(sf).serializeAndReturnSchema(obs, newContext)
        } catch (e: Exception) {
            throw Error("Serialization of observable should not throw - ${e.message}")
        }
    }
}
