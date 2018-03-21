package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.*
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.*
import org.junit.Test
import kotlin.test.assertEquals

// Make sure all serialization calls in this test don't get stomped on by anything else
val TESTING_CONTEXT = SerializationContextImpl(amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.Testing,
        null)

// Test factory that lets us count the number of serializer registration attempts
class TestSerializerFactory(
        wl : ClassWhitelist,
        cl : ClassLoader
) : SerializerFactory(wl, cl) {
    var registerCount = 0

    override fun register(customSerializer: CustomSerializer<out Any>) {
        ++registerCount
        return super.register(customSerializer)
    }
}

// Instance of our test factory counting registration attempts. Sucks its  gloal but for testing purposes this
// is the easiest way of getting access to the object
val testFactory = TestSerializerFactory(TESTING_CONTEXT.whitelist, TESTING_CONTEXT.deserializationClassLoader)

// Serializer factory factory, plugs into the SerializationScheme and controls which factory type
// we make for each use case. For our tests we need to make sure if its the Testing use case we return
// the global factory object created above that counts registrations
class TestSerializerFactoryFactory : SerializerFactoryFactory() {
    override fun make(context: SerializationContext) =
            when (context.useCase) {
                SerializationContext.UseCase.Testing -> testFactory
                else -> super.make(context)
            }
}

class AMQPTestSerializationScheme : AbstractAMQPSerializationScheme(emptyList(), TestSerializerFactoryFactory()) {
    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase) = true
}

// Test SerializationFactory that wraps a serialization scheme that just allows us to call <OBJ>.serialize.
// Returns the testing scheme we created above that wraps the testing factory
class TestSerializationFactory : SerializationFactory() {
    val scheme = AMQPTestSerializationScheme()

    override fun <T : Any> deserialize(
            byteSequence: ByteSequence,
            clazz: Class<T>, context:
            SerializationContext
    ): T {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : Any> deserializeWithCompatibleContext(
            byteSequence: ByteSequence,
            clazz: Class<T>,
            context: SerializationContext
    ): ObjectWithCompatibleContext<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext) = scheme.serialize(obj, context)
}

// The actual test
class SerializationSchemaTests {
    @Test
    fun onlyRegisterCustomSerializersOnce() {
        @CordaSerializable data class C(val a: Int)

        val c = C(1)
        val testSerializationFactory = TestSerializationFactory()
        val expectedCustomSerializerCount = 40

        assertEquals (0, testFactory.registerCount)
        c.serialize (testSerializationFactory, TESTING_CONTEXT)
        assertEquals (expectedCustomSerializerCount, testFactory.registerCount)
        c.serialize (testSerializationFactory, TESTING_CONTEXT)
        assertEquals (expectedCustomSerializerCount, testFactory.registerCount)
    }
}