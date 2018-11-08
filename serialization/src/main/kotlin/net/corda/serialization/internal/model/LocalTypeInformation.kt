package net.corda.serialization.internal.model

import java.lang.reflect.*
import kotlin.reflect.KFunction
import java.util.*

typealias PropertyName = String

/**
 * The [LocalTypeInformation] captured for a [Type] gathers together everything that can be ascertained about the type
 * through runtime reflection, in the form of a directed acyclic graph (DAG) of types and relationships between types.
 *
 * Types can be related in the following ways:
 *
 * * Type A is the type of a _property_ of type B.
 * * Type A is the type of an _interface_ of type B.
 * * Type A is the type of the _superclass_ of type B.
 * * Type A is the type of a _type parameter_ of type B.
 * * Type A is an _array type_, of which type B is the _component type_.
 *
 * All of these relationships are represented by references and collections held by the objects representing the nodes
 * themselves.
 *
 * A type is [Composable] if it is isomorphic to a dictionary of its property values, i.e. if we can obtain an instance
 * of the type from a dictionary containing typed key/value pairs corresponding to its properties, and a dictionary from
 * an instance of the type, and can round-trip (in both directions) between these representations without losing
 * information. This is the basis for compositional serialization, i.e. building a serializer for a type out of the
 * serializers we have for its property types.
 *
 * A type is [Atomic] if it cannot be decomposed or recomposed in this fashion (usually because it is the type of a
 * scalar value of some sort, such as [Int]), and [Opaque] if we have chosen not to investigate its composability,
 * typically because it is handled by a custom serializer.
 *
 * Abstract types are represented by [AnInterface] and [Abstract], the difference between them being that an [Abstract]
 * type may have a superclass.
 *
 * If a concrete type does not have a unique deserialization constructor, it is represented by [NonComposable], meaning
 * that we know how to take it apart but do not know how to put it back together again.
 *
 * An array of any type is represented by [ArrayOf]. Enums are represented by [AnEnum].
 *
 * The type of [Any]/[java.lang.Object] is represented by [Top]. Unbounded wildcards, or wildcards whose upper bound is
 * [Top], are represented by [Unknown]. Bounded wildcards are always resolved to their upper bounds, e.g.
 * `List<? extends String>` becomes `List<String>`.
 *
 * If we encounter a cycle while traversing the DAG, the type on which traversal detected the cycle is represented by
 * [Cycle], and no further traversal is attempted from that type. Kotlin objects are represented by [Singleton].
 */
sealed class LocalTypeInformation {

    companion object {
        /**
         * Using the provided [LocalTypeLookup] to record and locate already-visited nodes, traverse the DAG of related
         * types beginning the with provided [Type] and construct a complete set of [LocalTypeInformation] for that type.
         *
         * @param type The [Type] to obtain [LocalTypeInformation] for.
         * @param lookup The [LocalTypeLookup] to use to find previously-constructed [LocalTypeInformation].
         */
        fun forType(type: Type, lookup: LocalTypeLookup): LocalTypeInformation =
                LocalTypeInformationBuilder(lookup).build(type, TypeIdentifier.forGenericType(type))
    }

    /**
     * The actual type which was observed when constructing this type information.
     */
    abstract val observedType: Type

    /**
     * The [TypeIdentifier] for the type represented by this type information, used to cross-reference with
     * [RemoteTypeInformation].
     */
    abstract val typeIdentifier: TypeIdentifier

    /**
     * Obtain a multi-line, recursively-indented representation of this type information.
     *
     * @param simplifyClassNames By default, class names are printed as their "simple" class names, i.e. "String" instead
     * of "java.lang.String". If this is set to `false`, then the full class name will be printed instead.
     */
    fun prettyPrint(simplifyClassNames: Boolean = true): String =
            LocalTypeInformationPrettyPrinter(simplifyClassNames).prettyPrint(this)

    /**
     * The [LocalTypeInformation] corresponding to an unbounded wildcard ([TypeIdentifier.UnknownType])
     */
    object Unknown : LocalTypeInformation() {
        override val observedType get() = TypeIdentifier.UnknownType.getLocalType()
        override val typeIdentifier get() = TypeIdentifier.UnknownType
    }

    /**
     * The [LocalTypeInformation] corresponding to [java.lang.Object] / [Any] ([TypeIdentifier.TopType])
     */
    object Top : LocalTypeInformation() {
        override val observedType get() = TypeIdentifier.TopType.getLocalType()
        override val typeIdentifier get() = TypeIdentifier.TopType
    }

    /**
     * The [LocalTypeInformation] emitted if we hit a cycle while traversing the graph of related types.
     */
    data class Cycle(
            override val observedType: Type,
            override val typeIdentifier: TypeIdentifier,
            private val _follow: () -> LocalTypeInformation) : LocalTypeInformation() {
        val follow: LocalTypeInformation get() = _follow()

        // Custom equals / hashcode because otherwise the "follow" lambda makes equality harder to reason about.
        override fun equals(other: Any?): Boolean =
                other is Cycle &&
                        other.observedType == observedType &&
                        other.typeIdentifier == typeIdentifier

        override fun hashCode(): Int = Objects.hash(observedType, typeIdentifier)

        override fun toString(): String = "Cycle($observedType, $typeIdentifier)"
    }

    /**
     * May in fact be a more complex class, but is treated as if atomic, i.e. we don't further expand its properties.
     */
    data class Opaque(override val observedType: Class<*>, override val typeIdentifier: TypeIdentifier) : LocalTypeInformation()

    /**
     * Represents a scalar type such as [Int].
     */
    data class Atomic(override val observedType: Class<*>, override val typeIdentifier: TypeIdentifier) : LocalTypeInformation()

    /**
     * Represents an array of some other type.
     *
     * @param componentType The [LocalTypeInformation] for the component type of the array (e.g. [Int], if the type is [IntArray])
     */
    data class AnArray(override val observedType: Type, override val typeIdentifier: TypeIdentifier, val componentType: LocalTypeInformation) : LocalTypeInformation()

    /**
     * Represents an `enum`
     *
     * @param members The string names of the members of the enum.
     * @param superclass [LocalTypeInformation] for the superclass of the type (as enums can inherit from other types).
     * @param interfaces [LocalTypeInformation] for each interface implemented by the type.
     */
    data class AnEnum(
            override val observedType: Class<*>,
            override val typeIdentifier: TypeIdentifier,
            val members: List<String>,
            val interfaces: List<LocalTypeInformation>): LocalTypeInformation()

    /**
     * Represents a type whose underlying class is an interface.
     *
     * @param properties [LocalPropertyInformation] for the read-only properties of the interface, i.e. its "getter" methods.
     * @param interfaces [LocalTypeInformation] for the interfaces extended by this interface.
     * @param typeParameters [LocalTypeInformation] for the resolved type parameters of the type.
     */
    data class AnInterface(
            override val observedType: Type,
            override val typeIdentifier: TypeIdentifier,
            val properties: Map<PropertyName, LocalPropertyInformation>,
            val interfaces: List<LocalTypeInformation>,
            val typeParameters: List<LocalTypeInformation>) : LocalTypeInformation()

    /**
     * Represents a type whose underlying class is abstract.
     *
     * @param properties [LocalPropertyInformation] for the read-only properties of the interface, i.e. its "getter" methods.
     * @param superclass [LocalTypeInformation] for the superclass of the underlying class of this type.
     * @param interfaces [LocalTypeInformation] for the interfaces extended by this interface.
     * @param typeParameters [LocalTypeInformation] for the resolved type parameters of the type.
     */
    data class Abstract(
            override val observedType: Type,
            override val typeIdentifier: TypeIdentifier,
            val properties: Map<PropertyName, LocalPropertyInformation>,
            val superclass: LocalTypeInformation,
            val interfaces: List<LocalTypeInformation>,
            val typeParameters: List<LocalTypeInformation>) : LocalTypeInformation()

    /**
     * Represents a type which has only a single instantiation, e.g. a Kotlin `object`.
     *
     * @param superclass [LocalTypeInformation] for the superclass of the underlying class of this type.
     * @param interfaces [LocalTypeInformation] for the interfaces extended by this interface.
     */
    data class Singleton(override val observedType: Type, override val typeIdentifier: TypeIdentifier, val superclass: LocalTypeInformation, val interfaces: List<LocalTypeInformation>) : LocalTypeInformation()

    /**
     * Represents a type whose instances can be reversibly decomposed into dictionaries of typed values.
     *
     * @param constructor [LocalConstructorInformation] for the constructor used when building instances of this type
     * out of dictionaries of typed values.
     * @param properties [LocalPropertyInformation] for the properties of the interface.
     * @param superclass [LocalTypeInformation] for the superclass of the underlying class of this type.
     * @param interfaces [LocalTypeInformation] for the interfaces extended by this interface.
     * @param typeParameters [LocalTypeInformation] for the resolved type parameters of the type.
     */
    data class Composable(
            override val observedType: Type,
            override val typeIdentifier: TypeIdentifier,
            val constructor: LocalConstructorInformation,
            val properties: Map<PropertyName, LocalPropertyInformation>,
            val superclass: LocalTypeInformation,
            val interfaces: List<LocalTypeInformation>,
            val typeParameters: List<LocalTypeInformation>) : LocalTypeInformation()

    /**
     * Represents a type whose instances may have observable properties (represented by "getter" methods), but for which
     * we do not possess a method (such as a unique "deserialization constructor") for creating a new instance from a
     * dictionary of property values.
     *
     * @param properties [LocalPropertyInformation] for the properties of the interface.
     * @param superclass [LocalTypeInformation] for the superclass of the underlying class of this type.
     * @param interfaces [LocalTypeInformation] for the interfaces extended by this interface.
     * @param typeParameters [LocalTypeInformation] for the resolved type parameters of the type.
     */
    data class NonComposable(
            override val observedType: Type,
            override val typeIdentifier: TypeIdentifier,
            val properties: Map<PropertyName, LocalPropertyInformation>,
            val superclass: LocalTypeInformation,
            val interfaces: List<LocalTypeInformation>,
            val typeParameters: List<LocalTypeInformation>) : LocalTypeInformation()

    /**
     * Represents a type whose underlying class is a collection class such as [List] with a single type parameter.
     *
     * @param elementType [LocalTypeInformation] for the resolved type parameter of the type, i.e. the type of its
     * elements. [Unknown] if the type is erased.
     */
    data class ACollection(override val observedType: Type, override val typeIdentifier: TypeIdentifier, val elementType: LocalTypeInformation) : LocalTypeInformation() {
        val isErased: Boolean get() = typeIdentifier is TypeIdentifier.Erased

        fun withElementType(parameter: LocalTypeInformation): ACollection = when(typeIdentifier) {
            is TypeIdentifier.Erased -> {
                val unerasedType = typeIdentifier.toParameterized(listOf(parameter.typeIdentifier))
                ACollection(
                        unerasedType.getLocalType(this::class.java.classLoader),
                        unerasedType,
                        parameter)
            }
            is TypeIdentifier.Parameterised -> {
                val reparameterizedType = typeIdentifier.copy(parameters = listOf(parameter.typeIdentifier))
                ACollection(
                        reparameterizedType.getLocalType(this::class.java.classLoader),
                        reparameterizedType,
                        parameter
                )
            }
            else -> throw IllegalStateException("Cannot parameterise $this")
        }
    }

    /**
     * Represents a type whose underlying class is a map class such as [Map] with two type parameters.
     *
     * @param keyType [LocalTypeInformation] for the first resolved type parameter of the type, i.e. the type of its
     * keys. [Unknown] if the type is erased.
     * @param valueType [LocalTypeInformation] for the second resolved type parameter of the type, i.e. the type of its
     * values. [Unknown] if the type is erased.
     */
    data class AMap(override val observedType: Type, override val typeIdentifier: TypeIdentifier,
                    val keyType: LocalTypeInformation, val valueType: LocalTypeInformation) : LocalTypeInformation() {
        val isErased: Boolean get() = typeIdentifier is TypeIdentifier.Erased

        fun withParameters(keyType: LocalTypeInformation, valueType: LocalTypeInformation): AMap = when(typeIdentifier) {
            is TypeIdentifier.Erased -> {
                val unerasedType = typeIdentifier.toParameterized(listOf(keyType.typeIdentifier, valueType.typeIdentifier))
                AMap(
                        unerasedType.getLocalType(this::class.java.classLoader),
                        unerasedType,
                        keyType, valueType)
            }
            is TypeIdentifier.Parameterised -> {
                val reparameterizedType = typeIdentifier.copy(parameters = listOf(keyType.typeIdentifier, valueType.typeIdentifier))
                AMap(
                        reparameterizedType.getLocalType(this::class.java.classLoader),
                        reparameterizedType,
                        keyType, valueType
                )
            }
            else -> throw IllegalStateException("Cannot parameterise $this")
        }
    }
}

/**
 * Represents information about a constructor.
 */
data class LocalConstructorInformation(
        val observedMethod: KFunction<Any>,
        val parameters: List<LocalConstructorParameterInformation>) {
    val hasParameters: Boolean get() = parameters.isNotEmpty()
}

/**
 * Represents information about a constructor parameter
 */
data class LocalConstructorParameterInformation(
        val name: String,
        val type: LocalTypeInformation,
        val isMandatory: Boolean)

private data class LocalTypeInformationPrettyPrinter(private val simplifyClassNames: Boolean, private val indent: Int = 0) {

    fun prettyPrint(typeInformation: LocalTypeInformation): String =
        with(typeInformation) {
            when (this) {
                is LocalTypeInformation.Abstract ->
                    typeIdentifier.prettyPrint() +
                            printInheritsFrom(interfaces, superclass) +
                            indentAnd { printProperties(properties) }
                is LocalTypeInformation.AnInterface ->
                    typeIdentifier.prettyPrint() + printInheritsFrom(interfaces)
                is LocalTypeInformation.Composable -> typeIdentifier.prettyPrint() +
                        printConstructor(constructor) +
                        printInheritsFrom(interfaces, superclass) +
                        indentAnd { printProperties(properties) }
                else -> typeIdentifier.prettyPrint()
            }
        }

    private fun printConstructor(constructor: LocalConstructorInformation) =
            constructor.parameters.joinToString(", ", "(", ")") {
                it.name +
                        ": " + it.type.typeIdentifier.prettyPrint(simplifyClassNames) +
                        (if (!it.isMandatory) "?" else "")
            }

    private fun printInheritsFrom(interfaces: List<LocalTypeInformation>, superclass: LocalTypeInformation? = null): String {
        val parents = if (superclass == null || superclass == LocalTypeInformation.Top) interfaces.asSequence()
        else sequenceOf(superclass) + interfaces.asSequence()
        return if (!parents.iterator().hasNext()) ""
        else parents.joinToString(", ", ": ", "") { it.typeIdentifier.prettyPrint(simplifyClassNames) }
    }

    private fun printProperties(properties: Map<String, LocalPropertyInformation>) =
            properties.entries.asSequence().sortedBy { it.key }.joinToString("\n", "\n", "") {
                it.prettyPrint()
            }

    private fun Map.Entry<String, LocalPropertyInformation>.prettyPrint(): String =
            "  ".repeat(indent) + key +
                    (if(!value.isMandatory) " (optional)" else "") +
                    (if (value.isCalculated) " (calculated)" else "") +
                    ": " + value.type.prettyPrint()

    private inline fun indentAnd(block: LocalTypeInformationPrettyPrinter.() -> String) =
            copy(indent = indent + 1).block()
}

