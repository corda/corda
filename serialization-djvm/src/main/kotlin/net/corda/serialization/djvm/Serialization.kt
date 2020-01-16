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
    return createSandboxSerializationEnv(classLoader, emptySet(), emptySet())
}

fun createSandboxSerializationEnv(
    classLoader: SandboxClassLoader,
    customSerializerClassNames: Set<String>,
    serializationWhitelistNames: Set<String>
): SerializationEnvironment {
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
            customSerializerClassNames = customSerializerClassNames,
            serializationWhitelistNames = serializationWhitelistNames,
            serializerFactoryFactory = SandboxSerializerFactoryFactory(primitiveSerializerFactory)
        ))
    }
    return SerializationEnvironment.with(factory, p2pContext = p2pContext)
}

inline fun <reified T: Any> SerializedBytes<T>.deserializeFor(classLoader: SandboxClassLoader): Any {
    return deserializeTo(T::class.java, classLoader)
}

inline fun <reified T: Any> ByteSequence.deserializeTypeFor(classLoader: SandboxClassLoader): Any {
    return deserializeTo(T::class.java, classLoader)
}

fun <T: Any> ByteSequence.deserializeTo(clazz: Class<T>, classLoader: SandboxClassLoader): Any {
    val sandboxClazz = classLoader.toSandboxClass(clazz)
    return deserializeTo(sandboxClazz)
}

fun ByteSequence.deserializeTo(clazz: Class<*>): Any {
    return deserializeTo(clazz, SerializationFactory.defaultFactory)
}

fun ByteSequence.deserializeTo(clazz: Class<*>, factory: SerializationFactory): Any {
    return factory.deserialize(this, clazz, factory.defaultContext)
}
