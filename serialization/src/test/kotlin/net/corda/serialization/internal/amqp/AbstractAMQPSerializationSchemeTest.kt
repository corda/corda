package net.corda.serialization.internal.amqp

import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.ByteSequence
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.testutils.serializationProperties
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Test
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.stream.IntStream

class AbstractAMQPSerializationSchemeTest {
    @Test
    fun `number of cached factories must be bounded by maxFactories`() {
        val genesisContext = SerializationContextImpl(
                ByteSequence.of(byteArrayOf('c'.toByte(), 'o'.toByte(), 'r'.toByte(), 'd'.toByte(), 'a'.toByte(), 0.toByte(), 0.toByte(), 1.toByte())),
                ClassLoader.getSystemClassLoader(),
                AllWhitelist,
                serializationProperties,
                false,
                SerializationContext.UseCase.RPCClient,
                null)


        nodeSerializationEnv = SerializationEnvironmentImpl(
                SerializationFactoryImpl().apply {
                    registerScheme(AMQPClientSerializationScheme(emptyList()))
                },
                rpcClientContext = TESTING_CONTEXT,
                p2pContext = TESTING_CONTEXT
        )


        val factory = TestSerializerFactory(TESTING_CONTEXT.whitelist, TESTING_CONTEXT.deserializationClassLoader)
        val backingMap = ConcurrentHashMap<Pair<ClassWhitelist, ClassLoader>, SerializerFactory>()
        val maxFactories = 512
        val scheme = object : AbstractAMQPSerializationScheme(emptySet(), backingMap, createSerializerFactoryFactory(), maxFactories) {
            override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
                return factory
            }

            override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
                return factory
            }

            override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
                return true
            }

        }


        IntStream.range(0, 2048).parallel().forEach {
            val context = if (ThreadLocalRandom.current().nextBoolean()) {
                genesisContext.withClassLoader(URLClassLoader(emptyArray()))
            } else {
                genesisContext
            }
            val testString = "TEST${ThreadLocalRandom.current().nextInt()}"
            val serialized = scheme.serialize(testString, context)
            val deserialized = serialized.deserialize(context = context)
            Assert.assertThat(testString, `is`(deserialized))
            Assert.assertThat(backingMap.size, `is`(Matchers.lessThanOrEqualTo(maxFactories)))
        }
        Assert.assertThat(backingMap.size, CoreMatchers.`is`(Matchers.lessThanOrEqualTo(maxFactories)))
    }
}



