package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.model.BaseLocalTypes
import org.apache.qpid.proton.codec.Data
import java.lang.UnsupportedOperationException
import java.lang.reflect.Type

/**
 * Used whenever a deserialized enums fingerprint doesn't match the fingerprint of the generated
 * serializer object. I.e. the deserializing code has a different version of the code either newer or
 * older). The changes will have been documented using the transformation annotations, a copy of which
 * are encoded as part of the AMQP envelope.
 *
 * This function ascertains which version of the enumeration is newer by comparing the length of the
 * transformations list. Since transformation annotations should only ever be added, never removed even
 * when seemingly unneeded (such as repeated renaming of a single constant), the longer list will dictate
 * which is more up to date.
 *
 * The list of transforms come from two places, the class as it exists on the current class path and the
 * class as it exists as it was serialized. In the case of the former we can build the list by using
 * reflection on the class. In the case of the latter the transforms are retrieved from the AMQP envelope.
 *
 * With a set of transforms chosen we calculate the set of all possible constants, then using the
 * transformation rules we create a mapping between those values and the values that exist on the
 * current class
 *
 * @property type The enum as it exists now, not as it did when it was serialized (either in the past
 * or future).
 * @param factory the [SerializerFactory] that is building this serialization object.
 * @property conversions A mapping between all potential enum constants that could've been assigned to
 * an instance of the enum as it existed at time of serialisation and those that exist now
 * @property ordinals Convenience mapping of constant to ordinality
 */
class EnumEvolutionSerializer(
        override val type: Type,
        factory: LocalSerializerFactory,
        private val baseLocalTypes: BaseLocalTypes,
        private val conversions: Map<String, String>,
        private val ordinals: Map<String, Int>) : AMQPSerializer<Any> {
    override val typeDescriptor = factory.createDescriptor(type)

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ): Any {
        val enumName = (obj as List<*>)[0] as String

        val converted = conversions[enumName] ?: throw AMQPNotSerializableException(type, "No rule to evolve enum constant $type::$enumName")
        val ordinal = ordinals[converted] ?: throw AMQPNotSerializableException(type, "Ordinal not found for enum value $type::$converted")

        return baseLocalTypes.enumConstants.apply(type.asClass())[ordinal]
    }

    override fun writeClassInfo(output: SerializationOutput) {
        throw UnsupportedOperationException("It should be impossible to write an evolution serializer")
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        throw UnsupportedOperationException("It should be impossible to write an evolution serializer")
    }
}
