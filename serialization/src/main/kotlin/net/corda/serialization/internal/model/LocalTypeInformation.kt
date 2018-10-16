package net.corda.serialization.internal.model

import com.google.common.reflect.TypeToken
import net.corda.core.internal.isAbstractClass
import net.corda.core.internal.kotlinObjectInstance
import net.corda.serialization.internal.amqp.*
import java.io.NotSerializableException
import java.lang.reflect.*
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaType

typealias PropertyName = String

interface LocalTypeLookup {
    fun lookup(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation): LocalTypeInformation
    fun isExcluded(type: Type): Boolean
}

@Suppress("unused")
sealed class LocalPropertyInformation(val isCalculated: Boolean) {
    abstract val type: LocalTypeInformation
    abstract val isMandatory: Boolean

    data class ReadOnlyProperty(val observedGetter: Method, override val type: LocalTypeInformation, override val isMandatory: Boolean) : LocalPropertyInformation(false)
    data class ConstructorPairedProperty(val observedGetter: Method, override val type: LocalTypeInformation, override val isMandatory: Boolean) : LocalPropertyInformation(false)
    data class GetterSetterProperty(val observedGetter: Method, val observedSetter: Method, override val type: LocalTypeInformation, override val isMandatory: Boolean) : LocalPropertyInformation(false)
    data class CalculatedProperty(val observedGetter: Method, override val type: LocalTypeInformation, override val isMandatory: Boolean) : LocalPropertyInformation(true)
}

data class LocalConstructorParameterInformation(
        val name: String,
        val type: LocalTypeInformation,
        val isMandatory: Boolean)

data class LocalConstructorInformation(
        val observedMethod: KFunction<Any>,
        val parameters: List<LocalConstructorParameterInformation>)

sealed class LocalTypeInformation {

    companion object {
        fun forType(type: Type, lookup: LocalTypeLookup): LocalTypeInformation =
                LocalTypeInformationBuilder(lookup).build(type, TypeIdentifier.forGenericType(type))
    }

    abstract val observedType: Type
    abstract val typeIdentifier: TypeIdentifier

    //region Pretty printing
    fun prettyPrint(indent: Int = 0): String {
        return when (this) {
            is LocalTypeInformation.Abstract ->
                typeIdentifier.prettyPrint() + printInterfaces(interfaces) + printProperties(properties, indent + 1)
            is LocalTypeInformation.AnInterface ->
                typeIdentifier.prettyPrint() + printInterfaces(interfaces)
            is LocalTypeInformation.APojo -> typeIdentifier.prettyPrint() +
                    printConstructor(constructor) +
                    printInterfaces(interfaces) +
                    printProperties(properties, indent + 1)
            else -> typeIdentifier.prettyPrint()
        }
    }

    private fun printConstructor(constructor: LocalConstructorInformation) =
            constructor.parameters.joinToString(", ", "(", ")") {
                it.name +
                ": " + it.type.typeIdentifier.prettyPrint() +
                (if (!it.isMandatory) "?" else "")
            }

    private fun printInterfaces(interfaces: List<LocalTypeInformation>) =
            if (interfaces.isEmpty()) ""
            else interfaces.joinToString(", ", ": ", "") { it.typeIdentifier.prettyPrint() }

    private fun printProperties(properties: Map<String, LocalPropertyInformation>, indent: Int) =
            properties.entries.sortedBy { it.key }.joinToString("\n", "\n", "") {
                it.prettyPrint(indent)
            }

    private fun Map.Entry<String, LocalPropertyInformation>.prettyPrint(indent: Int): String =
            "  ".repeat(indent) + key +
                    (if(!value.isMandatory) " (optional)" else "") +
                    (if (value.isCalculated) " (calculated)" else "") +
                    ": " + value.type.prettyPrint(indent)
    //endregion

    object Unknown : LocalTypeInformation() {
        override val observedType get() = throw UnsupportedOperationException("Type is unknown")
        override val typeIdentifier = TypeIdentifier.Unknown
    }

    object Any : LocalTypeInformation() {
        override val observedType = Any::class.java
        override val typeIdentifier = TypeIdentifier.Any
    }

    data class Cycle(override val observedType: Type, override val typeIdentifier: TypeIdentifier) : LocalTypeInformation()

    /**
     * May in fact be a more complex class, but is treated like a primitive, i.e. we don't further expand its properties.
     */
    data class Opaque(override val observedType: Class<*>, override val typeIdentifier: TypeIdentifier) : LocalTypeInformation()

    data class APrimitive(override val observedType: Class<*>, override val typeIdentifier: TypeIdentifier) : LocalTypeInformation()

    data class AnArray(override val observedType: Type, override val typeIdentifier: TypeIdentifier, val componentType: LocalTypeInformation) : LocalTypeInformation()

    data class AnEnum(override val observedType: Class<*>, override val typeIdentifier: TypeIdentifier, val interfaces: List<LocalTypeInformation>, val members: List<String>)
        : LocalTypeInformation()

    data class AnInterface(override val observedType: Type, override val typeIdentifier: TypeIdentifier, val interfaces: List<LocalTypeInformation>, val typeParameters: List<LocalTypeInformation>) : LocalTypeInformation()

    data class Abstract(
            override val observedType: Type,
            override val typeIdentifier: TypeIdentifier,
            val properties: Map<PropertyName, LocalPropertyInformation>,
            val interfaces: List<LocalTypeInformation>,
            val typeParameters: List<LocalTypeInformation>) : LocalTypeInformation()

    data class AnObject(override val observedType: Type, override val typeIdentifier: TypeIdentifier, val interfaces: List<LocalTypeInformation>, val typeParameters: List<LocalTypeInformation>) : LocalTypeInformation()

    data class APojo(
            override val observedType: Type,
            override val typeIdentifier: TypeIdentifier,
            val constructor: LocalConstructorInformation,
            val properties: Map<PropertyName, LocalPropertyInformation>,
            val interfaces: List<LocalTypeInformation>,
            val typeParameters: List<LocalTypeInformation>) : LocalTypeInformation()

    data class ACollection(override val observedType: Type, override val typeIdentifier: TypeIdentifier, val typeParameters: List<LocalTypeInformation>) : LocalTypeInformation()
}

private data class LocalTypeInformationBuilder(val lookup: LocalTypeLookup, val resolutionContext: Type? = null, val visited: Set<TypeIdentifier> = emptySet()) {

    fun build(type: Type, typeIdentifier: TypeIdentifier): LocalTypeInformation {
        return if (typeIdentifier in visited) LocalTypeInformation.Cycle(type, typeIdentifier) else
            lookup.lookup(type, typeIdentifier) {
                copy(visited = visited + typeIdentifier).buildIfNotFound(type, typeIdentifier)
            }
    }

    private fun resolveAndBuild(type: Type): LocalTypeInformation {
        val resolved = type.resolveAgainstContext()
        return build(resolved, TypeIdentifier.forGenericType(resolved, resolutionContext ?: type))
    }

    private fun Type.resolveAgainstContext(): Type =
            if (resolutionContext == null) this else resolveAgainst(resolutionContext)

    private fun buildIfNotFound(type: Type, typeIdentifier: TypeIdentifier): LocalTypeInformation {
        val rawType = type.asClass()
        return when (typeIdentifier) {
            is TypeIdentifier.Any -> LocalTypeInformation.Any
            is TypeIdentifier.Unknown -> LocalTypeInformation.Unknown
            is TypeIdentifier.Unparameterised,
            is TypeIdentifier.Erased -> buildForClass(rawType, typeIdentifier)
            is TypeIdentifier.ArrayOf -> {
                LocalTypeInformation.AnArray(
                        type,
                        typeIdentifier,
                        resolveAndBuild(type.componentType()))
            }
            is TypeIdentifier.Parameterised -> buildForParameterised(rawType, type as ParameterizedType, typeIdentifier)
        }
    }

    private fun buildForClass(type: Class<*>, typeIdentifier: TypeIdentifier): LocalTypeInformation = withContext(type) {
        when {
            type.isInterface -> LocalTypeInformation.AnInterface(type, typeIdentifier, buildInterfaceInformation(type), emptyList())
            type.isPrimitive -> LocalTypeInformation.APrimitive(type, typeIdentifier)
            type.isEnum -> LocalTypeInformation.AnEnum(
                    type,
                    typeIdentifier,
                    buildInterfaceInformation(type),
                    type.enumConstants.map { it.toString() })
            type.isAbstractClass -> LocalTypeInformation.Abstract(
                    type,
                    typeIdentifier,
                    buildAbstractProperties(type),
                    buildInterfaceInformation(type),
                    emptyList())
            type.kotlinObjectInstance != null -> LocalTypeInformation.AnObject(
                    type,
                    typeIdentifier,
                    buildInterfaceInformation(type),
                    emptyList())
            else -> {
                val constructorInformation = buildConstructorInformation(type)
                LocalTypeInformation.APojo(
                        type,
                        typeIdentifier,
                        constructorInformation,
                        buildObjectProperties(type, constructorInformation),
                        buildInterfaceInformation(type),
                        emptyList())
            }
        }
    }

    private inline fun <T> withContext(newContext: Type, block: LocalTypeInformationBuilder.() -> T): T =
            copy(resolutionContext = newContext).run(block)

    private fun buildForParameterised(
            rawType: Class<*>,
            type: ParameterizedType,
            typeIdentifier: TypeIdentifier.Parameterised): LocalTypeInformation = withContext(type) {
        when {
            rawType.isCollectionOrMap -> LocalTypeInformation.ACollection(rawType, typeIdentifier, buildTypeParameterInformation(type))
            rawType.isInterface -> LocalTypeInformation.AnInterface(rawType, typeIdentifier, buildInterfaceInformation(type), buildTypeParameterInformation(type))
            rawType.isAbstractClass -> {
                LocalTypeInformation.Abstract(
                        type,
                        typeIdentifier,
                        buildAbstractProperties(rawType),
                        buildInterfaceInformation(type),
                        buildTypeParameterInformation(type)
                )
            }
            rawType.kotlinObjectInstance != null -> LocalTypeInformation.AnObject(
                    type,
                    typeIdentifier,
                    buildInterfaceInformation(type),
                    buildTypeParameterInformation(type))
            else -> {
                val constructorInformation = buildConstructorInformation(type)
                LocalTypeInformation.APojo(
                        rawType,
                        typeIdentifier,
                        constructorInformation,
                        buildObjectProperties(rawType, constructorInformation),
                        buildInterfaceInformation(type),
                        buildTypeParameterInformation(type))
            }
        }
    }

    private fun buildInterfaceInformation(type: Type) =
            type.allInterfaces.mapNotNull {
                if (it == type) return@mapNotNull null
                resolveAndBuild(it)
            }.toList()

    private val Type.allInterfaces: Set<Type> get() = exploreType(this)

    private fun exploreType(type: Type, interfaces: MutableSet<Type> = LinkedHashSet()): MutableSet<Type> {
        val clazz = type.asClass()

        if (clazz.isInterface) {
            // Ignore classes we've already seen, and stop exploring once we reach a branch that has no `CordaSerializable`
            // annotation or whitelisting.
            if (clazz in interfaces || lookup.isExcluded(clazz)) return interfaces
            else interfaces += type
        }

        (clazz.genericInterfaces.asSequence() + clazz.genericSuperclass)
                .filterNotNull()
                .forEach { exploreType(it.resolveAgainstContext(), interfaces) }

        return interfaces
    }

    private fun buildAbstractProperties(rawType: Class<*>): Map<PropertyName, LocalPropertyInformation> =
            rawType.propertyDescriptors().asSequence().mapNotNull { (name, descriptor) ->
                if (descriptor.field == null || descriptor.getter == null) null
                else {
                    val paramType = (descriptor.getter.genericReturnType).resolveAgainstContext()
                    val paramTypeInformation = build(paramType, TypeIdentifier.forGenericType(paramType, resolutionContext
                            ?: rawType))
                    val isMandatory = paramType.asClass().isPrimitive || !descriptor.getter.returnsNullable()
                    name to LocalPropertyInformation.ReadOnlyProperty(descriptor.getter, paramTypeInformation, isMandatory)
                }
            }.toMap()

    private fun buildObjectProperties(rawType: Class<*>, constructorInformation: LocalConstructorInformation): Map<PropertyName, LocalPropertyInformation> =
            calculatedProperties(rawType) + nonCalculatedProperties(rawType, constructorInformation)

    private fun nonCalculatedProperties(rawType: Class<*>, constructorInformation: LocalConstructorInformation): Map<String, LocalPropertyInformation> =
            if (constructorInformation.parameters.isEmpty()) getterSetterProperties(rawType)
            else getConstructorPairedProperties(constructorInformation, rawType)

    private fun getConstructorPairedProperties(constructorInformation: LocalConstructorInformation, rawType: Class<*>): Map<String, LocalPropertyInformation.ConstructorPairedProperty> {
        val constructorParameterNames = constructorInformation.parameters.asSequence().map { it.name }.toSet()
        return rawType.propertyDescriptors().asSequence().mapNotNull { (name, descriptor) ->
            if (name !in constructorParameterNames) return@mapNotNull null
            if (descriptor.field == null || descriptor.getter == null) return@mapNotNull null

            val paramType = descriptor.getter.genericReturnType
            val paramTypeInformation = resolveAndBuild(paramType)
            val isMandatory = paramType.asClass().isPrimitive || !descriptor.getter.returnsNullable()

            name to LocalPropertyInformation.ConstructorPairedProperty(
                    descriptor.getter,
                    paramTypeInformation,
                    isMandatory)
        }.toMap()
    }

    private fun getterSetterProperties(rawType: Class<*>): Map<String, LocalPropertyInformation> =
            rawType.propertyDescriptors().asSequence().mapNotNull { (name, descriptor) ->
                if (descriptor.getter == null || descriptor.setter == null || descriptor.field == null) null
                else {
                    val paramType = descriptor.getter.genericReturnType
                    val paramTypeInformation = resolveAndBuild(paramType)
                    val isMandatory = paramType.asClass().isPrimitive || !descriptor.getter.returnsNullable()

                    name to LocalPropertyInformation.GetterSetterProperty(
                            descriptor.getter,
                            descriptor.setter,
                            paramTypeInformation,
                            isMandatory)
                }
            }.toMap()

    private fun calculatedProperties(rawType: Class<*>): Map<String, LocalPropertyInformation> =
            rawType.calculatedPropertyDescriptors().mapValues { (_, v) ->
                val paramType = v.getter!!.genericReturnType
                val paramTypeInformation = resolveAndBuild(paramType)
                val isMandatory = paramType.asClass().isPrimitive || !v.getter.returnsNullable()

                LocalPropertyInformation.CalculatedProperty(v.getter, paramTypeInformation, isMandatory)
            }

    private fun buildTypeParameterInformation(type: ParameterizedType): List<LocalTypeInformation> =
            type.actualTypeArguments.map {
                resolveAndBuild(it)
            }

    private fun buildConstructorInformation(type: Type): LocalConstructorInformation {
        val observedConstructor = constructorForDeserialization(type)
        if (observedConstructor.javaConstructor?.parameters?.getOrNull(0)?.name == "this$0")
            throw NotSerializableException("Type '${type.typeName} has synthetic fields and is likely a nested inner class.")

        return LocalConstructorInformation(observedConstructor, observedConstructor.parameters.map {
            val parameterType = it.type.javaType
            LocalConstructorParameterInformation(
                    it.name ?: throw IllegalStateException("Unnamed parameter in constructor $observedConstructor"),
                    resolveAndBuild(parameterType),
                    parameterType.asClass().isPrimitive || !it.type.isMarkedNullable)
        })
    }
}

internal fun Type.resolveAgainst(context: Type): Type = when (this) {
    is WildcardType -> this.upperBound
    is ParameterizedType,
    is TypeVariable<*> -> TypeToken.of(context).resolveType(this).type.upperBound
    else -> this
}

private val Type.upperBound: Type
    get() = when (this) {
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

internal val Class<*>.isCollectionOrMap
    get() =
        (Collection::class.java.isAssignableFrom(this) || Map::class.java.isAssignableFrom(this))
                && !EnumSet::class.java.isAssignableFrom(this)