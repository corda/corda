@file:JvmName("Serialization")
package net.corda.djvm.serialization

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.utilities.ByteSequence
import net.corda.djvm.execution.SandboxRuntimeException
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.serializers.PrimitiveSerializer
import net.corda.djvm.source.ClassSource
import net.corda.serialization.internal.GlobalTransientClassWhiteList
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AMQPSerializer
import net.corda.serialization.internal.amqp.amqpMagic
import java.lang.reflect.InvocationTargetException
import java.util.function.BiFunction
import java.util.function.Function

fun SandboxClassLoader.loadClassForSandbox(clazz: Class<*>): Class<Any> {
    @Suppress("unchecked_cast")
    return loadClassForSandbox(ClassSource.fromClassName(clazz.name)) as Class<Any>
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

    val taskClass = classLoader.loadClass("sandbox.RawTask")
    val taskApply = taskClass.getDeclaredMethod("apply", Any::class.java)
    val taskConstructor = taskClass.getDeclaredConstructor(classLoader.loadClassForSandbox(Function::class.java))
    val executor: BiFunction<in Any, in Any?, out Any?> = BiFunction { userTask, arg ->
        try {
            taskApply(taskConstructor.newInstance(userTask), arg)
        } catch (ex: InvocationTargetException) {
            val target = ex.targetException
            throw when (target) {
                is RuntimeException, is Error -> target
                else -> SandboxRuntimeException(target.message, target)
            }
        }
    }

    val sandboxBasicInput = classLoader.createBasicInput()

    val primitiveSerializerFactory: Function<Class<*>, AMQPSerializer<Any>> = Function { clazz ->
         PrimitiveSerializer(clazz, sandboxBasicInput)
    }

    val factory = SerializationFactoryImpl(mutableMapOf()).apply {
        registerScheme(AMQPSerializationScheme(
            classLoader = classLoader,
            sandboxBasicInput = sandboxBasicInput,
            executor = executor,
            serializerFactoryFactory = SandboxSerializerFactoryFactory(primitiveSerializerFactory)
        ))
    }
    return SerializationEnvironment.with(factory, p2pContext = p2pContext)
}

inline fun <reified T : Any> SerializedBytes<T>.deserializeFor(classLoader: SandboxClassLoader): Any {
    val clazz = classLoader.loadClassForSandbox(T::class.java)
    return deserializeTo(clazz, classLoader)
}

fun ByteSequence.deserializeTo(clazz: Class<*>, classLoader: SandboxClassLoader): Any {
    val factory = SerializationFactory.defaultFactory
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
