package net.corda.serialization.djvm.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.CreateMap
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.AMQPSerializer
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.LocalSerializerFactory
import net.corda.serialization.internal.amqp.Schema
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializationSchemas
import net.corda.serialization.internal.amqp.redescribe
import net.corda.serialization.internal.model.LocalTypeInformation
import net.corda.serialization.internal.model.TypeIdentifier
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.EnumMap
import java.util.NavigableMap
import java.util.SortedMap
import java.util.TreeMap
import java.util.function.Function

class SandboxMapSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    private val localFactory: LocalSerializerFactory
) : CustomSerializer.Implements<Any>(clazz = classLoader.toSandboxAnyClass(Map::class.java)) {
    @Suppress("unchecked_cast")
    private val creator: Function<Array<out Any>, out Any?>
        = taskFactory.apply(CreateMap::class.java) as Function<Array<out Any>, out Any?>

    // The order matters here - the first match should be the most specific one.
    // Kotlin preserves the ordering for us by associating into a LinkedHashMap.
    private val supportedTypes: Map<Class<Any>, Class<out Map<*, *>>> = listOf(
        TreeMap::class.java,
        LinkedHashMap::class.java,
        NavigableMap::class.java,
        SortedMap::class.java,
        EnumMap::class.java,
        Map::class.java
    ).associateBy {
        classLoader.toSandboxAnyClass(it)
    }

    private fun getBestMatchFor(type: Class<Any>): Map.Entry<Class<Any>, Class<out Map<*, *>>>
        = supportedTypes.entries.first { it.key.isAssignableFrom(type) }

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override fun specialiseFor(declaredType: Type): AMQPSerializer<Any>? {
        if (declaredType !is ParameterizedType) {
            return null
        }

        @Suppress("unchecked_cast")
        val rawType = declaredType.rawType as Class<Any>
        return ConcreteMapSerializer(declaredType, getBestMatchFor(rawType), creator, localFactory)
    }

    override fun readObject(
        obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext
    ): Any {
        throw UnsupportedOperationException("Factory only")
    }

    override fun writeDescribedObject(
        obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext
    ) {
        throw UnsupportedOperationException("Factory Only")
    }
}

private class ConcreteMapSerializer(
    declaredType: ParameterizedType,
    private val matchingType: Map.Entry<Class<Any>, Class<out Map<*, *>>>,
    private val creator: Function<Array<out Any>, out Any?>,
    factory: LocalSerializerFactory
) : AMQPSerializer<Any> {
    override val type: ParameterizedType = declaredType

    override val typeDescriptor: Symbol by lazy {
        factory.createDescriptor(
            LocalTypeInformation.AMap(
                observedType = declaredType,
                typeIdentifier = TypeIdentifier.forGenericType(declaredType),
                keyType = factory.getTypeInformation(declaredType.actualTypeArguments[0]),
                valueType = factory.getTypeInformation(declaredType.actualTypeArguments[1])
            )
        )
    }

    override fun readObject(
        obj: Any,
        schemas: SerializationSchemas,
        input: DeserializationInput,
        context: SerializationContext
    ): Any {
        val inboundKeyType = type.actualTypeArguments[0]
        val inboundValueType = type.actualTypeArguments[1]
        return ifThrowsAppend(type::getTypeName) {
            val entries = (obj as Map<*, *>).map {
                arrayOf(
                    input.readObjectOrNull(redescribe(it.key, inboundKeyType), schemas, inboundKeyType, context),
                    input.readObjectOrNull(redescribe(it.value, inboundValueType), schemas, inboundValueType, context)
                )
            }.toTypedArray()
            creator.apply(arrayOf(matchingType.key, entries))!!
        }
    }

    override fun writeClassInfo(output: SerializationOutput) {
        abortReadOnly()
    }

    override fun writeObject(
        obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int
    ) {
        abortReadOnly()
    }
}
