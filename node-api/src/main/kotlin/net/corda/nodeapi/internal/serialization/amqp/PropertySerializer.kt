package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.utilities.loggerFor
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.lang.reflect.Field
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.kotlinProperty

abstract class PropertyReader {
    abstract fun read(obj: Any?): Any?
    abstract fun isNullable(): Boolean
}

class PublicPropertyReader(private val readMethod: Method?) : PropertyReader() {
    init {
        readMethod?.isAccessible = true
    }

    private fun Method.returnsNullable(): Boolean {
        try {
            val returnTypeString = this.declaringClass.kotlin.memberProperties.firstOrNull { it.javaGetter == this }?.returnType?.toString() ?: "?"
            return returnTypeString.endsWith('?') || returnTypeString.endsWith('!')
        } catch (e: kotlin.reflect.jvm.internal.KotlinReflectionInternalError) {
            // This might happen for some types, e.g. kotlin.Throwable? - the root cause of the issue is: https://youtrack.jetbrains.com/issue/KT-13077
            // TODO: Revisit this when Kotlin issue is fixed.

            loggerFor<PropertySerializer>().error("Unexpected internal Kotlin error", e)
            return true
        }
    }

    override fun read(obj: Any?): Any? {
        return readMethod!!.invoke(obj)
    }

    override fun isNullable(): Boolean = readMethod?.returnsNullable() ?: false
}

class PrivatePropertyReader(val field: Field, parentType: Type) : PropertyReader() {
    init {
        loggerFor<PropertySerializer>().warn("Create property Serializer for private property '${field.name}' not "
                + "exposed by a getter on class '$parentType'\n"
                + "\tNOTE: This behaviour will be deprecated at some point in the future and a getter required")
    }

    override fun read(obj: Any?): Any? {
        field.isAccessible = true
        val rtn = field.get(obj)
        field.isAccessible = false
        return rtn
    }

    override fun isNullable() = try {
        field.kotlinProperty?.returnType?.isMarkedNullable ?: false
    } catch (e: kotlin.reflect.jvm.internal.KotlinReflectionInternalError) {
        // This might happen for some types, e.g. kotlin.Throwable? - the root cause of the issue is: https://youtrack.jetbrains.com/issue/KT-13077
        // TODO: Revisit this when Kotlin issue is fixed.
        loggerFor<PropertySerializer>().error("Unexpected internal Kotlin error", e)
        true
    }
}


/**
 * Base class for serialization of a property of an object.
 */
sealed class PropertySerializer(val name: String, val propertyReader: PropertyReader, val resolvedType: Type) {
    abstract fun writeClassInfo(output: SerializationOutput)
    abstract fun writeProperty(obj: Any?, data: Data, output: SerializationOutput)
    abstract fun readProperty(obj: Any?, schemas: SerializationSchemas, input: DeserializationInput): Any?

    val type: String = generateType()
    val requires: List<String> = generateRequires()
    val default: String? = generateDefault()
    val mandatory: Boolean = generateMandatory()

    private val isInterface: Boolean get() = resolvedType.asClass()?.isInterface == true
    private val isJVMPrimitive: Boolean get() = resolvedType.asClass()?.isPrimitive == true

    private fun generateType(): String {
        return if (isInterface || resolvedType == Any::class.java) "*" else SerializerFactory.nameForType(resolvedType)
    }

    private fun generateRequires(): List<String> {
        return if (isInterface) listOf(SerializerFactory.nameForType(resolvedType)) else emptyList()
    }

    private fun generateDefault(): String? =
            if (isJVMPrimitive) {
                when (resolvedType) {
                    java.lang.Boolean.TYPE -> "false"
                    java.lang.Character.TYPE -> "&#0"
                    else -> "0"
                }
            } else {
                null
            }

    private fun generateMandatory(): Boolean {
        return isJVMPrimitive || !(propertyReader.isNullable())
    }

    companion object {
        fun make(name: String, readMethod: PropertyReader, resolvedType: Type, factory: SerializerFactory): PropertySerializer {
            if (SerializerFactory.isPrimitive(resolvedType)) {
                return when (resolvedType) {
                    Char::class.java, Character::class.java -> AMQPCharPropertySerializer(name, readMethod)
                    else -> AMQPPrimitivePropertySerializer(name, readMethod, resolvedType)
                }
            } else {
                return DescribedTypePropertySerializer(name, readMethod, resolvedType) { factory.get(null, resolvedType) }
            }
        }
    }

    /**
     * A property serializer for a complex type (another object).
     */
    class DescribedTypePropertySerializer(
            name: String,
            readMethod: PropertyReader,
            resolvedType: Type,
            private val lazyTypeSerializer: () -> AMQPSerializer<*>) : PropertySerializer(name, readMethod, resolvedType) {
        // This is lazy so we don't get an infinite loop when a method returns an instance of the class.
        private val typeSerializer: AMQPSerializer<*> by lazy { lazyTypeSerializer() }

        override fun writeClassInfo(output: SerializationOutput) = ifThrowsAppend({ nameForDebug }) {
            if (resolvedType != Any::class.java) {
                typeSerializer.writeClassInfo(output)
            }
        }

        override fun readProperty(
                obj: Any?,
                schemas: SerializationSchemas,
                input: DeserializationInput): Any? = ifThrowsAppend({ nameForDebug }) {
            input.readObjectOrNull(obj, schemas, resolvedType)
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput) = ifThrowsAppend({ nameForDebug }) {
            output.writeObjectOrNull(propertyReader.read(obj), data, resolvedType)
        }

        private val nameForDebug = "$name(${resolvedType.typeName})"
    }

    /**
     * A property serializer for most AMQP primitive type (Int, String, etc).
     */
    class AMQPPrimitivePropertySerializer(
            name: String,
            readMethod: PropertyReader,
            resolvedType: Type) : PropertySerializer(name, readMethod, resolvedType) {
        override fun writeClassInfo(output: SerializationOutput) {}

        override fun readProperty(obj: Any?, schemas: SerializationSchemas, input: DeserializationInput): Any? {
            return if (obj is Binary) obj.array else obj
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput) {
            val value = propertyReader.read(obj)
            if (value is ByteArray) {
                data.putObject(Binary(value))
            } else {
                data.putObject(value)
            }
        }
    }

    /**
     * A property serializer for the AMQP char type, needed as a specialisation as the underlying
     * value of the character is stored in numeric UTF-16 form and on deserialisation requires explicit
     * casting back to a char otherwise it's treated as an Integer and a TypeMismatch occurs
     */
    class AMQPCharPropertySerializer(name: String, readMethod: PropertyReader) :
            PropertySerializer(name, readMethod, Character::class.java) {
        override fun writeClassInfo(output: SerializationOutput) {}

        override fun readProperty(obj: Any?, schemas: SerializationSchemas, input: DeserializationInput): Any? {
            return if (obj == null) null else (obj as Short).toChar()
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput) {
            val input = propertyReader.read(obj)
            if (input != null) data.putShort((input as Char).toShort()) else data.putNull()
        }
    }
}

