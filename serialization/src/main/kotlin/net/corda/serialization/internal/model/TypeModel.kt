package net.corda.serialization.internal.model

import net.corda.serialization.internal.amqp.asClass
import net.corda.serialization.internal.amqp.componentType
import net.corda.serialization.internal.amqp.propertyDescriptors
import net.corda.serialization.internal.amqp.resolveTypeVariables
import java.lang.reflect.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

interface CacheProvider {
    fun <K, V> createCache(): MutableMap<K, V>
}

object DefaultCacheProvider: CacheProvider {
    override fun <K, V> createCache(): MutableMap<K, V> = ConcurrentHashMap()
}

typealias PropertyName = String

/**
 * Used as a key for retrieving cached type information.
 *
 * The basic idea is this: at runtime, the type of any value presented to us will be erased. However, the types of its
 * properties, and supertype type parameters may carry generic type information. For example, one class may have as a
 * property a [Map<String, String>], while another has a property of type [Map<String, Int>]. Both [Type]s
 * are available through reflection. We want to be able to cache them both separately, and need suitable
 * type identifiers to do so.
 */
sealed class TypeIdentifier {
    companion object {
        fun forClass(type: Class<*>): TypeIdentifier = when {
            type == Any::class.java -> Any
            type.isArray -> ArrayOf(forClass(type.componentType))
            type.typeParameters.isEmpty() -> Unparameterised(type.name)
            else -> Erased(type.name)
        }

        fun forGenericType(type: Type): TypeIdentifier = when(type) {
            is ParameterizedType -> Parameterised((type.rawType as Class<*>).name, type.actualTypeArguments.map {
                forGenericType(resolveTypeVariables(it, type)) }
            )
            is Class<*> -> forClass(type)
            is GenericArrayType -> ArrayOf(forGenericType(type.genericComponentType))
            else -> Unknown
        }
    }

    object Any: TypeIdentifier()
    object Unknown: TypeIdentifier()
    data class Unparameterised(val className: String): TypeIdentifier()
    /**
     * A class such as List<Int>, for which we cannot obtain the type parameters at runtime because they have been erased.
     */
    data class Erased(val className: String): TypeIdentifier()
    data class ArrayOf(val componentType: TypeIdentifier): TypeIdentifier()
    data class Parameterised(val className: String, val parameters: List<TypeIdentifier>): TypeIdentifier()
}

interface LocalTypeLookup {
    fun lookup(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation): LocalTypeInformation
}

object AlwaysBuildLookup : LocalTypeLookup {
    override fun lookup(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation): LocalTypeInformation =
            builder()
}

sealed class LocalTypeInformation {

    companion object {
        fun forType(type: Type, lookup: LocalTypeLookup = AlwaysBuildLookup): LocalTypeInformation =
            LocalTypeInformationBuilder(lookup).build(type, TypeIdentifier.forGenericType(type))
    }

    abstract val observedType: Type
    abstract val typeIdentifier: TypeIdentifier

    object Unknown: LocalTypeInformation() {
        override val observedType get() = throw UnsupportedOperationException("Type is unknown")
        override val typeIdentifier = TypeIdentifier.Unknown
    }

    object Any: LocalTypeInformation() {
        override val observedType = Any::class.java
        override val typeIdentifier = TypeIdentifier.Any
    }

    data class APrimitive(override val observedType: Class<*>, override val typeIdentifier: TypeIdentifier): LocalTypeInformation()

    data class AnArray(override val observedType: Type, override val typeIdentifier: TypeIdentifier, val componentType: LocalTypeInformation): LocalTypeInformation()

    data class AnEnum(override val observedType: Class<*>, override val typeIdentifier: TypeIdentifier, val interfaces: List<LocalTypeInformation>, val members: List<String>)
        : LocalTypeInformation()

    data class AnInterface(override val observedType: Type, override val typeIdentifier: TypeIdentifier, val typeParameters: List<LocalTypeInformation>): LocalTypeInformation()

    data class AnObject(
            override val observedType: Type,
            override val typeIdentifier: TypeIdentifier,
            val properties: Map<PropertyName, LocalTypeInformation>,
            val interfaces: List<LocalTypeInformation>,
            val typeParameters: List<LocalTypeInformation>): LocalTypeInformation()

    data class ACollection(override val observedType: Class<*>, override val typeIdentifier: TypeIdentifier, val typeParameters: List<LocalTypeInformation>): LocalTypeInformation()
}

class CircularTypeDefinitionException(val type: Type): Exception("Circular dependency detected in definition of $type")

private data class LocalTypeInformationBuilder(val lookup: LocalTypeLookup, val visited: Set<TypeIdentifier> = emptySet()) {

    fun build(type: Type, typeIdentifier: TypeIdentifier): LocalTypeInformation {
        if (typeIdentifier in visited) throw CircularTypeDefinitionException(type)
        return lookup.lookup(type, typeIdentifier)  {
            copy(visited = visited + typeIdentifier).buildIfNotFound(type, typeIdentifier)
        }
    }

    private fun buildIfNotFound(type: Type, typeIdentifier: TypeIdentifier): LocalTypeInformation {
        val rawType = type.asClass()
        return when(typeIdentifier) {
            is TypeIdentifier.Any -> LocalTypeInformation.Any
            is TypeIdentifier.Unknown -> LocalTypeInformation.Unknown
            is TypeIdentifier.Unparameterised,
            is TypeIdentifier.Erased -> buildForClass(rawType, typeIdentifier)
            is TypeIdentifier.ArrayOf -> LocalTypeInformation.AnArray(
                    type,
                    typeIdentifier,
                    build(type.componentType(), typeIdentifier.componentType))
            is TypeIdentifier.Parameterised -> buildForParameterised(rawType, type as ParameterizedType, typeIdentifier)
        }
    }

    private fun buildForClass(type: Class<*>, typeIdentifier: TypeIdentifier): LocalTypeInformation = when {
        type.isInterface -> LocalTypeInformation.AnInterface(type, typeIdentifier, emptyList())
        type.isPrimitive || type.typeName.startsWith("java") -> LocalTypeInformation.APrimitive(type, typeIdentifier)
        type.isEnum ->
            LocalTypeInformation.AnEnum(
                    type,
                    typeIdentifier,
                    buildInterfaceInformation(type),
                    type.enumConstants.map { it.toString() })
        else -> LocalTypeInformation.AnObject(
                type,
                typeIdentifier,
                getObjectProperties(type, type),
                buildInterfaceInformation(type),
                emptyList()
        )
    }

    private fun buildForParameterised(rawType: Class<*>, type: ParameterizedType, typeIdentifier: TypeIdentifier.Parameterised): LocalTypeInformation =
            when {
                rawType.isCollectionOrMap -> LocalTypeInformation.ACollection(rawType, typeIdentifier, getTypeParameterInformation(type))
                rawType.isInterface -> LocalTypeInformation.AnInterface(rawType, typeIdentifier, getTypeParameterInformation(type))
                else -> LocalTypeInformation.AnObject(
                        rawType,
                        typeIdentifier,
                        getObjectProperties(rawType, type),
                        buildInterfaceInformation(rawType),
                        getTypeParameterInformation(type))
            }

    private fun buildInterfaceInformation(type: Type) =
            type.allInterfaces.map { build(it, TypeIdentifier.forGenericType(it)) }.toList()

    private val Type.allInterfaces: Sequence<Type> get() =
        generateSequence(this) { it.asClass().genericSuperclass }
                .flatMap { it -> it.asClass().genericInterfaces.asSequence().flatMap { it.allInterfaces }  + it }
                .filter { it.asClass().isInterface }
                .distinct()

    private fun getObjectProperties(rawType: Class<*>, contextType: Type): Map<PropertyName, LocalTypeInformation> =
            rawType.propertyDescriptors().mapValues { (_, v) ->
                val paramType = resolveTypeVariables(v.field?.genericType ?: v.getter!!.genericReturnType, contextType).upperBound
                build(paramType, TypeIdentifier.forGenericType(paramType))
            }

    private fun getTypeParameterInformation(type: ParameterizedType): List<LocalTypeInformation> =
            type.actualTypeArguments.map {
                val upperBound = it.upperBound
                build(upperBound, TypeIdentifier.forGenericType(upperBound))
            }

    private val Class<*>.isCollectionOrMap get() =
        (Collection::class.java.isAssignableFrom(this) || Map::class.java.isAssignableFrom(this))
                && !EnumSet::class.java.isAssignableFrom(this)

    private val Type.upperBound: Type get() = when(this) {
        is TypeVariable<*> -> when {
            this.bounds.isEmpty() -> this
            this.bounds.size > 1 -> this
            else -> this.bounds[0]
        }
        is WildcardType -> when {
            this.upperBounds.isEmpty() -> this
            this.upperBounds.size > 1 -> this
            else -> this.upperBounds[0]
        }
        else -> this
    }
}

class LocalTypeModel: LocalTypeLookup {

    private val cache = DefaultCacheProvider.createCache<TypeIdentifier, LocalTypeInformation>()

    fun interpret(type: Type): LocalTypeInformation = LocalTypeInformation.forType(type, this)

    override fun lookup(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation): LocalTypeInformation =
            cache[typeIdentifier] ?: builder().apply { cache.putIfAbsent(typeIdentifier, this) }

    fun get(typeIdentifier: TypeIdentifier): LocalTypeInformation? = cache[typeIdentifier]
}