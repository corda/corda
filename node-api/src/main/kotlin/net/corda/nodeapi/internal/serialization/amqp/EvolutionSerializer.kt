package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.nodeapi.internal.serialization.carpenter.getTypeAsClass
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.io.NotSerializableException
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaType

/**
 * Serializer for deserialising objects whose definition has changed since they
 * were serialised
 */
class EvolutionSerializer(
        clazz: Type,
        factory: SerializerFactory,
        val readers: List<OldParam?>,
        override val kotlinConstructor: KFunction<Any>?) : ObjectSerializer(clazz, factory) {

    // explicitly set as empty to indicate it's unused by this type of serializer
    override val propertySerializers: Collection<PropertySerializer> = emptyList()

    /**
     * Represents a parameter as would be passed to the constructor of the class as it was
     * when it was serialised and NOT how that class appears now
     *
     * @param type The jvm type of the parameter
     * @param idx where in the parameter list this parameter falls. Required as the parameter
     * order may have been changed and we need to know where into the list to look
     * @param property object to read the actual property value
     */
    data class OldParam(val type: Type, val idx: Int, val property: PropertySerializer) {
        fun readProperty(paramValues: List<*>, schemas: SerializationSchemas, input: DeserializationInput) =
                property.readProperty(paramValues[idx], schemas, input)
    }

    companion object {
        /**
         * Unlike the generic deserialisation case where we need to locate the primary constructor
         * for the object (or our best guess) in the case of an object whose structure has changed
         * since serialisation we need to attempt to locate a constructor that we can use. I.e.
         * it's parameters match the serialised members and it will initialise any newly added
         * elements
         *
         * TODO: Type evolution
         * TODO: rename annotation
         */
        internal fun getEvolverConstructor(type: Type, oldArgs: Map<String?, Type>): KFunction<Any>? {
            val clazz: Class<*> = type.asClass()!!
            if (!isConcrete(clazz)) return null

            val oldArgumentSet = oldArgs.map { Pair(it.key, it.value) }

            var maxConstructorVersion = Integer.MIN_VALUE
            var constructor: KFunction<Any>? = null
            clazz.kotlin.constructors.forEach {
                val version = it.findAnnotation<DeprecatedConstructorForDeserialization>()?.version ?: Integer.MIN_VALUE
                if (oldArgumentSet.containsAll(it.parameters.map { v -> Pair(v.name, v.type.javaType) }) &&
                        version > maxConstructorVersion) {
                    constructor = it
                    maxConstructorVersion = version
                }
            }

            // if we didn't get an exact match revert to existing behaviour, if the new parameters
            // are not mandatory (i.e. nullable) things are fine
            return constructor ?: constructorForDeserialization(type)
        }

        /**
         * Build a serialization object for deserialisation only of objects serialised
         * as different versions of a class
         *
         * @param old is an object holding the schema that represents the object
         *  as it was serialised and the type descriptor of that type
         * @param new is the Serializer built for the Class as it exists now, not
         * how it was serialised and persisted.
         */
        fun make(old: CompositeType, new: ObjectSerializer,
                 factory: SerializerFactory): AMQPSerializer<Any> {

            val oldFieldToType = old.fields.map {
                it.name as String? to it.getTypeAsClass(factory.classloader) as Type
            }.toMap()

            val constructor = getEvolverConstructor(new.type, oldFieldToType) ?:
                    throw NotSerializableException(
                            "Attempt to deserialize an interface: ${new.type}. Serialized form is invalid.")

            val oldArgs = mutableMapOf<String, OldParam>()
            var idx = 0
            old.fields.forEach {
                val returnType = it.getTypeAsClass(factory.classloader)
                oldArgs[it.name] = OldParam(
                        returnType, idx++, PropertySerializer.make(it.name, null, returnType, factory))
            }

            val readers = constructor.parameters.map {
                oldArgs[it.name!!] ?: if (!it.type.isMarkedNullable) {
                    throw NotSerializableException(
                            "New parameter ${it.name} is mandatory, should be nullable for evolution to worK")
                } else null
            }

            return EvolutionSerializer(new.type, factory, readers, constructor)
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        throw UnsupportedOperationException("It should be impossible to write an evolution serializer")
    }

    /**
     * Unlike a normal [readObject] call where we simply apply the parameter deserialisers
     * to the object list of values we need to map that list, which is ordered per the
     * constructor of the original state of the object, we need to map the new parameter order
     * of the current constructor onto that list inserting nulls where new parameters are
     * encountered
     *
     * TODO: Object references
     */
    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput): Any {
        if (obj !is List<*>) throw NotSerializableException("Body of described type is unexpected $obj")

        return construct(readers.map { it?.readProperty(obj, schemas, input) })
    }
}

