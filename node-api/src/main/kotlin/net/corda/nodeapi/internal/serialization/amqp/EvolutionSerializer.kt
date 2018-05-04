/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.serialization.SerializationContext
import net.corda.nodeapi.internal.serialization.carpenter.getTypeAsClass
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Type
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaType


/**
 * Serializer for deserializing objects whose definition has changed since they
 * were serialised.
 *
 * @property oldReaders A linked map representing the properties of the object as they were serialized. Note
 * this may contain properties that are no longer needed by the class. These *must* be read however to ensure
 * any refferenced objects in the object stream are captured properly
 * @property kotlinConstructor
 * @property constructorArgs used to hold the properties as sent to the object's constructor. Passed in as a
 * pre populated array as properties not present on the old constructor must be initialised in the factory
 */
abstract class EvolutionSerializer(
        clazz: Type,
        factory: SerializerFactory,
        protected val oldReaders: Map<String, OldParam>,
        override val kotlinConstructor: KFunction<Any>?) : ObjectSerializer(clazz, factory) {

    // explicitly set as empty to indicate it's unused by this type of serializer
    override val propertySerializers = PropertySerializersEvolution()

    /**
     * Represents a parameter as would be passed to the constructor of the class as it was
     * when it was serialised and NOT how that class appears now
     *
     * @param resultsIndex index into the constructor argument list where the read property
     * should be placed
     * @param property object to read the actual property value
     */
    data class OldParam(var resultsIndex: Int, val property: PropertySerializer) {
        fun readProperty(obj: Any?, schemas: SerializationSchemas, input: DeserializationInput,
                         new: Array<Any?>, context: SerializationContext
        ) = property.readProperty(obj, schemas, input, context).apply {
            if (resultsIndex >= 0) {
                new[resultsIndex] = this
            }
        }
    }

    companion object {
        /**
         * Unlike the generic deserialization case where we need to locate the primary constructor
         * for the object (or our best guess) in the case of an object whose structure has changed
         * since serialisation we need to attempt to locate a constructor that we can use. For example,
         * its parameters match the serialised members and it will initialise any newly added
         * elements.
         *
         * TODO: Type evolution
         * TODO: rename annotation
         */
        private fun getEvolverConstructor(type: Type, oldArgs: Map<String, OldParam>): KFunction<Any>? {
            val clazz: Class<*> = type.asClass()!!

            if (!isConcrete(clazz)) return null

            val oldArgumentSet = oldArgs.map { Pair(it.key as String?, it.value.property.resolvedType) }

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

        private fun makeWithConstructor(
                new: ObjectSerializer,
                factory: SerializerFactory,
                constructor: KFunction<Any>,
                readersAsSerialized: Map<String, OldParam>): AMQPSerializer<Any> {
            val constructorArgs = arrayOfNulls<Any?>(constructor.parameters.size)

            constructor.parameters.withIndex().forEach {
                readersAsSerialized.get(it.value.name!!)?.apply {
                    this.resultsIndex = it.index
                } ?: if (!it.value.type.isMarkedNullable) {
                    throw NotSerializableException(
                            "New parameter ${it.value.name} is mandatory, should be nullable for evolution to worK")
                }
            }
            return EvolutionSerializerViaConstructor(new.type, factory, readersAsSerialized, constructor, constructorArgs)
        }

        private fun makeWithSetters(
                new: ObjectSerializer,
                factory: SerializerFactory,
                constructor: KFunction<Any>,
                readersAsSerialized: Map<String, OldParam>,
                classProperties: Map<String, PropertyDescriptor>): AMQPSerializer<Any> {
            val setters = propertiesForSerializationFromSetters(classProperties,
                    new.type,
                    factory).associateBy({ it.getter.name }, { it })
            return EvolutionSerializerViaSetters(new.type, factory, readersAsSerialized, constructor, setters)
        }

        /**
         * Build a serialization object for deserialization only of objects serialised
         * as different versions of a class.
         *
         * @param old is an object holding the schema that represents the object
         *  as it was serialised and the type descriptor of that type
         * @param new is the Serializer built for the Class as it exists now, not
         * how it was serialised and persisted.
         * @param factory the [SerializerFactory] associated with the serialization
         * context this serializer is being built for
         */
        fun make(old: CompositeType, new: ObjectSerializer,
                 factory: SerializerFactory): AMQPSerializer<Any> {
            // The order in which the properties were serialised is important and must be preserved
            val readersAsSerialized = LinkedHashMap<String, OldParam>()
            old.fields.forEach {
                readersAsSerialized[it.name] = try {
                    OldParam(-1, PropertySerializer.make(it.name, EvolutionPropertyReader(),
                            it.getTypeAsClass(factory.classloader), factory))
                } catch (e: ClassNotFoundException) {
                    throw NotSerializableException(e.message)
                }
            }

            // cope with the situation where a generic interface was serialised as a type, in such cases
            // return the synthesised object which is, given the absence of a constructor, a no op
            val constructor = getEvolverConstructor(new.type, readersAsSerialized) ?: return new

            val classProperties = new.type.asClass()?.propertyDescriptors() ?: emptyMap()

            return if (classProperties.isNotEmpty() && constructor.parameters.isEmpty()) {
                makeWithSetters(new, factory, constructor, readersAsSerialized, classProperties)
            } else {
                makeWithConstructor(new, factory, constructor, readersAsSerialized)
            }
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        throw UnsupportedOperationException("It should be impossible to write an evolution serializer")
    }
}

class EvolutionSerializerViaConstructor(
        clazz: Type,
        factory: SerializerFactory,
        oldReaders: Map<String, EvolutionSerializer.OldParam>,
        kotlinConstructor: KFunction<Any>?,
        private val constructorArgs: Array<Any?>) : EvolutionSerializer(clazz, factory, oldReaders, kotlinConstructor) {
    /**
     * Unlike a normal [readObject] call where we simply apply the parameter deserialisers
     * to the object list of values we need to map that list, which is ordered per the
     * constructor of the original state of the object, we need to map the new parameter order
     * of the current constructor onto that list inserting nulls where new parameters are
     * encountered.
     *
     * TODO: Object references
     */
    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ): Any {
        if (obj !is List<*>) throw NotSerializableException("Body of described type is unexpected $obj")

        // *must* read all the parameters in the order they were serialized
        oldReaders.values.zip(obj).map { it.first.readProperty(it.second, schemas, input, constructorArgs, context) }

        return javaConstructor?.newInstance(*(constructorArgs)) ?: throw NotSerializableException(
                "Attempt to deserialize an interface: $clazz. Serialized form is invalid.")
    }
}

/**
 * Specific instance of an [EvolutionSerializer] where the properties of the object are set via calling
 * named setter functions on the instantiated object.
 */
class EvolutionSerializerViaSetters(
        clazz: Type,
        factory: SerializerFactory,
        oldReaders: Map<String, EvolutionSerializer.OldParam>,
        kotlinConstructor: KFunction<Any>?,
        private val setters: Map<String, PropertyAccessor>) : EvolutionSerializer(clazz, factory, oldReaders, kotlinConstructor) {

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ): Any {
        if (obj !is List<*>) throw NotSerializableException("Body of described type is unexpected $obj")

        val instance: Any = javaConstructor?.newInstance() ?: throw NotSerializableException(
                "Failed to instantiate instance of object $clazz")

        // *must* read all the parameters in the order they were serialized
        oldReaders.values.zip(obj).forEach {
            // if that property still exists on the new object then set it
            it.first.property.readProperty(it.second, schemas, input, context).apply {
                setters[it.first.property.name]?.set(instance, this)
            }
        }
        return instance
    }
}

/**
 * Instances of this type are injected into a [SerializerFactory] at creation time to dictate the
 * behaviour of evolution within that factory. Under normal circumstances this will simply
 * be an object that returns an [EvolutionSerializer]. Of course, any implementation that
 * extends this class can be written to invoke whatever behaviour is desired.
 */
abstract class EvolutionSerializerGetterBase {
    abstract fun getEvolutionSerializer(
            factory: SerializerFactory,
            typeNotation: TypeNotation,
            newSerializer: AMQPSerializer<Any>,
            schemas: SerializationSchemas): AMQPSerializer<Any>
}

/**
 * The normal use case for generating an [EvolutionSerializer]'s based on the differences
 * between the received schema and the class as it exists now on the class path,
 */
class EvolutionSerializerGetter : EvolutionSerializerGetterBase() {
    override fun getEvolutionSerializer(factory: SerializerFactory,
                                        typeNotation: TypeNotation,
                                        newSerializer: AMQPSerializer<Any>,
                                        schemas: SerializationSchemas): AMQPSerializer<Any> {
        return factory.getSerializersByDescriptor().computeIfAbsent(typeNotation.descriptor.name!!) {
            when (typeNotation) {
                is CompositeType -> EvolutionSerializer.make(typeNotation, newSerializer as ObjectSerializer, factory)
                is RestrictedType -> {
                    // The fingerprint of a generic collection can be changed through bug fixes to the
                    // fingerprinting function making it appear as if the class has altered whereas it hasn't.
                    // Given we don't support the evolution of these generic containers, if it appears
                    // one has been changed, simply return the original serializer and associate it with
                    // both the new and old fingerprint
                    if (newSerializer is CollectionSerializer || newSerializer is MapSerializer) {
                        newSerializer
                    } else {
                        EnumEvolutionSerializer.make(typeNotation, newSerializer, factory, schemas)
                    }
                }
            }
        }
    }
}

