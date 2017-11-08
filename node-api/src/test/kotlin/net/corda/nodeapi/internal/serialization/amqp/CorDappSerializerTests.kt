package net.corda.nodeapi.internal.serialization.amqp

import org.junit.Test
import net.corda.core.serialization.CordaCustomSerializer
import net.corda.core.serialization.CordaCustomSerializerProxy
import net.corda.core.serialization.SerializationCustomSerializer
import java.lang.reflect.Type
import kotlin.test.assertEquals

class CorDappSerializerTests {
    data class NeedsProxy (val a: String)

    @CordaCustomSerializer
    class NeedsProxyProxySerializer : SerializationCustomSerializer {
        @CordaCustomSerializerProxy
        data class Proxy(val proxy_a_: String)

        override val type: Type get()  = NeedsProxy::class.java
        override val ptype: Type get() = Proxy::class.java

        override fun fromProxy(proxy: Any) : Any {
            println ("NeedsProxyProxySerialiser - fromProxy")
            return NeedsProxy((proxy as Proxy).proxy_a_)
        }
        override fun toProxy(obj: Any) : Any {
            println ("NeedsProxyProxySerialiser - to Proxy")
            return Proxy((obj as NeedsProxy).a)
        }
    }

    // Standard proxy serialiser used internally, here for comparison purposes
    class InternalProxySerialiser(factory: SerializerFactory) :
            CustomSerializer.Proxy<NeedsProxy, InternalProxySerialiser.Proxy> (
                    NeedsProxy::class.java,
                    InternalProxySerialiser.Proxy::class.java,
                    factory) {
        data class Proxy(val proxy_a_: String)

        override fun toProxy(obj: NeedsProxy): Proxy {
            println ("InternalProxySerialiser - toProxy")
            return Proxy(obj.a)
        }

        override fun fromProxy(proxy: Proxy): NeedsProxy {
            println ("InternalProxySerialiser - fromProxy")
            return NeedsProxy(proxy.proxy_a_)
        }
    }

    @Test
    fun `type uses proxy`() {
        val internalProxyFactory = testDefaultFactory()
        val proxyFactory = testDefaultFactory()
        val defaultFactory = testDefaultFactory()

        val msg = "help"

        proxyFactory.registerExternal (CorDappCustomSerializer(NeedsProxyProxySerializer(), proxyFactory))
        internalProxyFactory.register (InternalProxySerialiser(internalProxyFactory))

        val needsProxy = NeedsProxy(msg)

        val bAndSProxy = SerializationOutput(proxyFactory).serializeAndReturnSchema (needsProxy)
        val bAndSInternal = SerializationOutput(internalProxyFactory).serializeAndReturnSchema (needsProxy)
        val bAndSDefault = SerializationOutput(defaultFactory).serializeAndReturnSchema (needsProxy)

        val objFromDefault = DeserializationInput(defaultFactory).deserializeAndReturnEnvelope(bAndSDefault.obj)
        val objFromInternal = DeserializationInput(internalProxyFactory).deserializeAndReturnEnvelope(bAndSInternal.obj)
        val objFromProxy = DeserializationInput(proxyFactory).deserializeAndReturnEnvelope(bAndSProxy.obj)

        assertEquals(msg, objFromDefault.obj.a)
        assertEquals(msg, objFromInternal.obj.a)
        assertEquals(msg, objFromProxy.obj.a)
    }

    @Test
    fun proxiedTypeIsNested() {
        data class A (val a: Int, val b: NeedsProxy)

        val factory = testDefaultFactory()
        factory.registerExternal (CorDappCustomSerializer(NeedsProxyProxySerializer(), factory))

        val tv1 = 100
        val tv2 = "pants schmants"
        val bAndS = SerializationOutput(factory).serializeAndReturnSchema (A(tv1, NeedsProxy(tv2)))

        val objFromDefault = DeserializationInput(factory).deserializeAndReturnEnvelope(bAndS.obj)

        assertEquals(tv1, objFromDefault.obj.a)
        assertEquals(tv2, objFromDefault.obj.b.a)
    }
}