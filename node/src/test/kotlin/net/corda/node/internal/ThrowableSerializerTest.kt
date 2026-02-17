package net.corda.node.internal;

import net.corda.core.flows.FlowException
import net.corda.core.serialization.SerializationContext
import org.junit.Test
import net.corda.node.services.statemachine.ErrorStateTransitionException
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.amqp.DefaultDescriptorBasedSerializerRegistry
import net.corda.serialization.internal.amqp.DescriptorBasedSerializerRegistry
import java.io.NotSerializableException
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializerFactoryBuilder
import net.corda.serialization.internal.amqp.amqpMagic
import net.corda.serialization.internal.amqp.custom.PublicKeySerializer
import net.corda.serialization.internal.amqp.custom.ThrowableSerializer
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import java.lang.reflect.InvocationTargetException

class ExceptionWithStaticGetter : FlowException() {
    var foo: String = "foobar"
    get() = throw IllegalArgumentException("get")
    set(value) {
        field = value
    }
}

public class ThrowableSerializerTest {
    @Test(timeout=300_000, expected=InvocationTargetException::class)
    fun `See if ErrorStateTransitionException serializes`() {
        val e = ErrorStateTransitionException(ExceptionWithStaticGetter())
        val sf = testDefaultFactory()
        sf.register(ThrowableSerializer(sf))
        SerializationOutput(sf).serialize(e, testSerializationContext)
    }
}

fun testDefaultFactory(descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
                               DefaultDescriptorBasedSerializerRegistry()) =
        SerializerFactoryBuilder.build(
                AllWhitelist,
                ClassCarpenterImpl(AllWhitelist, ClassLoader.getSystemClassLoader()),
                descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry).also { it.register(PublicKeySerializer) }

val serializationProperties: MutableMap<Any, Any> = mutableMapOf()

val testSerializationContext = SerializationContextImpl(
        preferredSerializationVersion = amqpMagic,
        deserializationClassLoader = ClassLoader.getSystemClassLoader(),
        whitelist = AllWhitelist,
        properties = serializationProperties,
        objectReferencesEnabled = false,
        useCase = SerializationContext.UseCase.Testing,
        encoding = null)
