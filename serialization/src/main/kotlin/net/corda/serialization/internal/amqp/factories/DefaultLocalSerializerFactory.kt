package net.corda.serialization.internal.amqp.factories

import net.corda.core.internal.kotlinObjectInstance
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.trace
import net.corda.serialization.internal.model.*
import net.corda.core.internal.reflection.DefaultCacheProvider
import net.corda.core.internal.reflection.LocalTypeInformation
import net.corda.core.internal.reflection.LocalTypeModel
import net.corda.core.internal.reflection.TypeIdentifier
import net.corda.serialization.internal.amqp.api.*
import net.corda.serialization.internal.amqp.schema.AMQPTypeIdentifiers
import net.corda.serialization.internal.amqp.schema.DESCRIPTOR_DOMAIN
import net.corda.serialization.internal.amqp.serializers.*
import net.corda.serialization.internal.amqp.utils.asClass
import net.corda.serialization.internal.amqp.utils.inferTypeVariables
import net.corda.serialization.internal.amqp.utils.isArray
import net.corda.serialization.internal.amqp.utils.requireWhitelisted
import org.apache.qpid.proton.amqp.Symbol
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import javax.annotation.concurrent.ThreadSafe

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