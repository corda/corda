package net.corda.serialization.internal.model

import net.corda.serialization.internal.amqp.asClass
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

sealed class LocalTypeInformation {
    abstract val type: Type
    open val typeName get() = type.typeName!!

    object Unknown: LocalTypeInformation() {
        override val type = Any::class.java
        override val typeName = "?"
    }

    data class APrimitive(override val type: Class<*>) : LocalTypeInformation()

    data class AnArray(override val type: Type, val componentType: LocalTypeInformation): LocalTypeInformation()

    data class AnEnum(override val type: Type, val interfaces: List<LocalTypeInformation>, val members: List<String>)
        : LocalTypeInformation()

    data class AnInterface(override val type: Type, val typeParameters: List<LocalTypeInformation>): LocalTypeInformation()

    data class AnObject(
            override val type: Type,
            val properties: Map<PropertyName, LocalTypeInformation>,
            val interfaces: List<LocalTypeInformation>,
            val typeParameters: List<LocalTypeInformation>): LocalTypeInformation()

    data class ACollection(override val type: Class<*>, val typeParameters: List<LocalTypeInformation>): LocalTypeInformation()
}

class CircularTypeDefinitionException(val type: Type): Exception("Circular dependency detected in definition of $type")

class LocalTypeInterpreter {
    val cache = DefaultCacheProvider.createCache<Type, LocalTypeInformation>()

    fun interpret(type: Type): LocalTypeInformation = interpret(type, mutableSetOf())

    private fun interpret(type: Type, visited: Set<Type>): LocalTypeInformation =
        cache[type] ?:
        interpretUncached(type, visited).apply { cache.putIfAbsent(type, this) }

    private fun interpretUncached(type: Type, visited: Set<Type>): LocalTypeInformation {
        if (type in visited) throw CircularTypeDefinitionException(type)

        val rawType = type.asClass()
        val newVisited = visited + type

        return when(type) {
            is ParameterizedType -> getParameterizedTypeInformation(rawType, type, newVisited)
            is Class<*> -> getClassInformation(rawType, newVisited)
            is GenericArrayType -> LocalTypeInformation.AnArray(type, interpret(resolveTypeVariables(type.genericComponentType, type), newVisited))
            is TypeVariable<*>, is WildcardType -> LocalTypeInformation.Unknown
            else -> throw UnsupportedOperationException("Cannot obtain type information for $this")
        }
    }

    private fun getClassInformation(type: Class<*>, visited: Set<Type>): LocalTypeInformation = when {
            type.isInterface -> LocalTypeInformation.AnInterface(type, emptyList())
            type.isPrimitive || type.typeName.startsWith("java") -> LocalTypeInformation.APrimitive(type)
            type.isArray -> LocalTypeInformation.AnArray(type, interpret(resolveTypeVariables(type.componentType, type), visited))
            type.isEnum ->
                LocalTypeInformation.AnEnum(
                        type,
                        type.genericInterfaces.map { interpret(it, visited) },
                        type.enumConstants.map { it.toString() })
            else -> LocalTypeInformation.AnObject(
                    type,
                    getObjectProperties(type, type, visited),
                    type.getAllInterfaces().map { interpret(it, visited) }.toList(),
                    emptyList()
            )
        }

    private fun getParameterizedTypeInformation(rawType: Class<*>, type: ParameterizedType, visited: Set<Type>): LocalTypeInformation =
        when {
            rawType.isCollectionOrMap -> LocalTypeInformation.ACollection(rawType, getTypeParameterInformation(type, visited))
            rawType.isInterface -> LocalTypeInformation.AnInterface(type, getTypeParameterInformation(type, visited))
            else -> LocalTypeInformation.AnObject(
                    type,
                    getObjectProperties(rawType, type, visited + type),
                    rawType.getAllInterfaces().map { interpret(it, visited) }.toList(),
                    emptyList())
        }

    private fun getObjectProperties(rawType: Class<*>, contextType: Type, visited: Set<Type>): Map<PropertyName, LocalTypeInformation> =
            rawType.propertyDescriptors().mapValues { (_, v) ->
                interpret(resolveTypeVariables(v.field?.genericType ?: v.getter!!.genericReturnType, contextType), visited)
            }

    private fun getTypeParameterInformation(type: ParameterizedType, visited: Set<Type>): List<LocalTypeInformation> =
            type.actualTypeArguments.map { interpret(it, visited) }

    private val Class<*>.isCollectionOrMap get() =
        (Collection::class.java.isAssignableFrom(this) || Map::class.java.isAssignableFrom(this))
                && !EnumSet::class.java.isAssignableFrom(this)

    private fun Type.getAllInterfaces(): Sequence<Type> =
            generateSequence(this) { it.asClass().genericSuperclass }
                    .flatMap { it -> it.asClass().genericInterfaces.asSequence().flatMap { it.getAllInterfaces() }  + it }
                    .filter { it.asClass().isInterface }
                    .distinct()
}
