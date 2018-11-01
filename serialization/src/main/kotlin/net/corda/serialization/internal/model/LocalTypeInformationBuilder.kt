package net.corda.serialization.internal.model

import net.corda.core.internal.isAbstractClass
import net.corda.core.internal.isConcreteClass
import net.corda.core.internal.kotlinObjectInstance
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.serialization.internal.amqp.asClass
import net.corda.serialization.internal.amqp.calculatedPropertyDescriptors
import net.corda.serialization.internal.amqp.componentType
import net.corda.serialization.internal.amqp.propertyDescriptors
import java.io.NotSerializableException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
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

    /**
     * Recursively build [LocalTypeInformation] for the given [Type] and [TypeIdentifier]
     */
    fun build(type: Type, typeIdentifier: TypeIdentifier): LocalTypeInformation =
        if (typeIdentifier in visited) LocalTypeInformation.Cycle(type, typeIdentifier)
        else lookup.lookup(type, typeIdentifier) {
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
            rawType.isCollectionOrMap -> LocalTypeInformation.ACollection(type, typeIdentifier, buildTypeParameterInformation(type))
            rawType.isInterface -> buildInterface(rawType, typeIdentifier, buildTypeParameterInformation(type))
            rawType.isAbstractClass -> buildAbstract(rawType, typeIdentifier, buildTypeParameterInformation(type))
            else -> buildNonAtomic(rawType, type, typeIdentifier, buildTypeParameterInformation(type))
        }
    }

    private fun buildAbstract(type: Class<*>, typeIdentifier: TypeIdentifier,
                              typeParameters: List<LocalTypeInformation>): LocalTypeInformation.Abstract =
            LocalTypeInformation.Abstract(
                    type,
                    typeIdentifier,
                    buildReadOnlyProperties(type),
                    buildSuperclassInformation(type),
                    buildInterfaceInformation(type),
                    typeParameters)

    private fun buildInterface(type: Class<*>, typeIdentifier: TypeIdentifier,
                               typeParameters: List<LocalTypeInformation>): LocalTypeInformation.AnInterface =
            LocalTypeInformation.AnInterface(
                    type,
                    typeIdentifier,
                    buildReadOnlyProperties(type),
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
                ?: return LocalTypeInformation.NonComposable(type, typeIdentifier, buildReadOnlyProperties(rawType),
                        superclassInformation, interfaceInformation, typeParameterInformation)

        val constructorInformation = buildConstructorInformation(type, observedConstructor)
        val properties = buildObjectProperties(rawType, constructorInformation)

        if (properties.values.any { it.type is LocalTypeInformation.NonComposable }) {
            return LocalTypeInformation.NonComposable(type, typeIdentifier, properties, superclassInformation,
                    interfaceInformation, typeParameterInformation)
        }

        return LocalTypeInformation.Composable(rawType, typeIdentifier, constructorInformation, properties,
                superclassInformation, interfaceInformation, typeParameterInformation)
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

        (clazz.genericInterfaces.asSequence() + clazz.genericSuperclass)
                .filterNotNull()
                .forEach { exploreType(it.resolveAgainstContext(), interfaces) }

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
            }.toMap()

    private fun buildObjectProperties(rawType: Class<*>, constructorInformation: LocalConstructorInformation): Map<PropertyName, LocalPropertyInformation> =
            calculatedProperties(rawType) + nonCalculatedProperties(rawType, constructorInformation)

    private fun nonCalculatedProperties(rawType: Class<*>, constructorInformation: LocalConstructorInformation): Map<String, LocalPropertyInformation> =
            if (constructorInformation.parameters.isEmpty()) getterSetterProperties(rawType)
            else getConstructorPairedProperties(constructorInformation, rawType)

    private fun getConstructorPairedProperties(constructorInformation: LocalConstructorInformation, rawType: Class<*>): Map<String, LocalPropertyInformation.ConstructorPairedProperty> {
        val constructorParameterIndices = constructorInformation.parameters.asSequence().mapIndexed { index, parameter ->
            parameter.name to index
        }.toMap()

        return rawType.propertyDescriptors().asSequence().mapNotNull { (name, descriptor) ->
            val constructorIndex = constructorParameterIndices[name] ?: return@mapNotNull null
            if (descriptor.field == null || descriptor.getter == null) return@mapNotNull null

            val paramType = descriptor.getter.genericReturnType
            val paramTypeInformation = resolveAndBuild(paramType)
            val isMandatory = paramType.asClass().isPrimitive || !descriptor.getter.returnsNullable()

            name to LocalPropertyInformation.ConstructorPairedProperty(
                    descriptor.getter,
                    ConstructorSlot(constructorIndex, constructorInformation),
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
} catch (e: kotlin.reflect.jvm.internal.KotlinReflectionInternalError) {
    // This might happen for some types, e.g. kotlin.Throwable? - the root cause of the issue
    // is: https://youtrack.jetbrains.com/issue/KT-13077
    // TODO: Revisit this when Kotlin issue is fixed.

    true
}

internal val Class<*>.isCollectionOrMap
    get() = (Collection::class.java.isAssignableFrom(this) || Map::class.java.isAssignableFrom(this))
            && !EnumSet::class.java.isAssignableFrom(this)

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
    if (!clazz.isConcreteClass) return null

    val kotlinCtors = clazz.kotlin.constructors

    val annotatedCtors = kotlinCtors.filter { it.findAnnotation<ConstructorForDeserialization>() != null }
    if (annotatedCtors.size > 1) return null // TODO: we should probably log a warning if this is the case.

    val defaultCtor = kotlinCtors.firstOrNull { it.parameters.isEmpty() }
    val nonDefaultCtors = kotlinCtors.filter { it != defaultCtor }

    val preferredCandidate = annotatedCtors.firstOrNull() ?:
    clazz.kotlin.primaryConstructor ?:
    when(nonDefaultCtors.size) {
        1 -> nonDefaultCtors.first()
        0 -> defaultCtor
        else -> null
    }

    return preferredCandidate?.apply { isAccessible = true }
}