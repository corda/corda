package net.corda.nodeapi.internal.serialization.amqp

import org.junit.Test
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationCustomSerializer
import org.assertj.core.api.Assertions
import java.io.NotSerializableException
import kotlin.test.assertEquals
import net.corda.nodeapi.internal.serialization.amqp.testutils.testDefaultFactory

class CorDappSerializerTests {
    data class NeedsProxy (val a: String)

    class NeedsProxyProxySerializer : SerializationCustomSerializer<NeedsProxy, NeedsProxyProxySerializer.Proxy> {
        data class Proxy(val proxy_a_: String)

        override fun fromProxy(proxy: Proxy) = NeedsProxy(proxy.proxy_a_)
        override fun toProxy(obj: NeedsProxy) = Proxy(obj.a)
    }

    // Standard proxy serializer used internally, here for comparison purposes
    class InternalProxySerializer(factory: SerializerFactory) :
            CustomSerializer.Proxy<NeedsProxy, InternalProxySerializer.Proxy> (
                    NeedsProxy::class.java,
                    InternalProxySerializer.Proxy::class.java,
                    factory) {
        data class Proxy(val proxy_a_: String)

        override fun toProxy(obj: NeedsProxy): Proxy {
            return Proxy(obj.a)
        }

        override fun fromProxy(proxy: Proxy): NeedsProxy {
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
        internalProxyFactory.register (InternalProxySerializer(internalProxyFactory))

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

    @Test
    fun testWithWhitelistNotAllowed() {
        data class A (val a: Int, val b: NeedsProxy)

        class WL : ClassWhitelist {
            private val allowedClasses = emptySet<String>()

            override fun hasListed(type: Class<*>): Boolean = type.name in allowedClasses
        }

        val factory = SerializerFactory(WL(), ClassLoader.getSystemClassLoader())
        factory.registerExternal (CorDappCustomSerializer(NeedsProxyProxySerializer(), factory))

        val tv1 = 100
        val tv2 = "pants schmants"
        Assertions.assertThatThrownBy {
            SerializationOutput(factory).serialize(A(tv1, NeedsProxy(tv2)))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    @Test
    fun testWithWhitelistAllowed() {
        data class A (val a: Int, val b: NeedsProxy)

        class WL : ClassWhitelist {
            private val allowedClasses = hashSetOf(
                    A::class.java.name,
                    NeedsProxy::class.java.name)

            override fun hasListed(type: Class<*>): Boolean = type.name in allowedClasses
        }

        val factory = SerializerFactory(WL(), ClassLoader.getSystemClassLoader())
        factory.registerExternal (CorDappCustomSerializer(NeedsProxyProxySerializer(), factory))

        val tv1 = 100
        val tv2 = "pants schmants"
        val obj = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(A(tv1, NeedsProxy(tv2))))

        assertEquals(tv1, obj.a)
        assertEquals(tv2, obj.b.a)
    }

    // The custom type not being whitelisted won't matter here because the act of adding a
    // custom serializer bypasses the whitelist
    @Test
    fun testWithWhitelistAllowedOuterOnly() {
        data class A (val a: Int, val b: NeedsProxy)

        class WL : ClassWhitelist {
            // explicitly don't add NeedsProxy
            private val allowedClasses = hashSetOf(A::class.java.name)

            override fun hasListed(type: Class<*>): Boolean = type.name in allowedClasses
        }

        val factory = SerializerFactory(WL(), ClassLoader.getSystemClassLoader())
        factory.registerExternal (CorDappCustomSerializer(NeedsProxyProxySerializer(), factory))

        val tv1 = 100
        val tv2 = "pants schmants"
        val obj = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(A(tv1, NeedsProxy(tv2))))

        assertEquals(tv1, obj.a)
        assertEquals(tv2, obj.b.a)
    }
}