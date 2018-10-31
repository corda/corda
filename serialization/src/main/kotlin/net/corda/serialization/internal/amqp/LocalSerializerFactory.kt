package net.corda.serialization.internal.amqp

import net.corda.core.internal.kotlinObjectInstance
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.trace
import net.corda.serialization.internal.model.DefaultCacheProvider
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*

interface LocalSerializerFactory {
    val whitelist: ClassWhitelist
    val fingerPrinter: FingerPrinter
    val classloader: ClassLoader

    val descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry<AMQPSerializer<Any>>
    val transformsCache: MutableMap<String, EnumMap<TransformTypes, MutableList<Transform>>>

    /**
     * Look up, and manufacture if necessary, a serializer for the given type.
     *
     * @param actualClass Will be null if there isn't an actual object instance available (e.g. for
     * restricted type processing).
     */
    @Throws(NotSerializableException::class)
    fun get(actualClass: Class<*>?, declaredType: Type): AMQPSerializer<Any>
}

class DefaultLocalSerializerFactory(
        override val whitelist: ClassWhitelist,
        override val fingerPrinter: FingerPrinter,
        override val classloader: ClassLoader,
        override val descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry<AMQPSerializer<Any>>,
        private val customSerializerRegistry: CustomSerializerRegistry,
        private val onlyCustomSerializers: Boolean)
    : LocalSerializerFactory {

    companion object {
        val logger = contextLogger()
    }

    private val serializersByType: MutableMap<Type, AMQPSerializer<Any>> = DefaultCacheProvider.createCache()
    override val transformsCache: MutableMap<String, EnumMap<TransformTypes, MutableList<Transform>>> = DefaultCacheProvider.createCache()

    override fun get(actualClass: Class<*>?, declaredType: Type): AMQPSerializer<Any> {
        // can be useful to enable but will be *extremely* chatty if you do
        logger.trace { "Get Serializer for $actualClass ${declaredType.typeName}" }

        val declaredClass = declaredType.asClass()
        val actualType: Type = if (actualClass == null) declaredType
        else inferTypeVariables(actualClass, declaredClass, declaredType) ?: declaredType

        val serializer = when {
            // Declared class may not be set to Collection, but actual class could be a collection.
            // In this case use of CollectionSerializer is perfectly appropriate.
            (Collection::class.java.isAssignableFrom(declaredClass) ||
                    (actualClass != null && Collection::class.java.isAssignableFrom(actualClass))) &&
                    !EnumSet::class.java.isAssignableFrom(actualClass ?: declaredClass) -> {
                val declaredTypeAmended = CollectionSerializer.deriveParameterizedType(declaredType, declaredClass, actualClass)
                serializersByType.computeIfAbsent(declaredTypeAmended) {
                    CollectionSerializer(declaredTypeAmended, this)
                }
            }
            // Declared class may not be set to Map, but actual class could be a map.
            // In this case use of MapSerializer is perfectly appropriate.
            (Map::class.java.isAssignableFrom(declaredClass) ||
                    (actualClass != null && Map::class.java.isAssignableFrom(actualClass))) -> {
                val declaredTypeAmended = MapSerializer.deriveParameterizedType(declaredType, declaredClass, actualClass)
                serializersByType.computeIfAbsent(declaredTypeAmended) {
                    makeMapSerializer(declaredTypeAmended)
                }
            }
            Enum::class.java.isAssignableFrom(actualClass ?: declaredClass) -> {
                logger.trace {
                    "class=[${actualClass?.simpleName} | $declaredClass] is an enumeration " +
                            "declaredType=${declaredType.typeName} " +
                            "isEnum=${declaredType::class.java.isEnum}"
                }

                serializersByType.computeIfAbsent(actualClass ?: declaredClass) {
                    whitelist.requireWhitelisted(actualType)
                    EnumSerializer(actualType, actualClass ?: declaredClass, this)
                }
            }
            else -> {
                makeClassSerializer(actualClass ?: declaredClass, actualType, declaredType)
            }
        }

        descriptorBasedSerializerRegistry[serializer.typeDescriptor.toString()] = serializer

        return serializer
    }

    private fun makeClassSerializer(
            clazz: Class<*>,
            type: Type,
            declaredType: Type
    ): AMQPSerializer<Any> = serializersByType.computeIfAbsent(type) {
        logger.debug { "class=${clazz.simpleName}, type=$type is a composite type" }
        if (clazz.isSynthetic) {
            // Explicitly ban synthetic classes, we have no way of recreating them when deserializing. This also
            // captures Lambda expressions and other anonymous functions
            throw AMQPNotSerializableException(
                    type,
                    "Serializer does not support synthetic classes")
        } else if (SerializerFactory.isPrimitive(clazz)) {
            AMQPPrimitiveSerializer(clazz)
        } else {
            customSerializerRegistry.findCustomSerializer(clazz, declaredType) ?: run {
                if (onlyCustomSerializers) {
                    throw AMQPNotSerializableException(type, "Only allowing custom serializers")
                }
                if (type.isArray()) {
                    // Don't need to check the whitelist since each element will come back through the whitelisting process.
                    if (clazz.componentType.isPrimitive) PrimArraySerializer.make(type, this)
                    else ArraySerializer.make(type, this)
                } else {
                    val singleton = clazz.kotlinObjectInstance
                    if (singleton != null) {
                        whitelist.requireWhitelisted(clazz)
                        SingletonSerializer(clazz, singleton, this)
                    } else {
                        whitelist.requireWhitelisted(type)
                        ObjectSerializer(type, this)
                    }
                }
            }
        }
    }

    private fun makeMapSerializer(declaredType: ParameterizedType): AMQPSerializer<Any> {
        val rawType = declaredType.rawType as Class<*>
        rawType.checkSupportedMapType()
        return MapSerializer(declaredType, this)
    }

}