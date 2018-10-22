package net.corda.serialization.internal.model

import com.google.common.reflect.TypeToken
import java.lang.reflect.*

/**
 * Used as a key for retrieving cached type information. We need slightly more information than the bare classname,
 * and slightly less information than is captured by Java's [Type] (We drop type variance because in practice we resolve
 * wildcards to their upper bounds, e.g. `? extends Foo` to `Foo`). We also need an identifier we can use even when the
 * identified type is not visible from the current classloader.
 *
 * These identifiers act as the anchor for comparison between remote type information (prior to matching it to an actual
 * local class) and local type information.
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
    fun prettyPrint(simplifyClassNames: Boolean = true): String = when(this) {
            is TypeIdentifier.Unknown -> "?"
            is TypeIdentifier.Top -> "*"
            is TypeIdentifier.Unparameterised -> name.simplifyClassNameIfRequired(simplifyClassNames)
            is TypeIdentifier.Erased -> "${name.simplifyClassNameIfRequired(simplifyClassNames)} (erased)"
            is TypeIdentifier.ArrayOf -> "${componentType.prettyPrint()}[]"
            is TypeIdentifier.Parameterised ->
                name.simplifyClassNameIfRequired(simplifyClassNames) + parameters.joinToString(", ", "<", ">") {
                    it.prettyPrint()
                }
        }

    private fun String.simplifyClassNameIfRequired(simplifyClassNames: Boolean): String =
        if (simplifyClassNames) split(".", "$").last() else this

    companion object {
        /**
         * Obtain the [TypeIdentifier] for an erased Java class.
         *
         * @param type The class to get a [TypeIdentifier] for.
         */
        fun forClass(type: Class<*>): TypeIdentifier = when {
            type.name == "java.lang.Object" -> Top
            type.isArray -> ArrayOf(forClass(type.componentType))
            type.typeParameters.isEmpty() -> Unparameterised(type.name)
            else -> Erased(type.name)
        }

        /**
         * Obtain the [TypeIdentifier] for a Java [Type] (typically obtained by calling one of
         * [java.lang.reflect.Parameter.getAnnotatedType],
         * [java.lang.reflect.Field.getGenericType] or
         * [java.lang.reflect.Method.getGenericReturnType]). Wildcard types and type variables are converted to [Unknown].
         *
         * @param type The [Type] to obtain a [TypeIdentifier] for.
         * @param resolutionContext Optionally, a [Type] which can be used to resolve type variables, for example a
         * class implementing a parameterised interface and specifying values for type variables which are referred to
         * by methods defined in the interface.
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
    object Top : TypeIdentifier() {
        override val name get() = "*"
        override fun toString() = "Top"
    }

    /**
     * The [TypeIdentifier] of an unbounded wildcard.
     */
    object Unknown : TypeIdentifier() {
        override val name get() = "?"
        override fun toString() = "Unknown"
    }

    /**
     * Identifies a class with no type parameters.
     */
    data class Unparameterised(override val name: String) : TypeIdentifier() {
        override fun toString() = "Unparameterised($name)"
    }

    /**
     * Identifies a parameterised class such as List<Int>, for which we cannot obtain the type parameters at runtime
     * because they have been erased.
     */
    data class Erased(override val name: String) : TypeIdentifier() {
        override fun toString() = "Erased($name)"
    }

    /**
     * Identifies a type which is an array of some other type.
     *
     * @param componentType The [TypeIdentifier] of the component type of this array.
     */
    data class ArrayOf(val componentType: TypeIdentifier) : TypeIdentifier() {
        override val name get() = componentType.name + "[]"
        override fun toString() = "ArrayOf(${componentType.prettyPrint()})"
    }

    /**
     * A parameterised class such as Map<String, String> for which we have resolved type parameter values.
     *
     * @param parameters [TypeIdentifier]s for each of the resolved type parameter values of this type.
     */
    data class Parameterised(override val name: String, val parameters: List<TypeIdentifier>) : TypeIdentifier() {
        override fun toString() = "Parameterised(${prettyPrint()})"
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
            this.bounds.isEmpty() || this.bounds.size > 1 -> this
            else -> this.bounds[0]
        }
        is WildcardType -> when {
            this.upperBounds.isEmpty() || this.upperBounds.size > 1 -> this
            else -> this.upperBounds[0]
        }
        else -> this
    }