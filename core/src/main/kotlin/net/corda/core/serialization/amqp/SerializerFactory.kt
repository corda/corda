package net.corda.core.serialization.amqp

import com.google.common.util.concurrent.SettableFuture
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class SerializerFactory {
    private val serializersByType = ConcurrentHashMap<Type, Serializer>()
    private val serializersByDescriptor = ConcurrentHashMap<Any, Serializer>()

    @Throws(NotSerializableException::class)
    fun get(actualType: Class<*>?, declaredType: Type): Serializer {
        if (declaredType is ParameterizedType) {
            // We allow only List and Map.
            // TODO: support Set?
            val rawType = declaredType.rawType
            if (rawType is Class<*>) {
                checkParameterisedTypesConcrete(declaredType.actualTypeArguments)
                if (rawType.isAssignableFrom(List::class.java)) {
                    return makeListSerializer(declaredType)
                } else if (rawType.isAssignableFrom(Map::class.java)) {
                    return makeMapSerializer(declaredType)
                } else {
                    throw NotSerializableException("Declared types of $declaredType are not supported.")
                }
            } else {
                throw NotSerializableException("Declared types of $declaredType are not supported.")
            }
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
        // TODO: check for array type
        return makeSerializer(clazz) { ClassSerializer(clazz, this@SerializerFactory) }
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
}

