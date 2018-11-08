package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.model.LocalPropertyInformation
import net.corda.serialization.internal.model.LocalTypeInformation
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Method
import java.lang.reflect.Field

sealed class TypeModellingPropertyReader {

    companion object {
        fun make(propertyInformation: LocalPropertyInformation) = when(propertyInformation) {
            is LocalPropertyInformation.GetterSetterProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.ConstructorPairedProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.ReadOnlyProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.CalculatedProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.PrivateConstructorPairedProperty -> FieldReader(
                    propertyInformation.observedField.also { it.isAccessible = true })
        }
    }

    abstract fun read(obj: Any?): Any?

    class GetterReader(val getter: Method): TypeModellingPropertyReader() {
        override fun read(obj: Any?): Any? = if (obj == null) null else getter.invoke(obj)
    }

    class FieldReader(val field: Field): TypeModellingPropertyReader() {
        override fun read(obj: Any?): Any? = if (obj == null) null else field.get(obj)
    }
}

sealed class ComposableTypePropertySerializer {

    abstract val name: String
    abstract val propertyInformation: LocalPropertyInformation
    abstract val reader: TypeModellingPropertyReader

    abstract fun writeClassInfo(output: SerializationOutput)
    abstract fun writeProperty(obj: Any?, data: Data, output: SerializationOutput, context: SerializationContext, debugIndent: Int = 0)
    abstract fun readProperty(obj: Any?, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any?

    companion object {
        fun make(name: String, propertyInformation: LocalPropertyInformation, factory: LocalSerializerFactory): ComposableTypePropertySerializer {
            val reader = TypeModellingPropertyReader.make(propertyInformation)
            val type = propertyInformation.type.observedType
            return if (SerializerFactory.isPrimitive(type)) {
                when (type) {
                    Char::class.java, Character::class.java -> AMQPCharPropertySerializer(name, propertyInformation, reader)
                    else -> AMQPPrimitivePropertySerializer(name, propertyInformation, reader)
                }
            } else {
                DescribedTypePropertySerializer(name, propertyInformation, reader) { factory.get(propertyInformation.type) }
            }
        }
    }

    class DescribedTypePropertySerializer(
            override val name: String,
            override val propertyInformation: LocalPropertyInformation,
            override val reader: TypeModellingPropertyReader,
            val serializerProvider: () -> AMQPSerializer<Any>) : ComposableTypePropertySerializer() {

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

        override fun readProperty(obj: Any?, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any? =
                ifThrowsAppend({ nameForDebug }) {
                    input.readObjectOrNull(obj, schemas, propertyInformation.type.observedType, context)
                }
    }

    /**
     * A property serializer for most AMQP primitive type (Int, String, etc).
     */
    class AMQPPrimitivePropertySerializer(
            override val name: String,
            override val propertyInformation: LocalPropertyInformation,
            override val reader: TypeModellingPropertyReader) : ComposableTypePropertySerializer() {
        override fun writeClassInfo(output: SerializationOutput) {}

        override fun readProperty(obj: Any?, schemas: SerializationSchemas,
                                  input: DeserializationInput, context: SerializationContext
        ): Any? {
            return if (obj is Binary) obj.array else obj
        }

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

    /**
     * A property serializer for the AMQP char type, needed as a specialisation as the underlying
     * value of the character is stored in numeric UTF-16 form and on deserialization requires explicit
     * casting back to a char otherwise it's treated as an Integer and a TypeMismatch occurs
     */
    class AMQPCharPropertySerializer(
            override val name: String,
            override val propertyInformation: LocalPropertyInformation,
            override val reader: TypeModellingPropertyReader) :
            ComposableTypePropertySerializer() {

        override fun writeClassInfo(output: SerializationOutput) {}

        override fun readProperty(obj: Any?, schemas: SerializationSchemas,
                                  input: DeserializationInput, context: SerializationContext
        ): Any? {
            return if (obj == null) null else (obj as Short).toChar()
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput,
                                   context: SerializationContext, debugIndent: Int
        ) {
            val input = reader.read(obj)
            if (input != null) data.putShort((input as Char).toShort()) else data.putNull()
        }
    }
}