package net.corda.serialization.internal.amqp

import net.corda.core.internal.kotlinObjectInstance
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.trace
import net.corda.serialization.internal.model.*
import org.apache.qpid.proton.amqp.Symbol
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * A factory that handles the serialisation and deserialisation of [Type]s visible from a given [ClassLoader].
 *
 * Unlike the [RemoteSerializerFactory], which deals with types for which we have [Schema] information and serialised data,
 * the [LocalSerializerFactory] deals with types for which we have a Java [Type] (and perhaps some in-memory data, from which
 * we can discover the actual [Class] we are working with.
 */
interface LocalSerializerFactory {
    /**
     * The [ClassWhitelist] used by this factory. Classes must be whitelisted for serialization, because they are expected
     * to be written in a secure manner.
     */
    val whitelist: ClassWhitelist

    /**
     * The [ClassLoader] used by this factory.
     */
    val classloader: ClassLoader

    /**
     * Retrieves the names of the registered custom serializers.
     */
    val customSerializerNames: List<String>

    /**
     * Obtain an [AMQPSerializer] for an object of actual type [actualClass], and declared type [declaredType].
     */
    fun get(actualClass: Class<*>, declaredType: Type): AMQPSerializer<Any>

    /**
     * Obtain an [AMQPSerializer] for the [declaredType].
     */
    @JvmDefault
    fun get(declaredType: Type): AMQPSerializer<Any> = get(getTypeInformation(declaredType))

    /**
     * Obtain an [AMQPSerializer] for the type having the given [typeInformation].
     */
    fun get(typeInformation: LocalTypeInformation): AMQPSerializer<Any>

    /**
     * Obtain [LocalTypeInformation] for the given [Type].
     */
    fun getTypeInformation(type: Type): LocalTypeInformation

    /**
     * Obtain [LocalTypeInformation] for the [Type] that has the given name in the [ClassLoader] associated with this factory.
     *
     * @return null if the type with the given name does not exist in the [ClassLoader] for this factory.
     */
    fun getTypeInformation(typeName: String): LocalTypeInformation?

    /**
     * Use the [FingerPrinter] to create a type descriptor for the given [type].
     */
    @JvmDefault
    fun createDescriptor(type: Type): Symbol = createDescriptor(getTypeInformation(type))

    /**
     * Use the [FingerPrinter] to create a type descriptor for the given [typeInformation].
     */
    fun createDescriptor(typeInformation: LocalTypeInformation): Symbol
}

/**
 * A [LocalSerializerFactory] equipped with a [LocalTypeModel] and a [FingerPrinter] to help it build fingerprint-based descriptors
 * and serializers for local types.
 */
@ThreadSafe
class DefaultLocalSerializerFactory(
        override val whitelist: ClassWhitelist,
        private val typeModel: LocalTypeModel,
        private val fingerPrinter: FingerPrinter,
        override val classloader: ClassLoader,
        private val descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry,
        private val customSerializerRegistry: CustomSerializerRegistry,
        private val onlyCustomSerializers: Boolean)
    : LocalSerializerFactory {

    companion object {
        val logger = contextLogger()
    }

    override val customSerializerNames: List<String>
        get() = customSerializerRegistry.customSerializerNames

    private data class ActualAndDeclaredType(val actualType: Class<*>, val declaredType: Type)

    private val serializersByActualAndDeclaredType: MutableMap<ActualAndDeclaredType, AMQPSerializer<Any>> = DefaultCacheProvider.createCache()
    private val serializersByTypeId: MutableMap<TypeIdentifier, AMQPSerializer<Any>> = DefaultCacheProvider.createCache()
    private val typesByName = DefaultCacheProvider.createCache<String, Optional<LocalTypeInformation>>()

    override fun createDescriptor(typeInformation: LocalTypeInformation): Symbol =
            Symbol.valueOf("$DESCRIPTOR_DOMAIN:${fingerPrinter.fingerprint(typeInformation)}")

    override fun getTypeInformation(type: Type): LocalTypeInformation = typeModel.inspect(type)

    override fun getTypeInformation(typeName: String): LocalTypeInformation? {
        return typesByName.getOrPut(typeName) {
            val localType = try {
                Class.forName(typeName, false, classloader)
            } catch (_: ClassNotFoundException) {
                null
            }
            Optional.ofNullable(localType?.run { getTypeInformation(this) })
        }.orElse(null)
    }

    override fun get(typeInformation: LocalTypeInformation): AMQPSerializer<Any> =
            get(typeInformation.observedType, typeInformation)

    private fun makeAndCache(typeInformation: LocalTypeInformation, build: () -> AMQPSerializer<Any>) =
            makeAndCache(typeInformation.typeIdentifier, build)

    private fun makeAndCache(typeIdentifier: TypeIdentifier, build: () -> AMQPSerializer<Any>) =
        serializersByTypeId.getOrPut(typeIdentifier) {
            build().also { serializer ->
                descriptorBasedSerializerRegistry[serializer.typeDescriptor.toString()] = serializer
            }
        }

    private fun get(declaredType: Type, localTypeInformation: LocalTypeInformation): AMQPSerializer<Any> =
        serializersByTypeId.getOrPut(localTypeInformation.typeIdentifier) {
            val declaredClass = declaredType.asClass()

            // can be useful to enable but will be *extremely* chatty if you do
            logger.trace { "Get Serializer for $declaredClass ${declaredType.typeName}" }
            customSerializerRegistry.findCustomSerializer(declaredClass, declaredType)?.apply { return@get this }

            return when (localTypeInformation) {
                is LocalTypeInformation.ACollection -> makeDeclaredCollection(localTypeInformation)
                is LocalTypeInformation.AMap -> makeDeclaredMap(localTypeInformation)
                is LocalTypeInformation.AnEnum -> makeDeclaredEnum(localTypeInformation, declaredType, declaredClass)
                else -> makeClassSerializer(declaredClass, declaredType, localTypeInformation)
            }
        }

    private fun makeDeclaredEnum(localTypeInformation: LocalTypeInformation, declaredType: Type, declaredClass: Class<*>): AMQPSerializer<Any> =
            makeAndCache(localTypeInformation) {
                whitelist.requireWhitelisted(declaredType)
                EnumSerializer(declaredType, declaredClass, this)
            }

    private fun makeActualEnum(localTypeInformation: LocalTypeInformation, declaredType: Type, declaredClass: Class<*>): AMQPSerializer<Any> =
            makeAndCache(localTypeInformation) {
                whitelist.requireWhitelisted(declaredType)
                EnumSerializer(declaredType, declaredClass, this)
            }

    private fun makeDeclaredCollection(localTypeInformation: LocalTypeInformation.ACollection): AMQPSerializer<Any> {
        val resolved = CollectionSerializer.resolveDeclared(localTypeInformation)
        return makeAndCache(resolved) {
            CollectionSerializer(resolved.typeIdentifier.getLocalType(classloader) as ParameterizedType, this)
        }
    }

    private fun makeDeclaredMap(localTypeInformation: LocalTypeInformation.AMap): AMQPSerializer<Any> {
        val resolved = MapSerializer.resolveDeclared(localTypeInformation)
        return makeAndCache(resolved) {
            MapSerializer(resolved.typeIdentifier.getLocalType(classloader) as ParameterizedType, this)
        }
    }

    override fun get(actualClass: Class<*>, declaredType: Type): AMQPSerializer<Any> {
        val actualAndDeclaredType = ActualAndDeclaredType(actualClass, declaredType)
        return serializersByActualAndDeclaredType.getOrPut(actualAndDeclaredType) {
            // can be useful to enable but will be *extremely* chatty if you do
            logger.trace { "Get Serializer for $actualClass ${declaredType.typeName}" }
            customSerializerRegistry.findCustomSerializer(actualClass, declaredType)?.apply { return@get this }

            val declaredClass = declaredType.asClass()
            val actualType: Type = inferTypeVariables(actualClass, declaredClass, declaredType) ?: declaredType
            val declaredTypeInformation = typeModel.inspect(declaredType)
            val actualTypeInformation = typeModel.inspect(actualType)

            return when (actualTypeInformation) {
                is LocalTypeInformation.ACollection -> makeActualCollection(actualClass, declaredTypeInformation as? LocalTypeInformation.ACollection
                        ?: actualTypeInformation)
                is LocalTypeInformation.AMap -> makeActualMap(declaredType, actualClass, declaredTypeInformation as? LocalTypeInformation.AMap
                        ?: actualTypeInformation)
                is LocalTypeInformation.AnEnum -> makeActualEnum(actualTypeInformation, actualType, actualClass)
                else -> makeClassSerializer(actualClass, actualType, actualTypeInformation)
            }
        }
    }

    private fun makeActualMap(declaredType: Type, actualClass: Class<*>, typeInformation: LocalTypeInformation.AMap): AMQPSerializer<Any> {
        declaredType.asClass().checkSupportedMapType()
        val resolved = MapSerializer.resolveActual(actualClass, typeInformation)
        return makeAndCache(resolved) {
            MapSerializer(resolved.typeIdentifier.getLocalType(classloader) as ParameterizedType, this)
        }
    }

    private fun makeActualCollection(actualClass: Class<*>, typeInformation: LocalTypeInformation.ACollection): AMQPSerializer<Any> {
        val resolved = CollectionSerializer.resolveActual(actualClass, typeInformation)

        return makeAndCache(resolved) {
            CollectionSerializer(resolved.typeIdentifier.getLocalType(classloader) as ParameterizedType, this)
        }
    }

    private fun makeClassSerializer(
            clazz: Class<*>,
            type: Type,
            typeInformation: LocalTypeInformation
    ): AMQPSerializer<Any> = makeAndCache(typeInformation) {
        logger.debug { "class=${clazz.simpleName}, type=$type is a composite type" }
        when {
            clazz.isSynthetic -> // Explicitly ban synthetic classes, we have no way of recreating them when deserializing. This also
                // captures Lambda expressions and other anonymous functions
                throw AMQPNotSerializableException(
                        type,
                        "Serializer does not support synthetic classes")
            AMQPTypeIdentifiers.isPrimitive(typeInformation.typeIdentifier) -> AMQPPrimitiveSerializer(clazz)
            else -> makeNonCustomSerializer(type, typeInformation, clazz)
        }
    }

    private fun makeNonCustomSerializer(type: Type, typeInformation: LocalTypeInformation, clazz: Class<*>): AMQPSerializer<Any> = when {
        onlyCustomSerializers -> throw AMQPNotSerializableException(type, "Only allowing custom serializers")
        type.isArray() ->
            if (clazz.componentType.isPrimitive) PrimArraySerializer.make(type, this)
            else {
                ArraySerializer.make(type, this)
            }
        else -> {
            val singleton = clazz.kotlinObjectInstance
            if (singleton != null) {
                whitelist.requireWhitelisted(clazz)
                SingletonSerializer(clazz, singleton, this)
            } else {
                whitelist.requireWhitelisted(type)
                ObjectSerializer.make(typeInformation, this)
            }
        }
    }

}