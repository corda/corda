package net.corda.serialization.internal.amqp

import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.contextLogger
import net.corda.serialization.internal.model.DefaultCacheProvider
import net.corda.serialization.internal.model.TypeIdentifier
import java.lang.reflect.Type

interface CustomSerializerRegistry {
    /**
     * Register a custom serializer for any type that cannot be serialized or deserialized by the default serializer
     * that expects to find getters and a constructor with a parameter for each property.
     */
    fun register(customSerializer: CustomSerializer<out Any>)
    fun registerExternal(customSerializer: CorDappCustomSerializer)

    fun findCustomSerializer(clazz: Class<*>, declaredType: Type): AMQPSerializer<Any>?
}

class CachingCustomSerializerRegistry(
        private val descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry)
    : CustomSerializerRegistry {

    companion object {
        val logger = contextLogger()
    }

    private data class CustomSerializerIdentifier(val actualTypeIdentifier: TypeIdentifier, val declaredTypeIdentifier: TypeIdentifier)

    private sealed class CustomSerializerLookupResult {

        abstract val serializerIfFound: AMQPSerializer<Any>?

        object None : CustomSerializerLookupResult() {
            override val serializerIfFound: AMQPSerializer<Any>? = null
        }

        data class CustomSerializerFound(override val serializerIfFound: AMQPSerializer<Any>) : CustomSerializerLookupResult()
    }

    private val customSerializersCache: MutableMap<CustomSerializerIdentifier, CustomSerializerLookupResult> = DefaultCacheProvider.createCache()
    private var customSerializers: List<SerializerFor> = emptyList()

    /**
     * Register a custom serializer for any type that cannot be serialized or deserialized by the default serializer
     * that expects to find getters and a constructor with a parameter for each property.
     */
    override fun register(customSerializer: CustomSerializer<out Any>) {
        logger.trace("action=\"Registering custom serializer\", class=\"${customSerializer.type}\"")

        if (!customSerializersCache.isEmpty()) {
            logger.warn("Attempting to register custom serializer $customSerializer.type} in an active cache." +
                    "All serializers should be registered before the cache comes into use.")
        }

        descriptorBasedSerializerRegistry.getOrBuild(customSerializer.typeDescriptor.toString()) {
            customSerializers += customSerializer
            for (additional in customSerializer.additionalSerializers) {
                register(additional)
            }
            customSerializer
        }
    }

    override fun registerExternal(customSerializer: CorDappCustomSerializer) {
        logger.trace("action=\"Registering external serializer\", class=\"${customSerializer.type}\"")

        if (!customSerializersCache.isEmpty()) {
            logger.warn("Attempting to register custom serializer ${customSerializer.type} in an active cache." +
                    "All serializers must be registered before the cache comes into use.")
        }

        descriptorBasedSerializerRegistry.getOrBuild(customSerializer.typeDescriptor.toString()) {
            customSerializers += customSerializer
            customSerializer
        }
    }
    
    override fun findCustomSerializer(clazz: Class<*>, declaredType: Type): AMQPSerializer<Any>? {
        val typeIdentifier = CustomSerializerIdentifier(
                TypeIdentifier.forClass(clazz),
                TypeIdentifier.forGenericType(declaredType))

        return customSerializersCache.getOrPut(typeIdentifier) {
                val customSerializer = doFindCustomSerializer(clazz, declaredType)
                if (customSerializer == null) CustomSerializerLookupResult.None
                else CustomSerializerLookupResult.CustomSerializerFound(customSerializer)
        }.serializerIfFound
    }

    private fun doFindCustomSerializer(clazz: Class<*>, declaredType: Type): AMQPSerializer<Any>? {
        // e.g. Imagine if we provided a Map serializer this way, then it won't work if the declared type is
        // AbstractMap, only Map. Otherwise it needs to inject additional schema for a RestrictedType source of the
        // super type.  Could be done, but do we need it?
        for (customSerializer in customSerializers) {
            if (customSerializer.isSerializerFor(clazz)) {
                val declaredSuperClass = declaredType.asClass().superclass

                return if (declaredSuperClass == null
                        || !customSerializer.isSerializerFor(declaredSuperClass)
                        || !customSerializer.revealSubclassesInSchema
                ) {
                    logger.debug("action=\"Using custom serializer\", class=${clazz.typeName}, " +
                            "declaredType=${declaredType.typeName}")

                    @Suppress("UNCHECKED_CAST")
                    customSerializer as? AMQPSerializer<Any>
                } else {
                    // Make a subclass serializer for the subclass and return that...
                    CustomSerializer.SubClass(clazz, uncheckedCast(customSerializer))
                }
            }
        }
        return null
    }
}