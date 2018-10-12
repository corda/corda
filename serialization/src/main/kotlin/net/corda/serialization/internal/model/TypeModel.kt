package net.corda.serialization.internal.model

import com.google.common.reflect.TypeToken
import net.corda.serialization.internal.amqp.asClass
import net.corda.serialization.internal.amqp.componentType
import net.corda.serialization.internal.amqp.propertyDescriptors
import java.lang.reflect.*
import java.util.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter

interface CacheProvider {
    fun <K, V> createCache(): MutableMap<K, V>
}

object DefaultCacheProvider: CacheProvider {
    override fun <K, V> createCache(): MutableMap<K, V> = mutableMapOf()
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

    abstract val name: String

    companion object {
        fun forClass(type: Class<*>): TypeIdentifier = when {
            type.name == "java.lang.Object" -> Any
            type.isArray -> ArrayOf(forClass(type.componentType))
            type.typeParameters.isEmpty() -> Unparameterised(type.name)
            else -> Erased(type.name)
        }

        fun forGenericType(type: Type): TypeIdentifier = when(type) {
            is ParameterizedType -> Parameterised((type.rawType as Class<*>).name, type.actualTypeArguments.map {
                forGenericType(it.resolveAgainst(type))
            })
            is Class<*> -> forClass(type)
            is GenericArrayType -> ArrayOf(forGenericType(type.genericComponentType.resolveAgainst(type)))
            else -> Unknown
        }
    }

    object Any: TypeIdentifier() {
        override val name = "*"
    }

    object Unknown: TypeIdentifier() {
        override val name = "?"
    }

    data class Unparameterised(override val name: String): TypeIdentifier()
    /**
     * A class such as List<Int>, for which we cannot obtain the type parameters at runtime because they have been erased.
     */
    data class Erased(override val name: String): TypeIdentifier()
    data class ArrayOf(val componentType: TypeIdentifier): TypeIdentifier() {
        override val name get() = componentType.name + "[]"
    }

    data class Parameterised(override val name: String, val parameters: List<TypeIdentifier>): TypeIdentifier()
}

interface LocalTypeLookup {
    fun lookup(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation): LocalTypeInformation
}

object AlwaysBuildLookup : LocalTypeLookup {
    override fun lookup(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation): LocalTypeInformation =
            builder()
}

data class LocalPropertyInformation(val type: LocalTypeInformation, val isMandatory: Boolean)

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

    data class Cycle(override val observedType: Type, override val typeIdentifier: TypeIdentifier): LocalTypeInformation()

    /**
     * May in fact be a more complex class, but is treated like a primitive, i.e. we don't further expand its properties.
     */
    data class Opaque(override val observedType: Class<*>, override val typeIdentifier: TypeIdentifier): LocalTypeInformation()

    data class APrimitive(override val observedType: Class<*>, override val typeIdentifier: TypeIdentifier): LocalTypeInformation()

    data class AnArray(override val observedType: Type, override val typeIdentifier: TypeIdentifier, val componentType: LocalTypeInformation): LocalTypeInformation()

    data class AnEnum(override val observedType: Class<*>, override val typeIdentifier: TypeIdentifier, val interfaces: List<LocalTypeInformation>, val members: List<String>)
        : LocalTypeInformation()

    data class AnInterface(override val observedType: Type, override val typeIdentifier: TypeIdentifier, val interfaces: List<LocalTypeInformation>, val typeParameters: List<LocalTypeInformation>): LocalTypeInformation()

    data class AnObject(
            override val observedType: Type,
            override val typeIdentifier: TypeIdentifier,
            val properties: Map<PropertyName, LocalPropertyInformation>,
            val interfaces: List<LocalTypeInformation>,
            val typeParameters: List<LocalTypeInformation>): LocalTypeInformation()

    data class ACollection(override val observedType: Class<*>, override val typeIdentifier: TypeIdentifier, val typeParameters: List<LocalTypeInformation>): LocalTypeInformation()
}

private data class LocalTypeInformationBuilder(val lookup: LocalTypeLookup, val resolutionContext: Type? = null, val visited: Set<TypeIdentifier> = emptySet()) {

    fun build(type: Type, typeIdentifier: TypeIdentifier): LocalTypeInformation {
        return if (typeIdentifier in visited) LocalTypeInformation.Cycle(type, typeIdentifier) else
        lookup.lookup(type, typeIdentifier)  {
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
        type.isInterface -> LocalTypeInformation.AnInterface(type, typeIdentifier, buildInterfaceInformation(type), emptyList())
        type.isPrimitive -> LocalTypeInformation.APrimitive(type, typeIdentifier)
        type.isEnum ->
            LocalTypeInformation.AnEnum(
                    type,
                    typeIdentifier,
                    buildInterfaceInformation(type),
                    type.enumConstants.map { it.toString() })
        else -> copy(resolutionContext = type).run {
            LocalTypeInformation.AnObject(
                    type,
                    typeIdentifier,
                    getObjectProperties(type),
                    buildInterfaceInformation(type),
                    emptyList()
            )
        }
    }

    private fun buildForParameterised(rawType: Class<*>, type: ParameterizedType, typeIdentifier: TypeIdentifier.Parameterised): LocalTypeInformation =
            when {
                rawType.isCollectionOrMap -> LocalTypeInformation.ACollection(rawType, typeIdentifier, getTypeParameterInformation(type))
                rawType.isInterface -> LocalTypeInformation.AnInterface(rawType, typeIdentifier, buildInterfaceInformation(type), getTypeParameterInformation(type))
                else -> copy(resolutionContext = type).run {
                    LocalTypeInformation.AnObject(
                            rawType,
                            typeIdentifier,
                            getObjectProperties(rawType),
                            buildInterfaceInformation(rawType),
                            getTypeParameterInformation(type))
                }
            }

    private fun buildInterfaceInformation(type: Type) =
            type.allInterfaces.mapNotNull {
                if (it == type) return@mapNotNull null
                val resolved = it.resolveAgainst(resolutionContext ?: type)
                build(resolved, TypeIdentifier.forGenericType(resolved))
            }.toList()

    private val Type.allInterfaces: Sequence<Type> get() =
        generateSequence(this) { it.asClass().genericSuperclass }
                .flatMap { it -> it.asClass().genericInterfaces.asSequence().flatMap { it.allInterfaces }  + it }
                .filter { it.asClass().isInterface }
                .distinct()

    private fun getObjectProperties(rawType: Class<*>): Map<PropertyName, LocalPropertyInformation> =
            rawType.propertyDescriptors().mapNotNull { (k, v) ->
                if (v.getter == null || v.field == null) return@mapNotNull null
                val paramType = (v.field.genericType ?: v.getter.genericReturnType).resolveAgainst(resolutionContext ?: rawType)
                val paramTypeInformation = build(paramType, TypeIdentifier.forGenericType(paramType))
                val isMandatory = paramType.asClass().isPrimitive || !v.getter.returnsNullable()
                k to LocalPropertyInformation(paramTypeInformation, isMandatory)
            }.toMap()

    private fun getTypeParameterInformation(type: ParameterizedType): List<LocalTypeInformation> =
            type.actualTypeArguments.map {
                val upperBound = it.upperBound
                build(upperBound, TypeIdentifier.forGenericType(upperBound))
            }

    private val Class<*>.isCollectionOrMap get() =
        (Collection::class.java.isAssignableFrom(this) || Map::class.java.isAssignableFrom(this))
                && !EnumSet::class.java.isAssignableFrom(this)
}

private fun Type.resolveAgainst(context: Type): Type = when(this) {
    is WildcardType -> this.upperBound
    is ParameterizedType,
    is TypeVariable<*> -> TypeToken.of(context).resolveType(this).type.upperBound
    else -> this
}

private val Type.upperBound: Type get() = when(this) {
    is TypeVariable<*> -> when {
        this.bounds.isEmpty() ||
        this.bounds.size > 1 -> this
        else -> this.bounds[0]
    }
    is WildcardType -> when {
        this.upperBounds.isEmpty() ||
        this.upperBounds.size > 1 -> this
        else -> this.upperBounds[0]
    }
    else -> this
}

interface OpacityRule {
    fun isOpaque(type: Type): Boolean

    operator fun plus(next: OpacityRule) = object : OpacityRule {
        override fun isOpaque(type: Type): Boolean =
                this@OpacityRule.isOpaque(type) || next.isOpaque(type)
    }
}

object DefaultOpacityRule: OpacityRule {
    override fun isOpaque(type: Type): Boolean = type.typeName.startsWith("java")
}

class LocalTypeModel(val opacityRule: OpacityRule = DefaultOpacityRule): LocalTypeLookup {

    private val cache = DefaultCacheProvider.createCache<TypeIdentifier, LocalTypeInformation>()

    fun interpret(type: Type): LocalTypeInformation = LocalTypeInformation.forType(type, this)

    override fun lookup(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation): LocalTypeInformation =
            cache[typeIdentifier] ?: buildIfNotOpaque(type, typeIdentifier, builder).apply {
                cache.putIfAbsent(typeIdentifier, this)
            }

    private fun buildIfNotOpaque(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation) =
            if (opacityRule.isOpaque(type)) LocalTypeInformation.Opaque(type.asClass(), typeIdentifier)
            else builder()


    operator fun get(typeIdentifier: TypeIdentifier): LocalTypeInformation? = cache[typeIdentifier]

    val knownIdentifiers get() = cache.keys.toSet()
}

private fun Method.returnsNullable(): Boolean = try {
    val returnTypeString = this.declaringClass.kotlin.memberProperties.firstOrNull {
        it.javaGetter == this
    }?.returnType?.toString() ?: "?"

    returnTypeString.endsWith('?') || returnTypeString.endsWith('!')
} catch (e: kotlin.reflect.jvm.internal.KotlinReflectionInternalError) {
    // This might happen for some types, e.g. kotlin.Throwable? - the root cause of the issue
    // is: https://youtrack.jetbrains.com/issue/KT-13077
    // TODO: Revisit this when Kotlin issue is fixed.

    true
}

private val String.simple: String get() = split(".", "$").last()

fun TypeIdentifier.prettyPrint(): String =
        when(this) {
            is TypeIdentifier.Unknown -> "?"
            is TypeIdentifier.Any -> "*"
            is TypeIdentifier.Unparameterised -> "${name.simple}"
            is TypeIdentifier.Erased -> "${name.simple} (erased)"
            is TypeIdentifier.ArrayOf -> this.componentType.prettyPrint() + "[]"
            is TypeIdentifier.Parameterised ->
                this.name.simple + this.parameters.joinToString(", ", "<", ">") {
                    it.prettyPrint()
                }
        }

fun LocalTypeInformation.prettyPrint(indent: Int = 0): String {
    return when(this) {
        is LocalTypeInformation.AnObject -> typeIdentifier.prettyPrint() +
                (if (interfaces.isEmpty()) "" else  interfaces.joinToString(", ", ": ", "") { it.prettyPrint() }) +
                this.properties.entries.joinToString("\n", "\n", "") { it.prettyPrint(indent + 1) }
        else -> typeIdentifier.prettyPrint()
    }
}

private fun Map.Entry<String, LocalPropertyInformation>.prettyPrint(indent: Int): String =
        "  ".repeat(indent) + key + ": " + value.type.prettyPrint(indent + 1).trimStart()