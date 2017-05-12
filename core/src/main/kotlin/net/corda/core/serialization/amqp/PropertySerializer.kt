package net.corda.core.serialization.amqp

import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter

/**
 * Base class for serialization of a property of an object.
 */
sealed class PropertySerializer(val name: String, val readMethod: Method, val resolvedType: Type) {
    abstract fun writeClassInfo(output: SerializationOutput)
    abstract fun writeProperty(obj: Any?, data: Data, output: SerializationOutput)
    abstract fun readProperty(obj: Any?, schema: Schema, input: DeserializationInput): Any?

    val type: String = generateType()
    val requires: List<String> = generateRequires()
    val default: String? = generateDefault()
    val mandatory: Boolean = generateMandatory()

    private val isInterface: Boolean get() = resolvedType.asClass()?.isInterface ?: false
    private val isJVMPrimitive: Boolean get() = resolvedType.asClass()?.isPrimitive ?: false

    private fun generateType(): String {
        return if (isInterface || resolvedType == Any::class.java) "*" else SerializerFactory.nameForType(resolvedType)
    }

    private fun generateRequires(): List<String> {
        return if (isInterface) listOf(SerializerFactory.nameForType(resolvedType)) else emptyList()
    }

    private fun generateDefault(): String? {
        if (isJVMPrimitive) {
            return when (resolvedType) {
                java.lang.Boolean.TYPE -> "false"
                java.lang.Character.TYPE -> "&#0"
                else -> "0"
            }
        } else {
            return null
        }
    }

    private fun generateMandatory(): Boolean {
        return isJVMPrimitive || !readMethod.returnsNullable()
    }

    private fun Method.returnsNullable(): Boolean {
        val returnTypeString = this.declaringClass.kotlin.memberProperties.firstOrNull { it.javaGetter == this }?.returnType?.toString() ?: "?"
        return returnTypeString.endsWith('?') || returnTypeString.endsWith('!')
    }

    companion object {
        fun make(name: String, readMethod: Method, resolvedType: Type, factory: SerializerFactory): PropertySerializer {
            //val type = readMethod.genericReturnType
            if (SerializerFactory.isPrimitive(resolvedType)) {
                // This is a little inefficient for performance since it does a runtime check of type.  We could do build time check with lots of subclasses here.
                return AMQPPrimitivePropertySerializer(name, readMethod, resolvedType)
            } else {
                return DescribedTypePropertySerializer(name, readMethod, resolvedType) { factory.get(null, resolvedType) }
            }
        }
    }

    /**
     * A property serializer for a complex type (another object).
     */
    class DescribedTypePropertySerializer(name: String, readMethod: Method, resolvedType: Type, private val lazyTypeSerializer: () -> AMQPSerializer<*>) : PropertySerializer(name, readMethod, resolvedType) {
        // This is lazy so we don't get an infinite loop when a method returns an instance of the class.
        private val typeSerializer: AMQPSerializer<*> by lazy { lazyTypeSerializer() }

        override fun writeClassInfo(output: SerializationOutput) {
            if (resolvedType != Any::class.java) {
                typeSerializer.writeClassInfo(output)
            }
        }

        override fun readProperty(obj: Any?, schema: Schema, input: DeserializationInput): Any? {
            return input.readObjectOrNull(obj, schema, resolvedType)
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput) {
            output.writeObjectOrNull(readMethod.invoke(obj), data, resolvedType)
        }
    }

    /**
     * A property serializer for an AMQP primitive type (Int, String, etc).
     */
    class AMQPPrimitivePropertySerializer(name: String, readMethod: Method, resolvedType: Type) : PropertySerializer(name, readMethod, resolvedType) {

        override fun writeClassInfo(output: SerializationOutput) {}

        override fun readProperty(obj: Any?, schema: Schema, input: DeserializationInput): Any? {
            return if (obj is Binary) obj.array else obj
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput) {
            val value = readMethod.invoke(obj)
            if (value is ByteArray) {
                data.putObject(Binary(value))
            } else {
                data.putObject(value)
            }
        }
    }
}

