package net.corda.serialization.internal.model

import net.corda.core.internal.isAbstractClass
import net.corda.core.internal.isConcreteClass
import net.corda.core.internal.kotlinObjectInstance
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.utilities.contextLogger
import net.corda.serialization.internal.amqp.*
import java.io.NotSerializableException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.internal.KotlinReflectionInternalError
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaType

/**
 * Provides the logic for building instances of [LocalTypeInformation] by reflecting over local [Type]s.
 *
 * @param lookup The [LocalTypeLookup] to use to locate and register constructed [LocalTypeInformation].
 * @param resolutionContext The [Type] to use when attempting to resolve type variables.
 * @param visited The [Set] of [TypeIdentifier]s already visited while building information for a given [Type]. Note that
 * this is not a [MutableSet], as we want to be able to backtrack while traversing through the graph of related types, and
 * will find it useful to revert to earlier states of knowledge about which types have been visited on a given branch.
 */
internal data class LocalTypeInformationBuilder(val lookup: LocalTypeLookup, val resolutionContext: Type? = null, val visited: Set<TypeIdentifier> = emptySet()) {

    companion object {
        private val logger = contextLogger()
    }

    /**
     * Recursively build [LocalTypeInformation] for the given [Type] and [TypeIdentifier]
     */
    fun build(type: Type, typeIdentifier: TypeIdentifier): LocalTypeInformation =
        if (typeIdentifier in visited) LocalTypeInformation.Cycle(type, typeIdentifier) {
            LocalTypeInformationBuilder(lookup, resolutionContext).build(type, typeIdentifier)
        }
        else lookup.findOrBuild(type, typeIdentifier) {
            copy(visited = visited + typeIdentifier).buildIfNotFound(type, typeIdentifier)
        }

    private fun resolveAndBuild(type: Type): LocalTypeInformation {
        val resolved = type.resolveAgainstContext()
        return build(resolved, TypeIdentifier.forGenericType(resolved, resolutionContext
                ?: type))
    }

    private fun Type.resolveAgainstContext(): Type =
            if (resolutionContext == null) this else resolveAgainst(resolutionContext)

    private fun buildIfNotFound(type: Type, typeIdentifier: TypeIdentifier): LocalTypeInformation {
        val rawType = type.asClass()
        return when (typeIdentifier) {
            is TypeIdentifier.TopType -> LocalTypeInformation.Top
            is TypeIdentifier.UnknownType -> LocalTypeInformation.Unknown
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
            Collection::class.java.isAssignableFrom(type) &&
            !EnumSet::class.java.isAssignableFrom(type) -> LocalTypeInformation.ACollection(type, typeIdentifier, LocalTypeInformation.Unknown)
            Map::class.java.isAssignableFrom(type) -> LocalTypeInformation.AMap(type, typeIdentifier, LocalTypeInformation.Unknown, LocalTypeInformation.Unknown)
            type.isInterface -> buildInterface(type, typeIdentifier, emptyList())
            type.isPrimitive -> LocalTypeInformation.Atomic(type, typeIdentifier)
            type.isEnum -> LocalTypeInformation.AnEnum(
                    type,
                    typeIdentifier,
                    type.enumConstants.map { it.toString() },
                    buildInterfaceInformation(type))
            type.isAbstractClass -> buildAbstract(type, typeIdentifier, emptyList())
            type.kotlinObjectInstance != null -> LocalTypeInformation.Singleton(
                    type,
                    typeIdentifier,
                    buildSuperclassInformation(type),
                    buildInterfaceInformation(type))
            else -> buildNonAtomic(type, type, typeIdentifier, emptyList())
        }
    }

    private fun buildForParameterised(
            rawType: Class<*>,
            type: ParameterizedType,
            typeIdentifier: TypeIdentifier.Parameterised): LocalTypeInformation = withContext(type) {
        when {
            Collection::class.java.isAssignableFrom(rawType) &&
            !EnumSet::class.java.isAssignableFrom(rawType) ->
                LocalTypeInformation.ACollection(type, typeIdentifier, buildTypeParameterInformation(type)[0])
            Map::class.java.isAssignableFrom(rawType) -> {
                val (keyType, valueType) = buildTypeParameterInformation(type)
                LocalTypeInformation.AMap(type, typeIdentifier, keyType, valueType)
            }
            rawType.isInterface -> buildInterface(type, typeIdentifier, buildTypeParameterInformation(type))
            rawType.isAbstractClass -> buildAbstract(type, typeIdentifier, buildTypeParameterInformation(type))
            else -> buildNonAtomic(rawType, type, typeIdentifier, buildTypeParameterInformation(type))
        }
    }

    private fun buildAbstract(type: Type, typeIdentifier: TypeIdentifier,
                              typeParameters: List<LocalTypeInformation>): LocalTypeInformation.Abstract =
            LocalTypeInformation.Abstract(
                    type,
                    typeIdentifier,
                    buildReadOnlyProperties(type.asClass()),
                    buildSuperclassInformation(type),
                    buildInterfaceInformation(type),
                    typeParameters)

    private fun buildInterface(type: Type, typeIdentifier: TypeIdentifier,
                               typeParameters: List<LocalTypeInformation>): LocalTypeInformation.AnInterface =
            LocalTypeInformation.AnInterface(
                    type,
                    typeIdentifier,
                    buildReadOnlyProperties(type.asClass()),
                    buildInterfaceInformation(type),
                    typeParameters)

    private inline fun <T> withContext(newContext: Type, block: LocalTypeInformationBuilder.() -> T): T =
            copy(resolutionContext = newContext).run(block)

    /**
     * Build a non-atomic type, which is either [Composable] or [NonComposable].
     *
     * Composability is a transitive property: a type is [Composable] iff it has a unique deserialization constructor _and_
     * all of its property types are also [Composable]. If not, the type is [NonComposable], meaning we cannot deserialize
     * it without a custom serializer (in which case it should normally have been flagged as [Opaque]).
     *
     * Rather than throwing an exception if a type is [NonComposable], we capture its type information so that it can
     * still be used to _serialize_ values, or as the basis for deciding on an evolution strategy.
     */
    private fun buildNonAtomic(rawType: Class<*>, type: Type, typeIdentifier: TypeIdentifier, typeParameterInformation: List<LocalTypeInformation>): LocalTypeInformation {
        val superclassInformation = buildSuperclassInformation(type)
        val interfaceInformation = buildInterfaceInformation(type)
        val observedConstructor = constructorForDeserialization(type)

        if (observedConstructor == null) {
            logger.warn("No unique deserialisation constructor found for class $rawType, type is marked as non-composable")
            return LocalTypeInformation.NonComposable(type, typeIdentifier, buildReadOnlyProperties(rawType),
                    superclassInformation, interfaceInformation, typeParameterInformation)
        }

        val constructorInformation = buildConstructorInformation(type, observedConstructor)
        val properties = buildObjectProperties(rawType, constructorInformation)

        val hasNonComposableProperties = properties.values.any { it.type is LocalTypeInformation.NonComposable }

        if (!propertiesSatisfyConstructor(constructorInformation, properties) || hasNonComposableProperties) {
            if (hasNonComposableProperties) {
                logger.warn("Type ${type.typeName} has non-composable properties and has been marked as non-composable")
            } else {
                logger.warn("Properties of type ${type.typeName} do not satisfy its constructor, type has been marked as non-composable")
            }
            return LocalTypeInformation.NonComposable(type, typeIdentifier, properties, superclassInformation,
                    interfaceInformation, typeParameterInformation)
        }

        return LocalTypeInformation.Composable(rawType, typeIdentifier, constructorInformation, properties,
                superclassInformation, interfaceInformation, typeParameterInformation)
    }

    // Can we supply all of the mandatory constructor parameters using values addressed by readable properties?
    private fun propertiesSatisfyConstructor(constructorInformation: LocalConstructorInformation, properties: Map<PropertyName, LocalPropertyInformation>): Boolean {
        if (!constructorInformation.hasParameters) return true

        val indicesAddressedByProperties = properties.values.asSequence().mapNotNull {
            when (it) {
                is LocalPropertyInformation.ConstructorPairedProperty -> it.constructorSlot.parameterIndex
                is LocalPropertyInformation.PrivateConstructorPairedProperty -> it.constructorSlot.parameterIndex
                else -> null
            }
        }.toSet()

        return (0 until constructorInformation.parameters.size).none { index ->
            constructorInformation.parameters[index].isMandatory && index !in indicesAddressedByProperties
        }
    }

    private fun buildSuperclassInformation(type: Type): LocalTypeInformation =
        resolveAndBuild(type.asClass().genericSuperclass)

    private fun buildInterfaceInformation(type: Type) =
            type.allInterfaces.asSequence().mapNotNull {
                if (it == type) return@mapNotNull null
                resolveAndBuild(it)
            }.toList()

    private val Type.allInterfaces: Set<Type> get() = exploreType(this)

    private fun exploreType(type: Type, interfaces: MutableSet<Type> = LinkedHashSet()): MutableSet<Type> {
        val clazz = type.asClass()

        if (clazz.isInterface) {
            // Ignore classes we've already seen, and stop exploring once we reach an excluded type.
            if (clazz in interfaces || lookup.isExcluded(clazz)) return interfaces
            else interfaces += type
        }

        clazz.genericInterfaces.forEach { exploreType(it.resolveAgainstContext(), interfaces) }
        if (clazz.genericSuperclass != null) exploreType(clazz.genericSuperclass.resolveAgainstContext(), interfaces)

        return interfaces
    }

    private fun buildReadOnlyProperties(rawType: Class<*>): Map<PropertyName, LocalPropertyInformation> =
            rawType.propertyDescriptors().asSequence().mapNotNull { (name, descriptor) ->
                if (descriptor.field == null || descriptor.getter == null) null
                else {
                    val paramType = (descriptor.getter.genericReturnType).resolveAgainstContext()
                    val paramTypeInformation = build(paramType, TypeIdentifier.forGenericType(paramType, resolutionContext
                            ?: rawType))
                    val isMandatory = paramType.asClass().isPrimitive || !descriptor.getter.returnsNullable()
                    name to LocalPropertyInformation.ReadOnlyProperty(descriptor.getter, paramTypeInformation, isMandatory)
                }
            }.sortedBy { (name, _) -> name }.toMap(LinkedHashMap())

    private fun buildObjectProperties(rawType: Class<*>, constructorInformation: LocalConstructorInformation): Map<PropertyName, LocalPropertyInformation> =
            (calculatedProperties(rawType) + nonCalculatedProperties(rawType, constructorInformation))
                    .sortedBy { (name, _) -> name }
                    .toMap(LinkedHashMap())

    private fun nonCalculatedProperties(rawType: Class<*>, constructorInformation: LocalConstructorInformation): Sequence<Pair<String, LocalPropertyInformation>> =
            if (constructorInformation.hasParameters) getConstructorPairedProperties(constructorInformation, rawType)
            else getterSetterProperties(rawType)

    private fun getConstructorPairedProperties(constructorInformation: LocalConstructorInformation, rawType: Class<*>): Sequence<Pair<String, LocalPropertyInformation>> {
        val constructorParameterIndices = constructorInformation.parameters.asSequence().mapIndexed { index, parameter ->
            parameter.name to index
        }.toMap()

        return rawType.propertyDescriptors().asSequence().mapNotNull { (name, descriptor) ->
            val property = makeConstructorPairedProperty(constructorParameterIndices, name, descriptor, constructorInformation)
            if (property == null) null else name to property
        }
    }

    private fun makeConstructorPairedProperty(constructorParameterIndices: Map<String, Int>,
                                              name: String,
                                              descriptor: PropertyDescriptor,
                                              constructorInformation: LocalConstructorInformation): LocalPropertyInformation? {
        val constructorIndex = constructorParameterIndices[name] ?: return null
        val isMandatory = constructorInformation.parameters[constructorIndex].isMandatory
        if (descriptor.field == null) return null

        if (descriptor.getter == null) {
            val paramType = descriptor.field.genericType
            val paramTypeInformation = resolveAndBuild(paramType)

            return LocalPropertyInformation.PrivateConstructorPairedProperty(
                    descriptor.field,
                    ConstructorSlot(constructorIndex, constructorInformation),
                    paramTypeInformation,
                    isMandatory)
        }

        val paramType = descriptor.getter.genericReturnType
        val paramTypeInformation = resolveAndBuild(paramType)

        return LocalPropertyInformation.ConstructorPairedProperty(
                descriptor.getter,
                ConstructorSlot(constructorIndex, constructorInformation),
                paramTypeInformation,
                isMandatory)
    }

    private fun getterSetterProperties(rawType: Class<*>): Sequence<Pair<String, LocalPropertyInformation>> =
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
            }

    private fun calculatedProperties(rawType: Class<*>): Sequence<Pair<String, LocalPropertyInformation>> =
            rawType.calculatedPropertyDescriptors().asSequence().map { (name, v) ->
                val paramType = v.getter!!.genericReturnType
                val paramTypeInformation = resolveAndBuild(paramType)
                val isMandatory = paramType.asClass().isPrimitive || !v.getter.returnsNullable()

                name to LocalPropertyInformation.CalculatedProperty(v.getter, paramTypeInformation, isMandatory)
            }

    private fun buildTypeParameterInformation(type: ParameterizedType): List<LocalTypeInformation> =
            type.actualTypeArguments.map {
                resolveAndBuild(it)
            }

    private fun buildConstructorInformation(type: Type, observedConstructor: KFunction<Any>): LocalConstructorInformation {
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

private fun Method.returnsNullable(): Boolean = try {
    val returnTypeString = this.declaringClass.kotlin.memberProperties.firstOrNull {
        it.javaGetter == this
    }?.returnType?.toString() ?: "?"

    returnTypeString.endsWith('?') || returnTypeString.endsWith('!')
} catch (e: KotlinReflectionInternalError) {
    // This might happen for some types, e.g. kotlin.Throwable? - the root cause of the issue
    // is: https://youtrack.jetbrains.com/issue/KT-13077
    // TODO: Revisit this when Kotlin issue is fixed.

    true
}

/**
 * Code for finding the unique constructor we will use for deserialization.
 *
 * If any constructor is uniquely annotated with [@ConstructorForDeserialization], then that constructor is chosen.
 * An error is reported if more than one constructor is annotated.
 *
 * Otherwise, if there is a Kotlin primary constructor, it selects that, and if not it selects either the unique
 * constructor or, if there are two and one is the default no-argument constructor, the non-default constructor.
 */
private fun constructorForDeserialization(type: Type): KFunction<Any>? {
    val clazz = type.asClass()
    if (!clazz.isConcreteClass || clazz.isSynthetic) return null

    val kotlinCtors = clazz.kotlin.constructors

    val annotatedCtors = kotlinCtors.filter { it.findAnnotation<ConstructorForDeserialization>() != null }
    if (annotatedCtors.size > 1) return null
    if (annotatedCtors.size == 1) return annotatedCtors.first().apply { isAccessible = true }

    val defaultCtor = kotlinCtors.firstOrNull { it.parameters.isEmpty() }
    val nonDefaultCtors = kotlinCtors.filter { it != defaultCtor }

    val preferredCandidate = clazz.kotlin.primaryConstructor ?:
        when(nonDefaultCtors.size) {
            1 -> nonDefaultCtors.first()
            0 -> defaultCtor
            else -> null
        }

    return preferredCandidate?.apply { isAccessible = true }
}