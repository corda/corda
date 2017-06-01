package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Method
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter

/**
 * Base class for serialization of a property of an object.
 */
sealed class PropertySerializer(val name: String, val readMethod: Method) {
    abstract fun writeClassInfo(output: SerializationOutput)
    abstract fun writeProperty(obj: Any?, data: Data, output: SerializationOutput)
    abstract fun readProperty(obj: Any?, schema: Schema, input: DeserializationInput): Any?

    val type: String = generateType()
    val requires: List<String> = generateRequires()
    val default: String? = generateDefault()
    val mandatory: Boolean = generateMandatory()

    private val isInterface: Boolean get() = (readMethod.genericReturnType as? Class<*>)?.isInterface ?: false
    private val isJVMPrimitive: Boolean get() = (readMethod.genericReturnType as? Class<*>)?.isPrimitive ?: false

    private fun generateType(): String {
        return if (isInterface) "*" else {
            val primitiveName = SerializerFactory.primitiveTypeName(readMethod.genericReturnType)
            return primitiveName ?: readMethod.genericReturnType.typeName
        }
    }

    private fun generateRequires(): List<String> {
        return if (isInterface) listOf(readMethod.genericReturnType.typeName) else emptyList()
    }

    private fun generateDefault(): String? {
        if (isJVMPrimitive) {
            return when (readMethod.genericReturnType) {
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
        fun make(name: String, readMethod: Method, factory: SerializerFactory): PropertySerializer {
            val type = readMethod.genericReturnType
            if (SerializerFactory.isPrimitive(type)) {
                // This is a little inefficient for performance since it does a runtime check of type.  We could do build time check with lots of subclasses here.
                return AMQPPrimitivePropertySerializer(name, readMethod)
            } else {
                return DescribedTypePropertySerializer(name, readMethod) { factory.get(null, type) }
            }
        }
    }

    /**
     * A property serializer for a complex type (another object).
     */
    class DescribedTypePropertySerializer(name: String, readMethod: Method, private val lazyTypeSerializer: () -> AMQPSerializer<out Any>) : PropertySerializer(name, readMethod) {
        // This is lazy so we don't get an infinite loop when a method returns an instance of the class.
        private val typeSerializer: AMQPSerializer<out Any> by lazy { lazyTypeSerializer() }

        override fun writeClassInfo(output: SerializationOutput) {
            typeSerializer.writeClassInfo(output)
        }

        override fun readProperty(obj: Any?, schema: Schema, input: DeserializationInput): Any? {
            return input.readObjectOrNull(obj, schema, readMethod.genericReturnType)
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput) {
            output.writeObjectOrNull(readMethod.invoke(obj), data, readMethod.genericReturnType)
        }
    }

    /**
     * A property serializer for an AMQP primitive type (Int, String, etc).
     */
    class AMQPPrimitivePropertySerializer(name: String, readMethod: Method) : PropertySerializer(name, readMethod) {
        override fun writeClassInfo(output: SerializationOutput) {}

        override fun readProperty(obj: Any?, schema: Schema, input: DeserializationInput): Any? {
            return obj
        }

        override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput) {
            data.putObject(readMethod.invoke(obj))
        }
    }
}

