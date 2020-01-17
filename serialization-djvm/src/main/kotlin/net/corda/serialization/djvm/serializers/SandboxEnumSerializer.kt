package net.corda.serialization.djvm.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.DescribeEnum
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.AMQPNotSerializableException
import net.corda.serialization.internal.amqp.AMQPSerializer
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.LocalSerializerFactory
import net.corda.serialization.internal.amqp.Schema
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializationSchemas
import net.corda.serialization.internal.model.EnumTransforms
import net.corda.serialization.internal.model.LocalTypeInformation
import net.corda.serialization.internal.model.TypeIdentifier
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.util.function.Function

class SandboxEnumSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    private val localFactory: LocalSerializerFactory
) : CustomSerializer.Implements<Any>(clazz = classLoader.toSandboxAnyClass(Enum::class.java)) {
    @Suppress("unchecked_cast")
    private val describer: Function<Class<*>, Array<Any>>
        = taskFactory.apply(DescribeEnum::class.java) as Function<Class<*>, Array<Any>>

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override fun specialiseFor(declaredType: Type): AMQPSerializer<Any>? {
        if (declaredType !is Class<*>) {
            return null
        }
        val members = describer.apply(declaredType)
        return ConcreteEnumSerializer(declaredType, members, localFactory)
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

private class ConcreteEnumSerializer(
    declaredType: Class<*>,
    private val members: Array<Any>,
    factory: LocalSerializerFactory
) : AMQPSerializer<Any> {
    override val type: Class<*> = declaredType

    override val typeDescriptor: Symbol by lazy {
        factory.createDescriptor(
            /*
             * Partially populated, providing just the information
             * required by the fingerprinter.
             */
            LocalTypeInformation.AnEnum(
                declaredType,
                TypeIdentifier.forGenericType(declaredType),
                members.map { it.toString() },
                emptyList(),
                EnumTransforms.empty
            )
        )
    }

    override fun readObject(
        obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext
    ): Any {
        val enumName = (obj as List<*>)[0] as String
        val enumOrd = obj[1] as Int
        val fromOrd = members[enumOrd]

        if (enumName != fromOrd.toString()) {
            throw AMQPNotSerializableException(
                type,
                "Deserializing obj as enum $type with value $enumName.$enumOrd but ordinality has changed"
            )
        }
        return fromOrd
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