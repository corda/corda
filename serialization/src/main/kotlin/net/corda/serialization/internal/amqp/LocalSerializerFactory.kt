package net.corda.serialization.internal.amqp

import net.corda.core.internal.kotlinObjectInstance
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.trace
import net.corda.serialization.internal.model.*
import org.apache.qpid.proton.amqp.Symbol
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.*

interface LocalSerializerFactory {
    // TODO: remove from interface
    val whitelist: ClassWhitelist

    // TODO: remove from interface
    val classloader: ClassLoader

    /**
     * Look up, and manufacture if necessary, a serializer for the given type.
     *
     * @param actualClass Will be null if there isn't an actual object instance available (e.g. for
     * restricted type processing).
     */
    @Throws(NotSerializableException::class)
    fun get(actualClass: Class<*>, declaredType: Type): AMQPSerializer<Any>

    fun get(declaredType: Type): AMQPSerializer<Any>
    fun get(typeInformation: LocalTypeInformation): AMQPSerializer<Any>

    fun createDescriptor(type: Type): Symbol
    fun getOrBuildTransform(name: String, builder: () -> EnumMap<TransformTypes, MutableList<Transform>>):
            EnumMap<TransformTypes, MutableList<Transform>>
}

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

    private val transformsCache: MutableMap<String, EnumMap<TransformTypes, MutableList<Transform>>> = DefaultCacheProvider.createCache()
    private val serializersByType: MutableMap<TypeIdentifier, AMQPSerializer<Any>> = DefaultCacheProvider.createCache()

    override fun createDescriptor(type: Type): Symbol =
            Symbol.valueOf("$DESCRIPTOR_DOMAIN:${fingerPrinter.fingerprint(typeModel.inspect(type))}")

    override fun getOrBuildTransform(name: String, builder: () -> EnumMap<TransformTypes, MutableList<Transform>>):
            EnumMap<TransformTypes, MutableList<Transform>> =
            transformsCache.computeIfAbsent(name) { _ -> builder() }

    override fun get(declaredType: Type): AMQPSerializer<Any> {
        val resolvedType = when(declaredType) {
            is WildcardType -> if (declaredType.upperBounds.size == 1) declaredType.upperBounds[0]
            else throw IllegalArgumentException("Cannot obtain upper bound for type $declaredType")
            else -> declaredType
        }
        return get(resolvedType, typeModel.inspect(resolvedType))
    }

    override fun get(typeInformation: LocalTypeInformation): AMQPSerializer<Any> =
            get(typeInformation.observedType, typeInformation)

    private fun make(typeInformation: LocalTypeInformation, build: () -> AMQPSerializer<Any>) =
            make(typeInformation.typeIdentifier, build)

    private fun make(typeIdentifier: TypeIdentifier, build: () -> AMQPSerializer<Any>) =
            serializersByType.computeIfAbsent(typeIdentifier) { _ ->
                build()
            }

    private fun get(declaredType: Type, localTypeInformation: LocalTypeInformation): AMQPSerializer<Any> {
        val declaredClass = declaredType.asClass()

        // can be useful to enable but will be *extremely* chatty if you do
        logger.trace { "Get Serializer for $declaredClass ${declaredType.typeName}" }

        return when(localTypeInformation) {
            is LocalTypeInformation.ACollection -> makeDeclaredCollection(localTypeInformation)
            is LocalTypeInformation.AMap -> makeDeclaredMap(localTypeInformation)
            is LocalTypeInformation.AnEnum -> makeDeclaredEnum(localTypeInformation, declaredType, declaredClass)
            else -> makeClassSerializer(declaredClass, declaredType, declaredType, localTypeInformation)
        }.also { serializer -> descriptorBasedSerializerRegistry[serializer.typeDescriptor.toString()] = serializer }
    }

    private fun makeDeclaredEnum(localTypeInformation: LocalTypeInformation, declaredType: Type, declaredClass: Class<*>): AMQPSerializer<Any> =
        make(localTypeInformation) {
            whitelist.requireWhitelisted(declaredType)
            EnumSerializer(declaredType, declaredClass, this)
        }

    private fun makeActualEnum(localTypeInformation: LocalTypeInformation, declaredType: Type, declaredClass: Class<*>): AMQPSerializer<Any> =
            make(localTypeInformation) {
                whitelist.requireWhitelisted(declaredType)
                EnumSerializer(declaredType, declaredClass, this)
            }

    private fun makeDeclaredCollection(localTypeInformation: LocalTypeInformation.ACollection): AMQPSerializer<Any> {
        val resolved = CollectionSerializer.resolveDeclared(localTypeInformation)
        return make(resolved) {
            CollectionSerializer(resolved.observedType as ParameterizedType, this)
        }
    }

    private fun makeDeclaredMap(localTypeInformation: LocalTypeInformation.AMap): AMQPSerializer<Any> {
        val resolved = MapSerializer.resolveDeclared(localTypeInformation)
        return make(resolved) {
            MapSerializer(resolved.observedType as ParameterizedType, this)
        }
    }

    override fun get(actualClass: Class<*>, declaredType: Type): AMQPSerializer<Any> {
        // can be useful to enable but will be *extremely* chatty if you do
        logger.trace { "Get Serializer for $actualClass ${declaredType.typeName}" }

        val declaredClass = declaredType.asClass()
        val actualType: Type = inferTypeVariables(actualClass, declaredClass, declaredType) ?: declaredType
        val declaredTypeInformation = typeModel.inspect(declaredType)
        val actualTypeInformation = typeModel.inspect(actualType)

        return when(actualTypeInformation) {
            is LocalTypeInformation.ACollection -> makeActualCollection(actualClass,declaredTypeInformation as? LocalTypeInformation.ACollection ?: actualTypeInformation)
            is LocalTypeInformation.AMap -> makeActualMap(declaredType, actualClass,declaredTypeInformation as? LocalTypeInformation.AMap ?: actualTypeInformation)
            is LocalTypeInformation.AnEnum -> makeActualEnum(actualTypeInformation, actualType, actualClass)
            else -> makeClassSerializer(actualClass, actualType, declaredType, actualTypeInformation)
        }.also { serializer -> descriptorBasedSerializerRegistry[serializer.typeDescriptor.toString()] = serializer }
    }

    private fun makeActualMap(declaredType: Type, actualClass: Class<*>, typeInformation: LocalTypeInformation.AMap): AMQPSerializer<Any> {
        declaredType.asClass().checkSupportedMapType()
        val resolved = MapSerializer.resolveActual(actualClass, typeInformation)
        return make(resolved) {
            MapSerializer(resolved.observedType as ParameterizedType, this)
        }
    }

    private fun makeActualCollection(actualClass: Class<*>, typeInformation: LocalTypeInformation.ACollection): AMQPSerializer<Any> {
        val resolved = CollectionSerializer.resolveActual(actualClass, typeInformation)

        return serializersByType.computeIfAbsent(resolved.typeIdentifier) {
            CollectionSerializer(resolved.observedType as ParameterizedType, this)
        }
    }

    private fun makeClassSerializer(
            clazz: Class<*>,
            type: Type,
            declaredType: Type,
            typeInformation: LocalTypeInformation
    ): AMQPSerializer<Any> = make(typeInformation) {
        logger.debug { "class=${clazz.simpleName}, type=$type is a composite type" }
        when {
            clazz.isSynthetic -> // Explicitly ban synthetic classes, we have no way of recreating them when deserializing. This also
                // captures Lambda expressions and other anonymous functions
                throw AMQPNotSerializableException(
                        type,
                        "Serializer does not support synthetic classes")
            SerializerFactory.isPrimitive(clazz) -> AMQPPrimitiveSerializer(clazz)
            else -> customSerializerRegistry.findCustomSerializer(clazz, declaredType) ?:
                makeNonCustomSerializer(type, clazz)
        }
    }

    private fun makeNonCustomSerializer(type: Type, clazz: Class<*>): AMQPSerializer<Any> = when {
        onlyCustomSerializers -> throw AMQPNotSerializableException(type, "Only allowing custom serializers")
        type.isArray() ->
            if (clazz.componentType.isPrimitive) PrimArraySerializer.make(type, this)
            else ArraySerializer.make(type, this)
        else -> {
            val singleton = clazz.kotlinObjectInstance
            if (singleton != null) {
                whitelist.requireWhitelisted(clazz)
                SingletonSerializer(clazz, singleton, this)
            } else {
                if (type.typeName.startsWith("?")) {
                    assert(false)
                }
                whitelist.requireWhitelisted(type)
                ObjectSerializer(type, this)
            }
        }
    }

}