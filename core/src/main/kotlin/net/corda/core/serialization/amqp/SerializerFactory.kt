package net.corda.core.serialization.amqp

import com.google.common.primitives.Primitives
import com.google.common.util.concurrent.SettableFuture
import org.apache.qpid.proton.amqp.*
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class SerializerFactory {
    private val serializersByType = ConcurrentHashMap<Type, Serializer>()
    private val serializersByDescriptor = ConcurrentHashMap<Any, Serializer>()

    @Throws(NotSerializableException::class)
    fun get(actualType: Class<*>?, declaredType: Type): Serializer {
        if (declaredType is ParameterizedType) {
            return serializersByType[declaredType] ?: {
                // We allow only List and Map.
                // TODO: support Set?
                val rawType = declaredType.rawType
                if (rawType is Class<*>) {
                    checkParameterisedTypesConcrete(declaredType.actualTypeArguments)
                    if (rawType.isAssignableFrom(List::class.java)) {
                        makeListSerializer(declaredType)
                    } else if (rawType.isAssignableFrom(Map::class.java)) {
                        makeMapSerializer(declaredType)
                    } else {
                        throw NotSerializableException("Declared types of $declaredType are not supported.")
                    }
                } else {
                    throw NotSerializableException("Declared types of $declaredType are not supported.")
                }
            }()
        } else if (declaredType is Class<*>) {
            // Straight classes allowed
            return makeClassSerializer(actualType ?: declaredType)
        } else {
            throw NotSerializableException("Declared types of $declaredType are not supported.")
        }
    }

    @Throws(NotSerializableException::class)
    fun get(typeDescriptor: Any, envelope: Envelope): Serializer {
        return serializersByDescriptor[typeDescriptor] ?: {
            processSchema(envelope.schema)
            serializersByDescriptor[typeDescriptor] ?: throw NotSerializableException("Could not find type matching descriptor $typeDescriptor.")
        }()
    }

    private fun processSchema(schema: Schema) {
        for (typeNotation in schema.types) {
            processSchemaEntry(typeNotation)
        }
    }

    private fun processSchemaEntry(typeNotation: TypeNotation) {
        // TODO: use sealed types for TypeNotation?
        // TODO: for now we know the type is directly convertible, and we don't do any comparison etc etc
        if (typeNotation is CompositeType) {
            // java.lang.Class (whether a class or interface)
            processCompositeType(typeNotation)
        } else if (typeNotation is RestrictedType) {
            // List / Map, possibly with generics
            processRestrictedType(typeNotation)
        } else {
            throw NotSerializableException("Unexpected type $typeNotation")
        }
    }

    private fun processRestrictedType(typeNotation: RestrictedType) {
        val type = DeserializedParameterizedType(typeNotation.name)
        get(null, type)
    }

    private fun processCompositeType(typeNotation: CompositeType) {
        val clazz = Class.forName(typeNotation.name)
        get(clazz, clazz)
    }

    private fun checkParameterisedTypesConcrete(actualTypeArguments: Array<out Type>) {
        for (type in actualTypeArguments) {
            // Needs to be another parameterised type or a class
            if (type !is Class<*>) {
                if (type is ParameterizedType) {
                    checkParameterisedTypesConcrete(type.actualTypeArguments)
                } else {
                    throw NotSerializableException("Declared parameterised types containing $type as a parameter are not supported.")
                }
            }
        }
    }

    inner class UnderConstructionSerializer(override val type: Type) : Serializer() {
        private val constructing = AtomicBoolean(false)
        private val constructed: SettableFuture<Serializer> = SettableFuture.create()

        override val typeDescriptor: String
            get() = constructed.get().typeDescriptor

        override fun writeClassInfo(output: SerializationOutput) {
            constructed.get().writeClassInfo(output)
        }

        override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
            constructed.get().writeObject(obj, data, type, output)
        }

        override fun readObject(obj: Any, envelope: Envelope, input: DeserializationInput) {
            constructed.get().readObject(obj, envelope, input)
        }

        fun replaceWith(makeSerializer: () -> Serializer): Serializer {
            if (constructing.compareAndSet(false, true)) {
                val serializer = makeSerializer()
                serializersByType[type] = serializer
                if (type is ParameterizedType && type !is DeserializedParameterizedType) {
                    serializersByType[DeserializedParameterizedType(type.toString())] = serializer
                }
                serializersByDescriptor[serializer.typeDescriptor] = serializer
                // TODO: replace all field serializers that reference this?
                constructed.set(serializer)
                serializer
                return serializer
            } else {
                return this
            }
        }
    }

    private fun makeSerializer(type: Type, makeSerializer: () -> Serializer): Serializer {
        val existingSerializer = serializersByType.computeIfAbsent(type) { UnderConstructionSerializer(type) }
        if (existingSerializer is UnderConstructionSerializer) {
            return existingSerializer.replaceWith(makeSerializer)
        } else {
            return existingSerializer
        }
    }

    private fun makeClassSerializer(clazz: Class<*>): Serializer {
        return serializersByType[clazz] ?: {
            // TODO: check for array type
            if (isPrimitive(clazz)) {
                serializersByType.computeIfAbsent(clazz) { PrimitiveSerializer(clazz) }
            } else {
                makeSerializer(clazz) { ClassSerializer(clazz, this@SerializerFactory) }
            }
        }()
    }

    private fun makeListSerializer(declaredType: ParameterizedType): Serializer {
        return makeSerializer(declaredType) { ListSerializer(declaredType) }
    }

    private fun makeMapSerializer(declaredType: ParameterizedType): Serializer {
        val rawType = declaredType.rawType as Class<*>
        if (HashMap::class.java.isAssignableFrom(rawType) && !LinkedHashMap::class.java.isAssignableFrom(rawType)) {
            throw NotSerializableException("Map type $declaredType is unstable under iteration.")
        }
        return makeSerializer(declaredType) { MapSerializer(declaredType) }
    }

    companion object {
        fun isPrimitive(type: Type): Boolean = type is Class<*> && Primitives.wrap(type) in primitiveTypeNames

        fun primitiveTypeName(type: Type): String? = primitiveTypeNames[type as? Class<*>]

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

class PrimitiveSerializer(clazz: Class<*>) : Serializer() {
    override val typeDescriptor: String = SerializerFactory.primitiveTypeName(Primitives.wrap(clazz))!!
    override val type: Type = clazz

    // NOOP since this is a primitive type.
    override fun writeClassInfo(output: SerializationOutput) {
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        data.putObject(obj)
    }

    override fun readObject(obj: Any, envelope: Envelope, input: DeserializationInput): Any = obj
}

