package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaMethod


abstract class PropertySerializer(val name: String, val readMethod: Method) {
    abstract fun writeProperty(obj: Any?, data: Data, output: SerializationOutput)
    abstract fun readProperty(obj: Any?, envelope: Envelope, input: DeserializationInput): Any?

    protected val clazz = (readMethod.genericReturnType as? Class<*> ?: (readMethod.genericReturnType as? ParameterizedType)?.rawType as? Class<*>) ?: throw NotSerializableException("Property does return class: ${readMethod.genericReturnType}")

    val type: String = generateType()
    val requires: List<String> = generateRequires()
    val default: String? = generateDefault()
    val mandatory: Boolean = generateMandatory()

    private fun generateType(): String {
        return if (clazz.isInterface) "*" else {
            val primitiveName = SerializerFactory.primitiveTypeName(clazz)
            return primitiveName ?: clazz.name
        }
    }

    private fun generateRequires(): List<String> {
        return if (clazz.isInterface) listOf(readMethod.genericReturnType.typeName) else emptyList()
    }

    private fun generateDefault(): String? {
        if (clazz.isPrimitive) {
            if (clazz == java.lang.Boolean.TYPE) {
                return "false"
            } else {
                return "0"
            }
        } else {
            return null
        }
    }

    private fun generateMandatory(): Boolean {
        // TODO: support @NotNull
        return clazz.isPrimitive || !readMethod.returnsKotlinNullable()
    }

    private fun Method.returnsKotlinNullable(): Boolean {
        return (declaringClass.kotlin.memberProperties.firstOrNull { it.getter.javaMethod == this })?.returnType?.isMarkedNullable ?: true
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
