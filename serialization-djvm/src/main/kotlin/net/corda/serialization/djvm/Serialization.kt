@file:JvmName("Serialization")
package net.corda.serialization.djvm

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.utilities.ByteSequence
import net.corda.djvm.rewiring.createRawPredicateFactory
import net.corda.djvm.rewiring.createSandboxPredicate
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.CheckEnum
import net.corda.serialization.djvm.deserializers.DescribeEnum
import net.corda.serialization.djvm.deserializers.GetEnumNames
import net.corda.serialization.djvm.serializers.PrimitiveSerializer
import net.corda.serialization.internal.GlobalTransientClassWhiteList
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AMQPSerializer
import net.corda.serialization.internal.amqp.amqpMagic
import net.corda.serialization.internal.model.BaseLocalTypes
import java.util.EnumSet
import java.util.function.Function
import java.util.function.Predicate

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

    val sandboxBasicInput = classLoader.createBasicInput()
    val rawTaskFactory = classLoader.createRawTaskFactory()
    val taskFactory = rawTaskFactory.compose(classLoader.createSandboxFunction())
    val predicateFactory = classLoader.createRawPredicateFactory().compose(classLoader.createSandboxPredicate())

    val primitiveSerializerFactory: Function<Class<*>, AMQPSerializer<Any>> = Function { clazz ->
         PrimitiveSerializer(clazz, sandboxBasicInput)
    }
    @Suppress("unchecked_cast")
    val isEnumPredicate = predicateFactory.apply(CheckEnum::class.java) as Predicate<Class<*>>
    @Suppress("unchecked_cast")
    val enumConstants = taskFactory.apply(DescribeEnum::class.java) as Function<Class<*>, Array<out Any>>
    @Suppress("unchecked_cast")
    val enumConstantNames = enumConstants.andThen(taskFactory.apply(GetEnumNames::class.java))
        .andThen { (it as Array<out Any>).map(Any::toString) } as Function<Class<*>, List<String>>

    val sandboxLocalTypes = BaseLocalTypes(
        collectionClass = classLoader.toSandboxClass(Collection::class.java),
        enumSetClass = classLoader.toSandboxClass(EnumSet::class.java),
        exceptionClass = classLoader.toSandboxClass(Exception::class.java),
        mapClass = classLoader.toSandboxClass(Map::class.java),
        stringClass = classLoader.toSandboxClass(String::class.java),
        isEnum = isEnumPredicate,
        enumConstants = enumConstants,
        enumConstantNames = enumConstantNames
    )
    val schemeBuilder = SandboxSerializationSchemeBuilder(
        classLoader = classLoader,
        sandboxBasicInput = sandboxBasicInput,
        rawTaskFactory = rawTaskFactory,
        taskFactory = taskFactory,
        predicateFactory = predicateFactory,
        customSerializerClassNames = customSerializerClassNames,
        serializationWhitelistNames = serializationWhitelistNames,
        serializerFactoryFactory = SandboxSerializerFactoryFactory(
            primitiveSerializerFactory = primitiveSerializerFactory,
            localTypes = sandboxLocalTypes
        )
    )
    val factory = SerializationFactoryImpl(mutableMapOf()).apply {
        registerScheme(schemeBuilder.buildFor(p2pContext))
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
