package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.model.*
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Method
import java.lang.reflect.Field
import java.lang.reflect.Type

class ComposableTypePropertySerializer(
        val name: String,
        val isCalculated: Boolean,
        private val readStrategy: PropertyReadStrategy,
        private val writeStrategy: PropertyWriteStrategy) :
            PropertyReadStrategy by readStrategy,
            PropertyWriteStrategy by writeStrategy {

    companion object {
        fun make(name: String, propertyInformation: LocalPropertyInformation, factory: LocalSerializerFactory): ComposableTypePropertySerializer =
                ComposableTypePropertySerializer(
                        name,
                        propertyInformation.isCalculated,
                        PropertyReadStrategy.make(name, propertyInformation.type.typeIdentifier, propertyInformation.type.observedType),
                        PropertyWriteStrategy.make(name, propertyInformation, factory))

        fun makeForEvolution(name: String, isCalculated: Boolean, typeIdentifier: TypeIdentifier, type: Type): ComposableTypePropertySerializer =
                ComposableTypePropertySerializer(
                        name,
                        isCalculated,
                        PropertyReadStrategy.make(name, typeIdentifier, type),
                        EvolutionPropertyWriteStrategy)
    }
}

sealed class TypeModellingPropertyReader {

    companion object {
        fun make(propertyInformation: LocalPropertyInformation) = when(propertyInformation) {
            is LocalPropertyInformation.GetterSetterProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.ConstructorPairedProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.ReadOnlyProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.CalculatedProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.PrivateConstructorPairedProperty -> FieldReader(propertyInformation.observedField)
        }
    }

    abstract fun read(obj: Any?): Any?

    class GetterReader(_getter: Method): TypeModellingPropertyReader() {
        val getter = _getter.also { it.isAccessible = true }

        override fun read(obj: Any?): Any? = if (obj == null) null else getter.invoke(obj)
    }

    class FieldReader(_field: Field): TypeModellingPropertyReader() {
        val field = _field.also { it.isAccessible = true }

        override fun read(obj: Any?): Any? = if (obj == null) null else field.get(obj)
    }
}

interface PropertyReadStrategy {

    companion object {
        fun make(name: String, typeIdentifier: TypeIdentifier, type: Type): PropertyReadStrategy =
                if (SerializerFactory.isPrimitive(type)) {
                    when (type) {
                        Char::class.java, Character::class.java -> AMQPCharPropertyReadStrategy
                        else -> AMQPPropertyReadStrategy
                    }
                } else {
                    DescribedTypeReadStrategy(name, typeIdentifier, type)
                }
    }

    fun readProperty(obj: Any?, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any?

}

interface PropertyWriteStrategy {

    companion object {
        fun make(name: String, propertyInformation: LocalPropertyInformation, factory: LocalSerializerFactory): PropertyWriteStrategy {
            val reader = TypeModellingPropertyReader.make(propertyInformation)
            val type = propertyInformation.type.observedType
            return if (SerializerFactory.isPrimitive(type)) {
                when (type) {
                    Char::class.java, Character::class.java -> AMQPCharPropertyWriteStategy(reader)
                    else -> AMQPPropertyWriteStrategy(reader)
                }
            } else {
                DescribedTypeWriteStrategy(name, propertyInformation, reader) { factory.get(propertyInformation.type) }
            }
        }
    }

    fun writeClassInfo(output: SerializationOutput)

    fun writeProperty(obj: Any?, data: Data, output: SerializationOutput, context: SerializationContext, debugIndent: Int)
}

object EvolutionPropertyWriteStrategy : PropertyWriteStrategy {
    override fun writeClassInfo(output: SerializationOutput) =
            throw UnsupportedOperationException("Evolution serializers cannot write values")

    override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput, context: SerializationContext, debugIndent: Int) =
            throw UnsupportedOperationException("Evolution serializers cannot write values")
}

class DescribedTypeReadStrategy(name: String,
                                typeIdentifier: TypeIdentifier,
                                private val type: Type): PropertyReadStrategy {

    private val nameForDebug = "$name(${typeIdentifier.prettyPrint(false)})"

    override fun readProperty(obj: Any?, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any? =
            ifThrowsAppend({ nameForDebug }) {
                input.readObjectOrNull(obj, schemas, type, context)
            }
}

class DescribedTypeWriteStrategy(name: String,
                                 private val propertyInformation: LocalPropertyInformation,
                                 private val reader: TypeModellingPropertyReader,
                                 private val serializerProvider: () -> AMQPSerializer<Any>) : PropertyWriteStrategy {

    private val serializer by lazy { serializerProvider() }

    private val nameForDebug = "$name(${propertyInformation.type.typeIdentifier.prettyPrint(false)})"

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

class AMQPPropertyWriteStrategy(private val reader: TypeModellingPropertyReader) : PropertyWriteStrategy {
    override fun writeClassInfo(output: SerializationOutput) {}

    override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput,
                               context: SerializationContext, debugIndent: Int
    ) {
        val value = reader.read(obj)
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

class AMQPCharPropertyWriteStategy(private val reader: TypeModellingPropertyReader) : PropertyWriteStrategy {
    override fun writeClassInfo(output: SerializationOutput) {}

    override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput,
                               context: SerializationContext, debugIndent: Int
    ) {
        val input = reader.read(obj)
        if (input != null) data.putShort((input as Char).toShort()) else data.putNull()
    }
}