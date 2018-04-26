package net.corda.node.internal.serialization

import com.google.common.collect.LinkedHashMultimap
import net.corda.core.concurrent.CordaFuture
import net.corda.node.internal.serialization.testutils.TestObservableContext
import net.corda.node.internal.serialization.testutils.serializationContext
import net.corda.node.serialization.amqp.RpcServerCordaFutureSerialiser
import net.corda.node.serialization.amqp.RpcServerObservableSerializer
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.amqp.SerializationOutput
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Test
import rx.Observable

class FutureSerializationTests {
    @Test
    fun serializeFuture() {
        /*
        val testClientAddress = "clientAddres"
        val observable = TestObservableContext(
                subscriptionMap(),
                clientAddressToObservables = LinkedHashMultimap.create(),
                deduplicationIdentity = "thisIsATest",
                clientAddress = SimpleString (testClientAddress))
        */

        val sf = SerializerFactory(
                cl = javaClass.classLoader,
                whitelist = AllWhitelist
        ).apply {
            register(RpcServerObservableSerializer())
            register(RpcServerCordaFutureSerialiser(this))
        }

        /*
        data class C(val f: CordaFuture<Int>)
        val c = C()


        val newContext = RpcServerObservableSerializer.createContext(, serializationContext)

        try {
            SerializationOutput(sf).serializeAndReturnSchema(obs, newContext)
        }
        catch (e: Exception) {
            throw Error ("Serialization of observable should not throw - ${e.message}")
        }
        */
    }
}