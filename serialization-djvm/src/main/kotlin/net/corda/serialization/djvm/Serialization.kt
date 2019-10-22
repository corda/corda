@file:JvmName("Serialization")
package net.corda.serialization.djvm

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.utilities.ByteSequence
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.serializers.PrimitiveSerializer
import net.corda.serialization.internal.GlobalTransientClassWhiteList
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AMQPSerializer
import net.corda.serialization.internal.amqp.amqpMagic
import java.util.function.Function

@Suppress("NOTHING_TO_INLINE")
inline fun SandboxClassLoader.toSandboxAnyClass(clazz: Class<*>): Class<Any> {
    @Suppress("unchecked_cast")
    return toSandboxClass(clazz) as Class<Any>
}

fun createSandboxSerializationEnv(classLoader: SandboxClassLoader): SerializationEnvironment {
    val p2pContext: SerializationContext = SerializationContextImpl(
        preferredSerializationVersion = amqpMagic,
        deserializationClassLoader = DelegatingClassLoader(classLoader),
        whitelist = GlobalTransientClassWhiteList(SandboxWhitelist()),
        properties = emptyMap(),
        objectReferencesEnabled = true,
        carpenterDisabled = true,
        useCase = UseCase.P2P,
        encoding = null
    )

    val taskFactory = classLoader.createRawTaskFactory()
    val sandboxBasicInput = classLoader.createBasicInput()

    val primitiveSerializerFactory: Function<Class<*>, AMQPSerializer<Any>> = Function { clazz ->
         PrimitiveSerializer(clazz, sandboxBasicInput)
    }

    val factory = SerializationFactoryImpl(mutableMapOf()).apply {
        registerScheme(AMQPSerializationScheme(
            classLoader = classLoader,
            sandboxBasicInput = sandboxBasicInput,
            taskFactory = taskFactory,
            serializerFactoryFactory = SandboxSerializerFactoryFactory(primitiveSerializerFactory)
        ))
    }
    return SerializationEnvironment.with(factory, p2pContext = p2pContext)
}

inline fun <reified T : Any> SerializedBytes<T>.deserializeFor(classLoader: SandboxClassLoader): Any {
    val clazz = classLoader.toSandboxClass(T::class.java)
    return deserializeTo(clazz, classLoader, SerializationFactory.defaultFactory)
}

fun ByteSequence.deserializeTo(clazz: Class<*>, classLoader: SandboxClassLoader, factory: SerializationFactory): Any {
    return deserializeTo(clazz, classLoader, factory, factory.defaultContext)
}

fun ByteSequence.deserializeTo(
    clazz: Class<*>,
    classLoader: SandboxClassLoader,
    factory: SerializationFactory,
    context: SerializationContext
): Any {
    val obj = factory.deserialize(this, Any::class.java, context)
    return if (clazz.isInstance(obj)) {
        obj
    } else {
        classLoader.createBasicInput().apply(obj)!!
    }
}
