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
        private val typeInformation: LocalTypeInformation,
        override val typeDescriptor: Symbol,
        private val typeNotation: TypeNotation,
        private val interfaces: List<LocalTypeInformation>,
        private val propertySerializers: Map<String, ComposableTypePropertySerializer>): AMQPSerializer<Any> {

    companion object {
        fun make(typeInformation: LocalTypeInformation, factory: LocalSerializerFactory): AMQPSerializer<Any> {
            val typeDescriptor = factory.createDescriptor(typeInformation.observedType)
            val typeNotation = TypeNotationGenerator(factory).getTypeNotation(typeInformation)

            return when (typeInformation) {
                is LocalTypeInformation.Composable ->
                    make(typeInformation, typeDescriptor, typeNotation, typeInformation.interfaces,
                            typeInformation.properties, factory)
                is LocalTypeInformation.AnInterface ->
                    make(typeInformation, typeDescriptor, typeNotation, typeInformation.interfaces,
                            typeInformation.properties, factory)
                is LocalTypeInformation.Abstract ->
                    make(typeInformation, typeDescriptor, typeNotation, typeInformation.interfaces,
                            typeInformation.properties, factory)
                else -> throw NotSerializableException("Cannot build object serializer for $typeInformation")
            }
        }

        private fun make(typeInformation: LocalTypeInformation, typeDescriptor: Symbol, typeNotation: TypeNotation,
                         interfaces: List<LocalTypeInformation>, properties: Map<String, LocalPropertyInformation>,
                         factory: LocalSerializerFactory): AMQPSerializer<Any> {
            val propertySerializers = properties.mapValues { (name, property) ->
                ComposableTypePropertySerializer.make(name, property, factory)
            }

            return ComposableSerializer(typeInformation, typeDescriptor, typeNotation, interfaces, propertySerializers)
        }
    }

    override val type: Type get() = typeInformation.observedType

    private val objectBuilderProvider by lazy {
        ObjectBuilder.makeProvider(typeInformation as? LocalTypeInformation.Composable
                ?: throw NotSerializableException("Cannot make ObjectBuilder for $typeInformation"))
    }

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            for (iface in interfaces) {
                output.requireSerializer(iface.observedType)
            }

            propertySerializers.values.forEach { serializer ->
                serializer.writeClassInfo(output)
            }
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) {
        data.withDescribed(typeNotation.descriptor) {
            withList {
                propertySerializers.values.forEach { propertySerializer ->
                    propertySerializer.writeProperty(obj, this, output, context, debugIndent + 1)
                }
            }
        }
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any =
            ifThrowsAppend({ type.typeName }) {
                if (obj !is List<*>) throw AMQPNotSerializableException(type, "Body of described type is unexpected $obj")
                if (obj.size != propertySerializers.size) {
                    throw AMQPNotSerializableException(type, "${obj.size} objects to deserialize, but " +
                            "${propertySerializers.size} properties in described type ${type.typeName}")
                }

                val builder = objectBuilderProvider()
                builder.initialize()
                obj.asSequence().zip(propertySerializers.values.asSequence())
                        // Read _all_ properties from the stream
                        .map { (item, property) -> property to property.readProperty(item, schemas, input, context) }
                        // Throw away any calculated properties
                        .filter { (property, _) -> !property.propertyInformation.isCalculated }
                        // Write the rest into the builder
                        .forEachIndexed { slot, (_, propertyValue) -> builder.populate(slot, propertyValue) }
                return builder.build()
            }
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