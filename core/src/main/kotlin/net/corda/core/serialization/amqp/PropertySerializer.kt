package net.corda.core.serialization.amqp

import com.google.common.primitives.Primitives
import org.apache.qpid.proton.amqp.*
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaMethod


abstract class PropertySerializer(val name: String, val readMethod: Method) {
    abstract fun writeProperty(obj: Any, data: Data, output: SerializationOutput)

    protected val clazz = (readMethod.genericReturnType as? Class<*> ?: (readMethod.genericReturnType as? ParameterizedType)?.rawType as? Class<*>) ?: throw NotSerializableException("Property does return class: ${readMethod.genericReturnType}")

    val type: String = generateType()
    val requires: Array<String>? = generateRequires()
    val default: String? = generateDefault()
    val mandatory: Boolean = generateMandatory()

    private fun generateType(): String {
        return if (clazz.isInterface) "*" else {
            val primitiveName = primitiveTypeNames[Primitives.wrap(clazz)]
            return primitiveName ?: clazz.name
        }
    }

    private fun generateRequires(): Array<String>? {
        return if (clazz.isInterface) arrayOf(clazz.name) else null
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
            if (type is Class<*> && Primitives.wrap(type) in primitiveTypeNames) {
                // This is a little inefficient for performance since it does a runtime check of type.  We could do build time check with lots of subclasses here.
                return PrimitivePropertySerializer(name, readMethod)
            } else {
                return ObjectPropertySerializer(name, readMethod)
            }
        }

        private val primitiveTypeNames: Map<Class<*>, String> = mapOf(Boolean::class.java to "boolean",
                Byte::class.java to "byte",
                UnsignedByte::class.java to "ubyte",
                Short::class.java to "short",
                UnsignedShort::class.java to "ushort",
                Integer::class.java to "int",
                UnsignedInteger::class.java to "uint",
                Long::class.java to "long",
                UnsignedLong::class.java to "ulong",
                Float::class.java to "float",
                Double::class.java to "double",
                Decimal32::class.java to "decimal32",
                Decimal64::class.java to "decimal62",
                Decimal128::class.java to "decimal128",
                Char::class.java to "char",
                Date::class.java to "timestamp",
                UUID::class.java to "uuid",
                ByteArray::class.java to "binary",
                String::class.java to "string",
                Symbol::class.java to "symbol")
    }


}

class ObjectPropertySerializer(name: String, readMethod: Method) : PropertySerializer(name, readMethod) {
    override fun writeProperty(obj: Any, data: Data, output: SerializationOutput) {
        output.writeObjectOrNull(readMethod.invoke(obj), data, readMethod.genericReturnType)
    }

}

class PrimitivePropertySerializer(name: String, readMethod: Method) : PropertySerializer(name, readMethod) {
    override fun writeProperty(obj: Any, data: Data, output: SerializationOutput) {
        data.putObject(readMethod.invoke(obj))
    }

}
