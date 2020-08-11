package net.corda.serialization.djvm.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.CheckEnum
import net.corda.serialization.djvm.deserializers.DescribeEnum
import net.corda.serialization.djvm.deserializers.GetEnumNames
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
import java.util.function.Predicate

class SandboxEnumSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    predicateFactory: Function<Class<out Predicate<*>>, out Predicate<in Any?>>,
    private val localFactory: LocalSerializerFactory
) : CustomSerializer.Implements<Any>(clazz = classLoader.toSandboxAnyClass(Enum::class.java)) {
    @Suppress("unchecked_cast")
    private val describeEnum: Function<Class<*>, Array<out Any>>
        = taskFactory.apply(DescribeEnum::class.java) as Function<Class<*>, Array<out Any>>
    @Suppress("unchecked_cast")
    private val getEnumNames: Function<Array<out Any>, List<String>>
        = (taskFactory.apply(GetEnumNames::class.java) as Function<Array<out Any>, Array<out Any>>)
            .andThen { it.map(Any::toString) }
    @Suppress("unchecked_cast")
    private val isEnum: Predicate<Class<*>>
        = predicateFactory.apply(CheckEnum::class.java) as Predicate<Class<*>>

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override fun isSerializerFor(clazz: Class<*>): Boolean {
        return super.isSerializerFor(clazz) && isEnum.test(clazz)
    }

    override fun specialiseFor(declaredType: Type): AMQPSerializer<Any>? {
        if (declaredType !is Class<*>) {
            return null
        }
        val members = describeEnum.apply(declaredType)
        val memberNames = getEnumNames.apply(members)
        return ConcreteEnumSerializer(declaredType, members, memberNames, localFactory)
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
    private val members: Array<out Any>,
    private val memberNames: List<String>,
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
                memberNames,
                emptyMap(),
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

        if (enumName != memberNames[enumOrd]) {
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