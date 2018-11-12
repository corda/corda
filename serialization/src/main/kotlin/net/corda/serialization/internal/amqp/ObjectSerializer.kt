package net.corda.serialization.internal.amqp

import net.corda.core.internal.isConcreteClass
import net.corda.core.serialization.SerializationContext
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.serialization.internal.amqp.SerializerFactory.Companion.nameForType
import net.corda.serialization.internal.model.*
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Type
import kotlin.reflect.jvm.javaConstructor

class ComposableSerializer(
        override val type: Type,
        override val typeDescriptor: Symbol,
        private val reader: ComposableObjectReader,
        private val writer: ComposableObjectWriter): AMQPSerializer<Any> {

    companion object {
        fun make(typeInformation: LocalTypeInformation, factory: LocalSerializerFactory): AMQPSerializer<Any> {
            val typeDescriptor = factory.createDescriptor(typeInformation.observedType)
            val typeNotation = TypeNotationGenerator(factory).getTypeNotation(typeInformation)

            return when (typeInformation) {
                is LocalTypeInformation.Composable ->
                    makeForComposable(typeInformation, typeNotation, typeDescriptor, factory)
                is LocalTypeInformation.AnInterface ->
                    makeForAbstract(typeNotation, typeInformation.interfaces, typeInformation.properties, typeInformation, typeDescriptor, factory)
                is LocalTypeInformation.Abstract ->
                    makeForAbstract(typeNotation, typeInformation.interfaces, typeInformation.properties, typeInformation, typeDescriptor, factory)
                else -> throw NotSerializableException("Cannot build object serializer for $typeInformation")
            }
        }

        private fun makeForAbstract(typeNotation: CompositeType,
                                    interfaces: List<LocalTypeInformation>,
                                    properties: Map<String, LocalPropertyInformation>,
                                    typeInformation: LocalTypeInformation,
                                    typeDescriptor: Symbol,
                                    factory: LocalSerializerFactory): AbstractComposableSerializer {
            val writer = ComposableObjectWriter(typeNotation, interfaces, makePropertySerializers(properties, factory))
            return AbstractComposableSerializer(typeInformation.observedType, typeDescriptor, writer)
        }

        private fun makeForComposable(typeInformation: LocalTypeInformation.Composable,
                                      typeNotation: CompositeType,
                                      typeDescriptor: Symbol,
                                      factory: LocalSerializerFactory): ComposableSerializer {
            val propertySerializers = makePropertySerializers(typeInformation.properties, factory)
            val reader = ComposableObjectReader(
                    typeInformation.typeIdentifier,
                    propertySerializers,
                    ObjectBuilder.makeProvider(typeInformation))

            val writer = ComposableObjectWriter(
                    typeNotation,
                    typeInformation.interfaces,
                    propertySerializers)

            return ComposableSerializer(
                    typeInformation.observedType,
                    typeDescriptor,
                    reader,
                    writer)
        }

        private fun makePropertySerializers(properties: Map<String, LocalPropertyInformation>,
                                            factory: LocalSerializerFactory): Map<String, ComposableTypePropertySerializer> =
            properties.mapValues { (name, property) ->
                ComposableTypePropertySerializer.make(name, property, factory)
            }
    }

    override fun writeClassInfo(output: SerializationOutput) = writer.writeClassInfo(output)

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) =
            writer.writeObject(obj, data, type, output, context, debugIndent)

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any =
            reader.readObject(obj, schemas, input, context)
}

class ComposableObjectWriter(
        private val typeNotation: TypeNotation,
        private val interfaces: List<LocalTypeInformation>,
        private val propertySerializers: Map<String, ComposableTypePropertySerializer>
) {
    fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            for (iface in interfaces) {
                output.requireSerializer(iface.observedType)
            }

            propertySerializers.values.forEach { serializer ->
                serializer.writeClassInfo(output)
            }
        }
    }

    fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) {
        data.withDescribed(typeNotation.descriptor) {
            withList {
                propertySerializers.values.forEach { propertySerializer ->
                    propertySerializer.writeProperty(obj, this, output, context, debugIndent + 1)
                }
            }
        }
    }
}

class ComposableObjectReader(
        val typeIdentifier: TypeIdentifier,
        private val propertySerializers: Map<String, ComposableTypePropertySerializer>,
        private val objectBuilderProvider: () -> ObjectBuilder
) {

    fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any =
            ifThrowsAppend({ typeIdentifier.prettyPrint(false) }) {
                if (obj !is List<*>) throw NotSerializableException("Body of described type is unexpected $obj")
                if (obj.size < propertySerializers.size) {
                    throw NotSerializableException("${obj.size} objects to deserialize, but " +
                            "${propertySerializers.size} properties in described type ${typeIdentifier.prettyPrint(false)}")
                }

                val builder = objectBuilderProvider()
                builder.initialize()
                obj.asSequence().zip(propertySerializers.values.asSequence())
                        // Read _all_ properties from the stream
                        .map { (item, property) -> property to property.readProperty(item, schemas, input, context) }
                        // Throw away any calculated properties
                        .filter { (property, _) -> !property.isCalculated }
                        // Write the rest into the builder
                        .forEachIndexed { slot, (_, propertyValue) -> builder.populate(slot, propertyValue) }
                return builder.build()
            }
}

class AbstractComposableSerializer(
        override val type: Type,
        override val typeDescriptor: Symbol,
        private val writer: ComposableObjectWriter): AMQPSerializer<Any> {
    override fun writeClassInfo(output: SerializationOutput) =
        writer.writeClassInfo(output)

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) =
        writer.writeObject(obj, data, type, output, context, debugIndent)

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any =
        throw UnsupportedOperationException("Cannot deserialize abstract type ${type.typeName}")
}

class EvolutionComposableSerializer(
        override val type: Type,
        override val typeDescriptor: Symbol,
        private val reader: ComposableObjectReader): AMQPSerializer<Any> {

    companion object {
        fun make(localTypeInformation: LocalTypeInformation.Composable, remoteTypeInformation: RemoteTypeInformation.Composable, constructor: LocalConstructorInformation,
                 properties: Map<String, LocalPropertyInformation>, classLoader: ClassLoader): EvolutionComposableSerializer {
            val reader = ComposableObjectReader(
                    localTypeInformation.typeIdentifier,
                    makePropertySerializers(properties, remoteTypeInformation.properties, classLoader),
                    EvolutionObjectBuilder.makeProvider(localTypeInformation.typeIdentifier, constructor, properties, remoteTypeInformation.properties.keys.sorted()))

            return EvolutionComposableSerializer(
                    localTypeInformation.observedType,
                    Symbol.valueOf(remoteTypeInformation.typeDescriptor),
                    reader)
        }

        private fun makePropertySerializers(localProperties: Map<String, LocalPropertyInformation>,
                                            remoteProperties: Map<String, RemotePropertyInformation>,
                                            classLoader: ClassLoader): Map<String, ComposableTypePropertySerializer> =
                remoteProperties.mapValues { (name, property) ->
                    val localProperty = localProperties[name]
                    val isCalculated = localProperty?.isCalculated ?: false
                    val type = localProperty?.type?.observedType ?: property.type.typeIdentifier.getLocalType(classLoader)
                    ComposableTypePropertySerializer.makeForEvolution(name, isCalculated, property.type.typeIdentifier, type)
                }
    }

    override fun writeClassInfo(output: SerializationOutput) =
            throw UnsupportedOperationException("Evolved types cannot be written")

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) =
            throw UnsupportedOperationException("Evolved types cannot be written")

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any =
            reader.readObject(obj, schemas, input, context)

}

/**
 * Responsible for serializing and deserializing a regular object instance via a series of properties
 * (matched with a constructor).
 */
open class ObjectSerializer(val clazz: Type, factory: LocalSerializerFactory) : AMQPSerializer<Any> {
    override val type: Type get() = clazz
    open val kotlinConstructor = if (clazz.asClass().isConcreteClass) constructorForDeserialization(clazz) else null
    val javaConstructor by lazy { kotlinConstructor?.javaConstructor }

    companion object {
        private val logger = contextLogger()
    }

    open val propertySerializers: PropertySerializers by lazy {
        propertiesForSerialization(kotlinConstructor, clazz, factory)
    }

    private val typeName = nameForType(clazz)

    override val typeDescriptor: Symbol = factory.createDescriptor(type)

    // We restrict to only those annotated or whitelisted
    private val interfaces = interfacesForSerialization(clazz, factory)

    internal open val typeNotation: TypeNotation by lazy {
        CompositeType(typeName, null, generateProvides(), Descriptor(typeDescriptor), generateFields())
    }

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            for (iface in interfaces) {
                output.requireSerializer(iface)
            }

            propertySerializers.serializationOrder.forEach { property ->
                property.serializer.writeClassInfo(output)
            }
        }
    }

    override fun writeObject(
            obj: Any,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext,
            debugIndent: Int) = ifThrowsAppend({ clazz.typeName }
    ) {
        if (propertySerializers.deserializableSize != javaConstructor?.parameterCount &&
                javaConstructor?.parameterCount ?: 0 > 0
        ) {
            throw AMQPNotSerializableException(type, "Serialization constructor for class $type expects "
                    + "${javaConstructor?.parameterCount} parameters but we have ${propertySerializers.size} "
                    + "properties to serialize.")
        }

        // Write described
        data.withDescribed(typeNotation.descriptor) {
            // Write list
            withList {
                propertySerializers.serializationOrder.forEach { property ->
                    property.serializer.writeProperty(obj, this, output, context, debugIndent + 1)
                }
            }
        }
    }

    override fun readObject(
            obj: Any,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ clazz.typeName }) {
        if (obj is List<*>) {
            if (obj.size != propertySerializers.size) {
                throw AMQPNotSerializableException(type, "${obj.size} objects to deserialize, but " +
                        "${propertySerializers.size} properties in described type $typeName")
            }

            return if (propertySerializers.byConstructor) {
                readObjectBuildViaConstructor(obj, schemas, input, context)
            } else {
                readObjectBuildViaSetters(obj, schemas, input, context)
            }
        } else {
            throw AMQPNotSerializableException(type, "Body of described type is unexpected $obj")
        }
    }

    private fun readObjectBuildViaConstructor(
            obj: List<*>,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ clazz.typeName }) {
        logger.trace { "Calling construction based construction for ${clazz.typeName}" }

        return construct(propertySerializers.serializationOrder
                .zip(obj)
                .mapNotNull { (accessor, obj) ->
                    // Ensure values get read out of input no matter what
                    val value = accessor.serializer.readProperty(obj, schemas, input, context)

                    when(accessor) {
                        is PropertyAccessorConstructor -> accessor.initialPosition to value
                        is CalculatedPropertyAccessor -> null
                        else -> throw UnsupportedOperationException(
                                "${accessor::class.simpleName} accessor not supported " +
                                        "for constructor-based object building")
                    }
                }
                .sortedWith(compareBy { it.first })
                .map { it.second })
    }

    private fun readObjectBuildViaSetters(
            obj: List<*>,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ clazz.typeName }) {
        logger.trace { "Calling setter based construction for ${clazz.typeName}" }

        val instance: Any = javaConstructor?.newInstanceUnwrapped() ?: throw AMQPNotSerializableException(
                type,
                "Failed to instantiate instance of object $clazz")

        // read the properties out of the serialised form, since we're invoking the setters the order we
        // do it in doesn't matter
        val propertiesFromBlob = obj
                .zip(propertySerializers.serializationOrder)
                .map { it.second.serializer.readProperty(it.first, schemas, input, context) }

        // one by one take a property and invoke the setter on the class
        propertySerializers.serializationOrder.zip(propertiesFromBlob).forEach {
            it.first.set(instance, it.second)
        }

        return instance
    }

    private fun generateFields(): List<Field> {
        return propertySerializers.serializationOrder.map {
            Field(it.serializer.name, it.serializer.type, it.serializer.requires, it.serializer.default, null, it.serializer.mandatory, false)
        }
    }

    private fun generateProvides(): List<String> = interfaces.map { nameForType(it) }

    fun construct(properties: List<Any?>): Any {
        logger.trace { "Calling constructor: '$javaConstructor' with properties '$properties'" }

        if (properties.size != javaConstructor?.parameterCount) {
            throw AMQPNotSerializableException(type, "Serialization constructor for class $type expects "
                    + "${javaConstructor?.parameterCount} parameters but we have ${properties.size} "
                    + "serialized properties.")
        }

        return javaConstructor?.newInstanceUnwrapped(*properties.toTypedArray())
                ?: throw AMQPNotSerializableException(
                        type,
                        "Attempt to deserialize an interface: $clazz. Serialized form is invalid.")
    }

    private fun <T> Constructor<T>.newInstanceUnwrapped(vararg args: Any?): T {
        try {
            return newInstance(*args)
        } catch (e: InvocationTargetException) {
            throw e.cause!!
        }
    }
}