package net.corda.nodeapi.internal.serialization.amqp

import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Type
import java.util.*

/**
 * @property transforms
 *
 */
class EnumEvolutionSerializer(
        clazz: Type,
        factory: SerializerFactory,
        private val conversions : Map<String, String>,
        private val ordinals : Map<String, Int>) : AMQPSerializer<Any> {
    override val type: Type = clazz
    override val typeDescriptor = Symbol.valueOf("$DESCRIPTOR_DOMAIN:${fingerprintForType(type, factory)}")!!

    companion object {
        fun MutableMap<String, String>.mapInPlace(f : (String)->String) {
            val i = this.iterator()
            while(i.hasNext()) {
                val curr = (i.next())
                curr.setValue(f(curr.value))
            }
        }

        /**
         * @param old
         * @param new
         */
        fun make(old: RestrictedType,
                 new: AMQPSerializer<Any>,
                 factory: SerializerFactory,
                 transformsFromBlob: TransformsSchema): AMQPSerializer<Any> {

            val wireTransforms = transformsFromBlob.types[old.name]
            val localTransforms = TransformsSchema.get(old.name, factory)
            val transforms = if (wireTransforms?.size ?: -1 > localTransforms.size) wireTransforms!! else localTransforms

            // if either of these isn't of the cast type then something has gone terribly wrong
            // elsewhere in the code
            @Suppress("UNCHECKED_CAST")
            val defaultRules = transforms[TransformTypes.EnumDefault] as? List<EnumDefaultSchemaTransform>
            @Suppress("UNCHECKED_CAST")
            val renameRules = transforms[TransformTypes.Rename] as? List<RenameSchemaTransform>

            // What values exist on the enum as it exists on the class path
            val localVals = new.type.asClass()!!.enumConstants.map { it.toString() }

            var conversions : MutableMap<String, String> = new.type.asClass()!!.enumConstants.map { it.toString() }
                    .union(defaultRules?.map { it.new }?.toSet() ?: emptySet())
                    .union(renameRules?.map { it.to } ?: emptySet())
                    .associateBy({ it }, { it })
                    .toMutableMap()

            val rules : MutableMap<String, String> = mutableMapOf()
            rules.putAll(defaultRules?.associateBy({ it.new }, { it.old }) ?: emptyMap())
            rules.putAll(renameRules?.associateBy({ it.to }, { it.from }) ?: emptyMap())

            while (conversions.filter { it.value !in localVals }.isNotEmpty()) {
                conversions.mapInPlace { rules[it] ?: it }
            }

            var idx = 0
            return EnumEvolutionSerializer(new.type, factory, conversions, localVals.associateBy( {it}, { idx++ }))
        }
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput): Any {
        var enumName = (obj as List<*>)[0] as String

        if (enumName !in conversions) {
            throw NotSerializableException ("No rule to evolve enum constant $type::$enumName")
        }

        return type.asClass()!!.enumConstants[ordinals[conversions[enumName]]!!]
    }

    override fun writeClassInfo(output: SerializationOutput) {
        throw IllegalAccessException("It should be impossible to write an evolution serializer")
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        throw IllegalAccessException("It should be impossible to write an evolution serializer")
    }
}