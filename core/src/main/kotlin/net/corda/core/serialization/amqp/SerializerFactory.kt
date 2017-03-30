package net.corda.core.serialization.amqp

import com.google.common.util.concurrent.SettableFuture
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class SerializerFactory {
    private val serializers = ConcurrentHashMap<Type, Serializer>()

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

    class UnderConstructionSerializer(val type: Type) : Serializer() {
        private val constructing = AtomicBoolean(false)
        private val constructed: SettableFuture<Serializer> = SettableFuture.create()

        override fun writeClassInfo(output: SerializationOutput) {
            constructed.get().writeClassInfo(output)
        }

        override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
            constructed.get().writeObject(obj, data, type, output)
        }

        fun replaceWith(serializers: MutableMap<Type, Serializer>, makeSerializer: () -> Serializer): Serializer {
            if (constructing.compareAndSet(false, true)) {
                val serializer = makeSerializer()
                serializers[type] = serializer
                // TODO: replace all field serializers that reference this?
                constructed.set(serializer)
                return serializer
            } else {
                return this
            }
        }
    }

    private fun makeSerializer(type: Type, makeSerializer: () -> Serializer): Serializer {
        val existingSerializer = serializers.computeIfAbsent(type) { UnderConstructionSerializer(type) }
        if (existingSerializer is UnderConstructionSerializer) {
            return existingSerializer.replaceWith(serializers, makeSerializer)
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

