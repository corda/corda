package net.corda.serialization.internal.model

import net.corda.core.internal.isAbstractClass
import net.corda.core.internal.isConcreteClass
import net.corda.core.internal.kotlinObjectInstance
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.serialization.internal.NotSerializableDetailedException
import net.corda.serialization.internal.amqp.*
import net.corda.serialization.internal.model.LocalTypeInformation.Abstract
import net.corda.serialization.internal.model.LocalTypeInformation.AnArray
import net.corda.serialization.internal.model.LocalTypeInformation.AnEnum
import net.corda.serialization.internal.model.LocalTypeInformation.AnInterface
import net.corda.serialization.internal.model.LocalTypeInformation.Atomic
import net.corda.serialization.internal.model.LocalTypeInformation.ACollection
import net.corda.serialization.internal.model.LocalTypeInformation.AMap
import net.corda.serialization.internal.model.LocalTypeInformation.Composable
import net.corda.serialization.internal.model.LocalTypeInformation.Cycle
import net.corda.serialization.internal.model.LocalTypeInformation.NonComposable
import net.corda.serialization.internal.model.LocalTypeInformation.Opaque
import net.corda.serialization.internal.model.LocalTypeInformation.Singleton
import net.corda.serialization.internal.model.LocalTypeInformation.Top
import net.corda.serialization.internal.model.LocalTypeInformation.Unknown
import java.io.NotSerializableException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
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
internal data class LocalTypeInformationBuilder(val lookup: LocalTypeLookup,
                                                var resolutionContext: Type? = null,
                                                var visited: Set<TypeIdentifier> = emptySet(),
                                                val cycles: MutableList<Cycle> = mutableListOf(),
                                                var validateProperties: Boolean = true) {
    private val baseTypes = lookup.baseTypes

    /**
     * If we are examining the type of a read-only property, or a type flagged as [Opaque], then we do not need to warn
     * if the [LocalTypeInformation] for that type (or any of its related types) is [LocalTypeInformation.NonComposable].
     */
    private inline fun <T> suppressValidation(block: LocalTypeInformationBuilder.() -> T): T {
        val previous = validateProperties
        return try {
            validateProperties = false
            this.block()
        } finally {
            validateProperties = previous
        }
    }

    /**
     * Recursively build [LocalTypeInformation] for the given [Type] and [TypeIdentifier]
     */
    fun build(type: Type, typeIdentifier: TypeIdentifier): LocalTypeInformation =
            if (typeIdentifier in visited) Cycle(type, typeIdentifier).apply { cycles.add(this) }
            else lookup.findOrBuild(type, typeIdentifier) { isOpaque ->
                val previous = visited
                try {
                    visited = visited + typeIdentifier
                    buildIfNotFound(type, typeIdentifier, isOpaque)
                } finally {
                    visited = previous
                }
            }

    private fun resolveAndBuild(type: Type): LocalTypeInformation {
        val resolved = type.resolveAgainstContext()
        return build(resolved, TypeIdentifier.forGenericType(resolved, resolutionContext
                ?: type))
    }

    private fun Type.resolveAgainstContext(): Type =
            resolutionContext?.let(::resolveAgainst) ?: this

    private fun buildIfNotFound(type: Type, typeIdentifier: TypeIdentifier, isOpaque: Boolean): LocalTypeInformation {
        val rawType = type.asClass()
        return when (typeIdentifier) {
            is TypeIdentifier.TopType -> Top
            is TypeIdentifier.UnknownType -> Unknown
            is TypeIdentifier.Unparameterised,
            is TypeIdentifier.Erased -> buildForClass(rawType, typeIdentifier, isOpaque)
            is TypeIdentifier.ArrayOf -> {
                AnArray(
                    type,
                    typeIdentifier,
                    resolveAndBuild(type.componentType())
                )
            }
            is TypeIdentifier.Parameterised -> buildForParameterised(rawType, type as ParameterizedType, typeIdentifier, isOpaque)
        }
    }

    private fun buildForClass(type: Class<*>, typeIdentifier: TypeIdentifier, isOpaque: Boolean): LocalTypeInformation = withContext(type) {
        when {
            baseTypes.collectionClass.isAssignableFrom(type) &&
                    !baseTypes.enumSetClass.isAssignableFrom(type) -> ACollection(type, typeIdentifier, Unknown)
            baseTypes.mapClass.isAssignableFrom(type) -> AMap(type, typeIdentifier, Unknown, Unknown)
            type === baseTypes.stringClass -> Atomic(type, typeIdentifier)
            type.kotlin.javaPrimitiveType != null -> Atomic(type, typeIdentifier)
            baseTypes.isEnum.test(type) -> baseTypes.enumConstantNames.apply(type).let { enumConstantNames ->
                AnEnum(
                    type,
                    typeIdentifier,
                    enumConstantNames,
                    /**
                     * Calculate "fallbacks" for any [Enum] incorrectly serialised
                     * as its [Enum.toString] value. We are only interested in the
                     * cases where these are different from [Enum.name].
                     * These fallbacks DO NOT contribute to this type's fingerprint.
                     */
                    baseTypes.enumConstants.apply(type).map(Any::toString).mapIndexed { ord, fallback ->
                        fallback to enumConstantNames[ord]
                    }.filterNot { it.first == it.second }.toMap(),
                    buildInterfaceInformation(type),
                    getEnumTransforms(type, enumConstantNames)
                )
            }
            type.kotlinObjectInstance != null -> Singleton(
                    type,
                    typeIdentifier,
                    buildSuperclassInformation(type),
                    buildInterfaceInformation(type))
            type.isInterface -> buildInterface(type, typeIdentifier, emptyList())
            type.isAbstractClass -> buildAbstract(type, typeIdentifier, emptyList())
            isOpaque -> Opaque(
                    type,
                    typeIdentifier,
                    suppressValidation { buildNonAtomic(type, type, typeIdentifier, emptyList()) })
            baseTypes.exceptionClass.isAssignableFrom(type.asClass()) -> suppressValidation {
                buildNonAtomic(type, type, typeIdentifier, emptyList())
            }
            else -> buildNonAtomic(type, type, typeIdentifier, emptyList())
        }
    }

    private fun getEnumTransforms(type: Class<*>, enumConstants: List<String>): EnumTransforms {
        try {
            val constants = enumConstants.asSequence().mapIndexed { index, constant -> constant to index }.toMap()
            return EnumTransforms.build(TransformsAnnotationProcessor.getEnumTransformsSchema(type), constants)
        } catch (e: InvalidEnumTransformsException) {
            throw NotSerializableDetailedException(type.name, e.message!!)
        }
    }

    private fun buildForParameterised(
            rawType: Class<*>,
            type: ParameterizedType,
            typeIdentifier: TypeIdentifier.Parameterised,
            isOpaque: Boolean): LocalTypeInformation = withContext(type) {
        when {
            baseTypes.collectionClass.isAssignableFrom(rawType) &&
                    !baseTypes.enumSetClass.isAssignableFrom(rawType) ->
                ACollection(type, typeIdentifier, buildTypeParameterInformation(type)[0])
            baseTypes.mapClass.isAssignableFrom(rawType) -> {
                val (keyType, valueType) = buildTypeParameterInformation(type)
                AMap(type, typeIdentifier, keyType, valueType)
            }
            rawType.isInterface -> buildInterface(type, typeIdentifier, buildTypeParameterInformation(type))
            rawType.isAbstractClass -> buildAbstract(type, typeIdentifier, buildTypeParameterInformation(type))
            isOpaque -> Opaque(rawType,
                    typeIdentifier,
                    suppressValidation { buildNonAtomic(rawType, type, typeIdentifier, buildTypeParameterInformation(type)) })
            else -> buildNonAtomic(rawType, type, typeIdentifier, buildTypeParameterInformation(type))
        }
    }

    private fun buildAbstract(type: Type, typeIdentifier: TypeIdentifier,
                              typeParameters: List<LocalTypeInformation>): Abstract =
            Abstract(
                type,
                typeIdentifier,
                buildReadOnlyProperties(type.asClass()),
                buildSuperclassInformation(type),
                buildInterfaceInformation(type),
                typeParameters
            )

    private fun buildInterface(type: Type, typeIdentifier: TypeIdentifier,
                               typeParameters: List<LocalTypeInformation>): AnInterface =
            AnInterface(
                type,
                typeIdentifier,
                buildReadOnlyProperties(type.asClass()),
                buildInterfaceInformation(type),
                typeParameters
            )

    private inline fun <T> withContext(newContext: Type, block: LocalTypeInformationBuilder.() -> T): T {
        val previous = resolutionContext
        return try {
            resolutionContext = newContext
            this.block()
        } finally {
            resolutionContext = previous
        }
    }

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
        val observedConstructor = constructorForDeserialization(type) ?: return NonComposable(
            observedType = type,
            typeIdentifier = typeIdentifier,
            constructor = null,
            properties = if (rawType === Class::class.java) {
                // Do NOT drill down into the internals of java.lang.Class.
                emptyMap()
            } else {
                buildReadOnlyProperties(rawType)
            },
            superclass = superclassInformation,
            interfaces = interfaceInformation,
            typeParameters = typeParameterInformation,
            nonComposableSubtypes = emptySet(),
            reason = "No unique deserialization constructor can be identified",
            remedy = "Either annotate a constructor for this type with @ConstructorForDeserialization, or provide a custom serializer for it"
        )

        val constructorInformation = buildConstructorInformation(type, observedConstructor)
        val properties = buildObjectProperties(rawType, constructorInformation)

        if (!propertiesSatisfyConstructor(constructorInformation, properties)) {
            val missingConstructorProperties = missingMandatoryConstructorProperties(constructorInformation, properties)
            val missingParameters = missingConstructorProperties.map(LocalConstructorParameterInformation::name)
            return NonComposable(
                observedType = type,
                typeIdentifier = typeIdentifier,
                constructor = constructorInformation,
                properties = properties,
                superclass = superclassInformation,
                interfaces = interfaceInformation,
                typeParameters = typeParameterInformation,
                nonComposableSubtypes = missingConstructorProperties
                    .filterIsInstanceTo(LinkedHashSet(), NonComposable::class.java),
                reason = "Mandatory constructor parameters $missingParameters are missing from the readable properties ${properties.keys}",
                remedy = "Either provide getters or readable fields for $missingParameters, or provide a custom serializer for this type"
            )
        }

        val nonComposableProperties = properties.filterValues { it.type is NonComposable }

        if (nonComposableProperties.isNotEmpty()) {
            return NonComposable(
                observedType = type,
                typeIdentifier = typeIdentifier,
                constructor = constructorInformation,
                properties = properties,
                superclass = superclassInformation,
                interfaces = interfaceInformation,
                typeParameters = typeParameterInformation,
                nonComposableSubtypes = nonComposableProperties.values.mapTo(LinkedHashSet()) {
                    it.type as NonComposable
                },
                reason = nonComposablePropertiesErrorReason(nonComposableProperties),
                remedy = "Either ensure that the properties ${nonComposableProperties.keys} are serializable, or provide a custom serializer for this type"
            )
        }

        val evolutionConstructors = evolutionConstructors(type).map { ctor ->
            val evolutionConstructorInformation = buildConstructorInformation(type, ctor)
            val evolutionProperties = buildObjectProperties(rawType, evolutionConstructorInformation)
            EvolutionConstructorInformation(evolutionConstructorInformation, evolutionProperties)
        }

        return Composable(type, typeIdentifier, constructorInformation, evolutionConstructors, properties,
                superclassInformation, interfaceInformation, typeParameterInformation)
    }

    // Can we supply all of the mandatory constructor parameters using values addressed by readable properties?
    private fun propertiesSatisfyConstructor(constructorInformation: LocalConstructorInformation, properties: Map<PropertyName, LocalPropertyInformation>): Boolean {
        if (!constructorInformation.hasParameters) return true

        val indicesAddressedByProperties = properties.values.asSequence().mapNotNullTo(LinkedHashSet()) {
            when (it) {
                is LocalPropertyInformation.ConstructorPairedProperty -> it.constructorSlot.parameterIndex
                is LocalPropertyInformation.PrivateConstructorPairedProperty -> it.constructorSlot.parameterIndex
                else -> null
            }
        }

        return (constructorInformation.parameters.indices).none { index ->
            constructorInformation.parameters[index].isMandatory && index !in indicesAddressedByProperties
        }
    }

    private fun missingMandatoryConstructorProperties(
        constructorInformation: LocalConstructorInformation,
        properties: Map<PropertyName, LocalPropertyInformation>
    ): List<LocalConstructorParameterInformation> {
        if (!constructorInformation.hasParameters) return emptyList()

        val indicesAddressedByProperties = properties.values.asSequence().mapNotNullTo(LinkedHashSet()) {
            when (it) {
                is LocalPropertyInformation.ConstructorPairedProperty -> it.constructorSlot.parameterIndex
                is LocalPropertyInformation.PrivateConstructorPairedProperty -> it.constructorSlot.parameterIndex
                else -> null
            }
        }

        return (constructorInformation.parameters.indices).mapNotNull { index ->
            val parameter = constructorInformation.parameters[index]
            when {
                constructorInformation.parameters[index].isMandatory && index !in indicesAddressedByProperties -> parameter
                else -> null
            }
        }
    }

    private fun nonComposablePropertiesErrorReason(nonComposableProperties: Map<PropertyName, LocalPropertyInformation>): String {
        val reasons = nonComposableProperties.entries.joinToString("\n") { (key, value) ->
            "$key [${value.type.observedType}]: ${(value.type as NonComposable).reason}"
                .replace("\n", "\n    ")
        }
        return "Has properties ${nonComposableProperties.keys} of types that are not serializable:\n" + reasons
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
            rawType.propertyDescriptors(validateProperties).asSequence().mapNotNull { (name, descriptor) ->
                if (descriptor.field == null || descriptor.getter == null) null
                else {
                    val paramType = (descriptor.getter.genericReturnType).resolveAgainstContext()
                    // Because this parameter is read-only, we don't need to warn if its type is non-composable.
                    val paramTypeInformation = suppressValidation {
                        build(paramType, TypeIdentifier.forGenericType(paramType, resolutionContext ?: rawType))
                    }
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

        return rawType.propertyDescriptors(validateProperties).asSequence().mapNotNull { (name, descriptor) ->
            val normalisedName = when {
                name in constructorParameterIndices -> name
                name.decapitalize() in constructorParameterIndices -> name.decapitalize()
                else -> return@mapNotNull null
            }

            val property = makeConstructorPairedProperty(
                    constructorParameterIndices.getValue(normalisedName),
                    descriptor,
                    constructorInformation)
            if (property == null) null else normalisedName to property
        }
    }

    private fun makeConstructorPairedProperty(constructorIndex: Int,
                                              descriptor: PropertyDescriptor,
                                              constructorInformation: LocalConstructorInformation): LocalPropertyInformation? {

        if (descriptor.getter == null) {
            if (descriptor.field == null) return null
            val paramType = descriptor.field.genericType
            val paramTypeInformation = resolveAndBuild(paramType)

            return LocalPropertyInformation.PrivateConstructorPairedProperty(
                    descriptor.field,
                    ConstructorSlot(constructorIndex, constructorInformation),
                    paramTypeInformation,
                    constructorInformation.parameters[constructorIndex].isMandatory)
        }

        val paramType = descriptor.getter.genericReturnType
        val paramTypeInformation = resolveAndBuild(paramType)

        return LocalPropertyInformation.ConstructorPairedProperty(
                descriptor.getter,
                ConstructorSlot(constructorIndex, constructorInformation),
                paramTypeInformation,
                descriptor.getter.returnType.isPrimitive ||
                        !descriptor.getter.returnsNullable())
    }

    private fun getterSetterProperties(rawType: Class<*>): Sequence<Pair<String, LocalPropertyInformation>> =
            rawType.propertyDescriptors(validateProperties).asSequence().mapNotNull { (name, descriptor) ->
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

        return LocalConstructorInformation(
                observedConstructor.javaConstructor!!.apply { isAccessible = true },
                observedConstructor.parameters.map {
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
    } ?: return null

    return try {
        preferredCandidate.apply { isAccessible = true }
    } catch (e: SecurityException) {
        null
    }
}

/**
 * Obtain evolution constructors in ascending version order.
 */
private fun evolutionConstructors(type: Type): List<KFunction<Any>> {
    val clazz = type.asClass()
    if (!clazz.isConcreteClass || clazz.isSynthetic) return emptyList()

    return clazz.kotlin.constructors.asSequence()
            .mapNotNull {
                val version = it.findAnnotation<DeprecatedConstructorForDeserialization>()?.version
                if (version == null) null else version to it
            }
            .sortedBy { (version, _) -> version }
            .map { (_, ctor) -> ctor.apply { isAccessible = true} }
            .toList()
}