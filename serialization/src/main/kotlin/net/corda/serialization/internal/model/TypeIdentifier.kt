package net.corda.serialization.internal.model

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Used as a key for retrieving cached type information. We need slightly more information than the bare classname,
 * and slightly less information than is captured by Java's [Type]; we also need an identifier we can use even when the
 * identified type is not visible from the current classloader.
 *
 * [TypeIdentifier] provides a family of type identifiers, together with a [prettyPrint] method for displaying them.
 */
sealed class TypeIdentifier {

    /**
     * The name of the type.
     */
    abstract val name: String

    /**
     * Obtain a nicely-formatted representation of the identified type, for help with debugging.
     */
    fun prettyPrint(): String =
        when(this) {
            is TypeIdentifier.Unknown -> "?"
            is TypeIdentifier.Any -> "*"
            is TypeIdentifier.Unparameterised -> name.simple
            is TypeIdentifier.Erased -> "${name.simple} (erased)"
            is TypeIdentifier.ArrayOf -> "${componentType.prettyPrint()}[]"
            is TypeIdentifier.Parameterised ->
                this.name.simple + this.parameters.joinToString(", ", "<", ">") {
                    it.prettyPrint()
                }
        }

    private val String.simple: String get() = split(".", "$").last()

    companion object {
        /**
         * Obtain the [TypeIdentifier] for an erased Java class.
         *
         * @param type The class to get a [TypeIdentifier] for.
         */
        fun forClass(type: Class<*>): TypeIdentifier = when {
            type.name == "java.lang.Object" -> Any
            type.isArray -> ArrayOf(forClass(type.componentType))
            type.typeParameters.isEmpty() -> Unparameterised(type.name)
            else -> Erased(type.name)
        }

        /**
         * Obtain the [TypeIdentifier] for a Java [Type] (typically obtained by calling one of
         * [java.lang.reflect.Parameter.getAnnotatedType],
         * [java.lang.reflect.Field.getGenericType] or
         * [java.lang.reflect.Method.getGenericReturnType]). Wildcard types and type variables are converted to [Unknown].
         */
        fun forGenericType(type: Type, resolutionContext: Type = type): TypeIdentifier = when(type) {
            is ParameterizedType -> Parameterised((type.rawType as Class<*>).name, type.actualTypeArguments.map {
                forGenericType(it.resolveAgainst(resolutionContext))
            })
            is Class<*> -> forClass(type)
            is GenericArrayType -> ArrayOf(forGenericType(type.genericComponentType.resolveAgainst(resolutionContext)))
            else -> Unknown
        }
    }

    /**
     * The [TypeIdentifier] of [Any] / [java.lang.Object].
     */
    object Any: TypeIdentifier() {
        override val name = "*"
    }

    /**
     * The [TypeIdentifier] of an unbounded wildcard.
     */
    object Unknown: TypeIdentifier() {
        override val name = "?"
    }

    /**
     * Identifies a class with no type parameters.
     */
    data class Unparameterised(override val name: String): TypeIdentifier()

    /**
     * Identifies a parameterised class such as List<Int>, for which we cannot obtain the type parameters at runtime
     * because they have been erased.
     */
    data class Erased(override val name: String): TypeIdentifier()

    /**
     * Identifies a type which is an array of some other type.
     *
     * @param componentType The [TypeIdentifier] of the component type of this array.
     */
    data class ArrayOf(val componentType: TypeIdentifier): TypeIdentifier() {
        override val name get() = componentType.name + "[]"
    }

    /**
     * A parameterised class such as Map<String, String> for which we have resolved type parameter values.
     *
     * @param parameters [TypeIdentifier]s for each of the resolved type parameter values of this type.
     */
    data class Parameterised(override val name: String, val parameters: List<TypeIdentifier>): TypeIdentifier()
}