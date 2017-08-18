package net.corda.nodeapi.internal.serialization.amqp

import net.corda.nodeapi.internal.serialization.carpenter.getTypeAsClass
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.io.NotSerializableException
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaType

/**
 *
 */
class EvolutionSerializer(
        clazz: Type,
        factory: SerializerFactory,
        val oldParams : Map<String, oldParam>,
        override val kotlinConstructor: KFunction<Any>?) : ObjectSerializer (clazz, factory) {

    // explicitly null this out as we won't be using this list
    override val propertySerializers: Collection<PropertySerializer> = listOf()

    /**
     * represents a paramter as would be passed to the constructor of the class as it was
     * when it was serialised and NOT how that class appears now
     */
    data class oldParam (val type: Type, val idx: Int, val property: PropertySerializer)

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

            val oldArgumentSet = oldArgs.map { Pair (it.key, it.value) }

            clazz.kotlin.constructors.forEach {
                if (oldArgumentSet.containsAll(it.parameters.map { v -> Pair(v.name, v.type.javaType) })) {
                    return it
                }
            }

            // if we didn't get an exact match revert to existing behaviour, if the new parameters
            // are not mandatory (i.e. nullable) things are fine
            return constructorForDeserialization(type)
        }

        /**
         * Build a serialization object for deserialisation only of objects serislaised
         * as different versions of a class
         *
         * @param old is an object holding the schema that represents the object
         *  as it was serialised and the type descriptor of that type
         * @param new is the Serializer built for the Class as it exists now, not
         * how it was serialised and persisted.
         */
        fun make (old: schemaAndDescriptor, new: ObjectSerializer,
                  factory: SerializerFactory) : AMQPSerializer<Any> {

            val oldFieldToType = (old.schema.types.first() as CompositeType).fields.map {
                it.name as String? to it.getTypeAsClass(factory.classloader) as Type
            }.toMap()

            val constructor = getEvolverConstructor(new.type, oldFieldToType) ?:
                    throw NotSerializableException(
                            "Attempt to deserialize an interface: new.type. Serialized form is invalid.")

            val oldArgs = mutableMapOf<String, oldParam>()
            var idx = 0
            (old.schema.types.first() as CompositeType).fields.forEach {
                val returnType = it.getTypeAsClass(factory.classloader)
                oldArgs[it.name] = oldParam(
                        returnType, idx++, PropertySerializer.make(it.name, null, returnType, factory))
            }

            return EvolutionSerializer(new.type, factory, oldArgs, constructor)
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        throw IllegalAccessException ("It should be impossible to write an evolution serializer")
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): Any {
        if (obj !is List<*>) throw NotSerializableException("Body of described type is unexpected $obj")

        val newArgs = kotlinConstructor?.parameters?.associateBy({ it.name!! }, {it.type.isMarkedNullable}) ?:
            throw NotSerializableException ("Bad Constructor selected for object $obj")

        return construct(newArgs.map {
            val param = oldParams[it.key]
            if (param == null && !it.value) {
                throw NotSerializableException(
                        "New parameter ${it.key} is mandatory, should be nullable for evolution to worK")
            }

            param?.property?.readProperty(obj[param.idx], schema, input)
        })
    }
}

