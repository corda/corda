package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.amqp.AMQPTypeIdentifiers.isPrimitive
import net.corda.serialization.internal.model.*
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Method
import java.lang.reflect.Field
import java.lang.reflect.Type

/**
 * A strategy for reading a property value during deserialization.
 */
interface PropertyReadStrategy {

    companion object {
        /**
         * Select the correct strategy for reading properties, based on the property type.
         */
        fun make(name: String, typeIdentifier: TypeIdentifier, type: Type): PropertyReadStrategy =
                if (isPrimitive(typeIdentifier)) {
                    when (typeIdentifier) {
                        in characterTypes -> AMQPCharPropertyReadStrategy
                        else -> AMQPPropertyReadStrategy
                    }
                } else {
                    DescribedTypeReadStrategy(name, typeIdentifier, type)
                }
    }

    /**
     * Use this strategy to read the value of a property during deserialization.
     */
    fun readProperty(obj: Any?, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any?

}

/**
 * A strategy for writing a property value during serialisation.
 */
interface PropertyWriteStrategy {

    companion object {
        /**
         * Select the correct strategy for writing properties, based on the property information.
         */
        fun make(name: String, propertyInformation: LocalPropertyInformation, factory: LocalSerializerFactory): PropertyWriteStrategy {
            val reader = PropertyReader.make(propertyInformation)
            val type = propertyInformation.type
            return if (isPrimitive(type.typeIdentifier)) {
                when (type.typeIdentifier) {
                    in characterTypes -> AMQPCharPropertyWriteStategy(reader)
                    else -> AMQPPropertyWriteStrategy(reader)
                }
            } else {
                DescribedTypeWriteStrategy(name, propertyInformation, reader) { factory.get(propertyInformation.type) }
            }
        }
    }

    /**
     * Write any [TypeNotation] needed to the [SerializationOutput].
     */
    fun writeClassInfo(output: SerializationOutput)

    /**
     * Write the property's value to the [SerializationOutput].
     */
    fun writeProperty(obj: Any?, data: Data, output: SerializationOutput, context: SerializationContext, debugIndent: Int)
}

/**
 * Combines strategies for reading and writing a given property's value during serialisation/deserialisation.
 */
interface PropertySerializer : PropertyReadStrategy, PropertyWriteStrategy {
    /**
     * The name of the property.
     */
    val name: String
    /**
     * Whether the property is calculated.
     */
    val isCalculated: Boolean
}

/**
 * A [PropertySerializer] for a property of a [LocalTypeInformation.Composable] type.
 */
class ComposableTypePropertySerializer(
        override val name: String,
        override val isCalculated: Boolean,
        private val readStrategy: PropertyReadStrategy,
        private val writeStrategy: PropertyWriteStrategy) :
            PropertySerializer,
            PropertyReadStrategy by readStrategy,
            PropertyWriteStrategy by writeStrategy {

    companion object {
        /**
         * Make a [PropertySerializer] for the given [LocalPropertyInformation].
         *
         * @param name The name of the property.
         * @param propertyInformation [LocalPropertyInformation] for the property.
         * @param factory The [LocalSerializerFactory] to use when writing values for this property.
         */
        fun make(name: String, propertyInformation: LocalPropertyInformation, factory: LocalSerializerFactory): PropertySerializer =
                ComposableTypePropertySerializer(
                        name,
                        propertyInformation.isCalculated,
                        PropertyReadStrategy.make(name, propertyInformation.type.typeIdentifier, propertyInformation.type.observedType),
                        PropertyWriteStrategy.make(name, propertyInformation, factory))

        /**
         * Make a [PropertySerializer] for use in deserialization only, when deserializing a type that requires evolution.
         *
         * @param name The name of the property.
         * @param isCalculated Whether the property is calculated.
         * @param typeIdentifier The [TypeIdentifier] for the property type.
         * @param type The local [Type] for the property type.
         */
        fun makeForEvolution(name: String, isCalculated: Boolean, typeIdentifier: TypeIdentifier, type: Type): PropertySerializer =
                ComposableTypePropertySerializer(
                        name,
                        isCalculated,
                        PropertyReadStrategy.make(name, typeIdentifier, type),
                        EvolutionPropertyWriteStrategy)
    }
}

/**
 * Obtains the value of a property from an instance of the type to which that property belongs, either by calling a getter method
 * or by reading the value of a private backing field.
 */
sealed class PropertyReader {

    companion object {
        /**
         * Make a [PropertyReader] based on the provided [LocalPropertyInformation].
         */
        fun make(propertyInformation: LocalPropertyInformation) = when(propertyInformation) {
            is LocalPropertyInformation.GetterSetterProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.ConstructorPairedProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.ReadOnlyProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.CalculatedProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.PrivateConstructorPairedProperty -> FieldReader(propertyInformation.observedField)
        }
    }

    /**
     * Get the value of the property from the supplied instance, or null if the instance is itself null.
     */
    abstract fun read(obj: Any?): Any?

    /**
     * Reads a property using a getter [Method].
     */
    class GetterReader(private val getter: Method): PropertyReader() {
        init {
            getter.isAccessible = true
        }

        override fun read(obj: Any?): Any? = if (obj == null) null else getter.invoke(obj)
    }

    /**
     * Reads a property using a backing [Field].
     */
    class FieldReader(private val field: Field): PropertyReader() {
        init {
            field.isAccessible = true
        }

        override fun read(obj: Any?): Any? = if (obj == null) null else field.get(obj)
    }
}

private val characterTypes = setOf(
        TypeIdentifier.forClass(Char::class.javaObjectType),
        TypeIdentifier.forClass(Char::class.javaPrimitiveType!!)
)

object EvolutionPropertyWriteStrategy : PropertyWriteStrategy {
    override fun writeClassInfo(output: SerializationOutput) =
            throw UnsupportedOperationException("Evolution serializers cannot write values")

    override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput, context: SerializationContext, debugIndent: Int) =
            throw UnsupportedOperationException("Evolution serializers cannot write values")
}

/**
 * Read a type that comes with its own [TypeDescriptor], by calling back into [RemoteSerializerFactory] to obtain a suitable
 * serializer for that descriptor.
 */
class DescribedTypeReadStrategy(name: String,
                                typeIdentifier: TypeIdentifier,
                                private val type: Type): PropertyReadStrategy {

    private val nameForDebug = "$name(${typeIdentifier.prettyPrint(false)})"

    override fun readProperty(obj: Any?, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any? =
            ifThrowsAppend({ nameForDebug }) {
                input.readObjectOrNull(redescribe(obj, type), schemas, type, context)
            }
}

/**
 * Writes a property value into [SerializationOutput], together with a schema information describing it.
 */
class DescribedTypeWriteStrategy(private val name: String,
                                 private val propertyInformation: LocalPropertyInformation,
                                 private val reader: PropertyReader,
                                 private val serializerProvider: () -> AMQPSerializer<Any>) : PropertyWriteStrategy {

    // Lazy to avoid getting into infinite loops when there are cycles.
    private val serializer by lazy { serializerProvider() }

    private val nameForDebug get() = "$name(${propertyInformation.type.typeIdentifier.prettyPrint(false)})"

    override fun writeClassInfo(output: SerializationOutput) {
        if (propertyInformation.type !is LocalTypeInformation.Top) {
            serializer.writeClassInfo(output)
        }
    }

    override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput, context: SerializationContext,
                               debugIndent: Int) = ifThrowsAppend({ nameForDebug }) {
        val propertyValue = reader.read(obj)
        output.writeObjectOrNull(propertyValue, data, propertyInformation.type.observedType, context, debugIndent)
    }
}

object AMQPPropertyReadStrategy : PropertyReadStrategy {
    override fun readProperty(obj: Any?, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any? =
            if (obj is Binary) obj.array else obj
}

class AMQPPropertyWriteStrategy(private val reader: PropertyReader) : PropertyWriteStrategy {
    override fun writeClassInfo(output: SerializationOutput) {}

    override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput,
                               context: SerializationContext, debugIndent: Int
    ) {
        val value = reader.read(obj)
        // ByteArrays have to be wrapped in an AMQP Binary wrapper.
        if (value is ByteArray) {
            data.putObject(Binary(value))
        } else {
            data.putObject(value)
        }
    }
}

object AMQPCharPropertyReadStrategy : PropertyReadStrategy {
    override fun readProperty(obj: Any?, schemas: SerializationSchemas,
                              input: DeserializationInput, context: SerializationContext
    ): Any? {
        return if (obj == null) null else (obj as Short).toChar()
    }
}

class AMQPCharPropertyWriteStategy(private val reader: PropertyReader) : PropertyWriteStrategy {
    override fun writeClassInfo(output: SerializationOutput) {}

    override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput,
                               context: SerializationContext, debugIndent: Int
    ) {
        val input = reader.read(obj)
        if (input != null) data.putShort((input as Char).toShort()) else data.putNull()
    }
}