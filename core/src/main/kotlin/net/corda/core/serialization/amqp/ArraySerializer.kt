package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Type

/**
 * Serialization / deserialization of arrays.
 */
open class ArraySerializer(override val type: Type, factory: SerializerFactory) : AMQPSerializer<Any> {
    companion object {
        fun make(type: Type, factory: SerializerFactory) = when (type) {
                Array<Character>::class.java -> CharArraySerializer (factory)
                else -> ArraySerializer(type, factory)
        }
    }

    override val typeDescriptor by lazy { "$DESCRIPTOR_DOMAIN:${fingerprintForType(type, factory)}" }
    internal val elementType: Type by lazy { type.componentType() }
    internal open val typeName by lazy { type.typeName }

    internal val typeNotation: TypeNotation by lazy {
        RestrictedType(typeName, null, emptyList(), "list", Descriptor(typeDescriptor, null), emptyList())
    }

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(elementType)
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        // Write described
        data.withDescribed(typeNotation.descriptor) {
            withList {
                for (entry in obj as Array<*>) {
                    output.writeObjectOrNull(entry, this, elementType)
                }
            }
        }
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): Any {
        if (obj is List<*>) {
            return obj.map { input.readObjectOrNull(it, schema, elementType) }.toArrayOfType(elementType)
        } else throw NotSerializableException("Expected a List but found $obj")
    }

    open fun <T> List<T>.toArrayOfType(type: Type): Any {
        val elementType = type.asClass() ?: throw NotSerializableException("Unexpected array element type $type")
        val list = this
        return java.lang.reflect.Array.newInstance(elementType, this.size).apply {
            (0..lastIndex).forEach { java.lang.reflect.Array.set(this, it, list[it]) }
        }
    }
}

/**
 * Boxed Character arrays required a specialisation to handle the type conversion properly when populating
 * the array since Kotlin won't allow an implicit cast from Int (as they're stored as 16bit ints) to Char
 */
class CharArraySerializer(factory: SerializerFactory) : ArraySerializer(Array<Character>::class.java, factory) {
    override fun <T> List<T>.toArrayOfType(type: Type): Any {
        val elementType = type.asClass() ?: throw NotSerializableException("Unexpected array element type $type")
        val list = this
        return java.lang.reflect.Array.newInstance(elementType, this.size).apply {
            (0..lastIndex).forEach { java.lang.reflect.Array.set(this, it, (list[it] as Int).toChar()) }
        }
    }
}

/**
 * Specialisation of [ArraySerializer] that handles arrays of unboxed java primitive types
 */
abstract class PrimArraySerializer (type: Type, factory: SerializerFactory) : ArraySerializer(type, factory) {
    companion object {
        // We don't need to handle the unboxed byte type as that is coercible to a byte array, but
        // the other 7 primitive types we do
        val primTypes: Map<Type, (SerializerFactory) -> PrimArraySerializer> = mapOf(
                IntArray::class.java to { f -> PrimIntArraySerializer(f, "int[p]") },
                CharArray::class.java to { f -> PrimCharArraySerializer(f, "char[p]") },
                BooleanArray::class.java to { f -> PrimBooleanArraySerializer(f, "boolean[p]") },
                FloatArray::class.java to { f -> PrimFloatArraySerializer(f, "float[p]") },
                ShortArray::class.java to { f -> PrimShortArraySerializer(f, "short[p]") },
                DoubleArray::class.java to { f -> PrimDoubleArraySerializer(f, "double[p]") },
                LongArray::class.java to { f -> PrimLongArraySerializer(f, "long[p]") }
        )

        fun make(type: Type, factory: SerializerFactory) = primTypes[type]!!(factory)
    }

    fun localWriteObject(data: Data, func : () -> Unit) {
        data.withDescribed(typeNotation.descriptor) { withList { func() } }
    }
}

class PrimIntArraySerializer(factory: SerializerFactory, override val typeName : String) :
        PrimArraySerializer(IntArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        localWriteObject(data) { (obj as IntArray).forEach { output.writeObjectOrNull(it, data, elementType) }}
    }
}

class PrimCharArraySerializer(factory: SerializerFactory, override val typeName : String) :
        PrimArraySerializer(CharArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        localWriteObject(data) { (obj as CharArray).forEach { output.writeObjectOrNull(it, data, elementType) }}
    }

    override fun <T> List<T>.toArrayOfType(type: Type): Any {
        val elementType = type.asClass() ?: throw NotSerializableException("Unexpected array element type $type")
        val list = this
        return java.lang.reflect.Array.newInstance(elementType, this.size).apply {
            val array = this
            (0..lastIndex).forEach { java.lang.reflect.Array.set(array, it, (list[it] as Int).toChar()) }
        }
    }
}

class PrimBooleanArraySerializer(factory: SerializerFactory, override val typeName : String) :
        PrimArraySerializer(BooleanArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        localWriteObject(data) { (obj as BooleanArray).forEach { output.writeObjectOrNull(it, data, elementType) } }
    }
}

class PrimDoubleArraySerializer(factory: SerializerFactory, override val typeName : String) :
        PrimArraySerializer(DoubleArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        localWriteObject(data) { (obj as DoubleArray).forEach { output.writeObjectOrNull(it, data, elementType) } }
    }
}

class PrimFloatArraySerializer(factory: SerializerFactory, override val typeName: String) :
        PrimArraySerializer(FloatArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        localWriteObject(data) { (obj as FloatArray).forEach { output.writeObjectOrNull(it, data, elementType) } }
    }
}

class PrimShortArraySerializer(factory: SerializerFactory, override val typeName: String) :
        PrimArraySerializer(ShortArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        localWriteObject(data) { (obj as ShortArray).forEach { output.writeObjectOrNull(it, data, elementType) } }
    }
}

class PrimLongArraySerializer(factory: SerializerFactory, override val typeName: String) :
        PrimArraySerializer(LongArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        localWriteObject(data) { (obj as LongArray).forEach { output.writeObjectOrNull(it, data, elementType) } }
    }
}
