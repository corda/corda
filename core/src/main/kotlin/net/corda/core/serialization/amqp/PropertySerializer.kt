package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Method
import kotlin.reflect.jvm.kotlinFunction


abstract class PropertySerializer(val name: String, val readMethod: Method) {
    abstract fun writeProperty(obj: Any?, data: Data, output: SerializationOutput)
    abstract fun readProperty(obj: Any?, envelope: Envelope, input: DeserializationInput): Any?

    val type: String = generateType()
    val requires: List<String> = generateRequires()
    val default: String? = generateDefault()
    val mandatory: Boolean = generateMandatory()

    private val isInterface: Boolean get() = (readMethod.genericReturnType as? Class<*>)?.isInterface ?: false
    private val isPrimitive: Boolean get() = (readMethod.genericReturnType as? Class<*>)?.isPrimitive ?: false

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
        if (isPrimitive) {
            if (readMethod.genericReturnType == java.lang.Boolean.TYPE) {
                return "false"
            } else {
                return "0"
            }
        } else {
            return null
        }
    }

    private fun generateMandatory(): Boolean {
        return isPrimitive || !readMethod.returnsNullable()
    }

    private fun Method.returnsNullable(): Boolean {
        val returnTypeString = this.kotlinFunction?.returnType?.toString() ?: "?"
        return returnTypeString.endsWith('?') || returnTypeString.endsWith('!')
    }

    companion object {
        fun make(name: String, readMethod: Method): PropertySerializer {
            val type = readMethod.genericReturnType
            if (SerializerFactory.isPrimitive(type)) {
                // This is a little inefficient for performance since it does a runtime check of type.  We could do build time check with lots of subclasses here.
                return PrimitivePropertySerializer(name, readMethod)
            } else {
                return ObjectPropertySerializer(name, readMethod)
            }
        }
    }
}

class ObjectPropertySerializer(name: String, readMethod: Method) : PropertySerializer(name, readMethod) {
    override fun readProperty(obj: Any?, envelope: Envelope, input: DeserializationInput): Any? {
        return input.readObjectOrNull(obj, envelope, readMethod.genericReturnType)
    }

    override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput) {
        output.writeObjectOrNull(readMethod.invoke(obj), data, readMethod.genericReturnType)
    }
}

class PrimitivePropertySerializer(name: String, readMethod: Method) : PropertySerializer(name, readMethod) {
    override fun readProperty(obj: Any?, envelope: Envelope, input: DeserializationInput): Any? {
        return obj
    }

    override fun writeProperty(obj: Any?, data: Data, output: SerializationOutput) {
        data.putObject(readMethod.invoke(obj))
    }

}
