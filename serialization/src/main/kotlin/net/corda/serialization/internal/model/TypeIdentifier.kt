package net.corda.serialization.internal.model

import com.google.common.reflect.TypeToken
import net.corda.serialization.internal.amqp.asClass
import java.io.NotSerializableException
import java.lang.reflect.*
import java.util.*

/**
 * Thrown if a [TypeIdentifier] is incompatible with the local [Type] to which it refers,
 * i.e. if the number of type parameters does not match.
 */
class IncompatibleTypeIdentifierException(message: String): NotSerializableException(message)

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
     * Obtain the local type matching this identifier
     *
     * @param classLoader The classloader to use to load the type.
     * @throws ClassNotFoundException if the type or any of its parameters cannot be loaded.
     * @throws IncompatibleTypeIdentifierException if the type identifier is incompatible with the locally-defined type
     * to which it refers.
     */
    abstract fun getLocalType(classLoader: ClassLoader = ClassLoader.getSystemClassLoader()): Type

    open val erased: TypeIdentifier get() = this

    /**
     * Obtain a nicely-formatted representation of the identified type, for help with debugging.
     */
    fun prettyPrint(simplifyClassNames: Boolean = true): String = when(this) {
            is TypeIdentifier.Cycle -> "cycle($name)"
            is TypeIdentifier.UnknownType -> "?"
            is TypeIdentifier.TopType -> "*"
            is TypeIdentifier.Unparameterised -> name.simplifyClassNameIfRequired(simplifyClassNames)
            is TypeIdentifier.Erased -> "${name.simplifyClassNameIfRequired(simplifyClassNames)} (erased)"
            is TypeIdentifier.ArrayOf -> "${componentType.prettyPrint(simplifyClassNames)}[]"
            is TypeIdentifier.Parameterised ->
                name.simplifyClassNameIfRequired(simplifyClassNames) + parameters.joinToString(", ", "<", ">") {
                    it.prettyPrint(simplifyClassNames)
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
            type.name == "java.lang.Object" -> TopType
            type.isArray -> ArrayOf(forClass(type.componentType))
            type.typeParameters.isEmpty() -> Unparameterised(type.name)
            else -> Erased(type.name, type.typeParameters.size)
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
        fun forGenericType(type: Type, resolutionContext: Type = type, seen: Set<Type> = emptySet()): TypeIdentifier =
                if (type in seen) Cycle(type)
                else when(type) {
                    is ParameterizedType -> Parameterised(
                            (type.rawType as Class<*>).name,
                            type.ownerType?.let { forGenericType(it, seen = seen + type) },
                            type.actualTypeArguments.map { forGenericType(it.resolveAgainst(resolutionContext), seen = seen + type) })
                    is Class<*> -> forClass(type)
                    is GenericArrayType -> ArrayOf(forGenericType(type.genericComponentType.resolveAgainst(resolutionContext), seen = seen + type))
                    is WildcardType -> when {
                        type.upperBounds.isEmpty() || type.upperBounds.size > 1 -> UnknownType
                        else -> forGenericType(type.upperBounds[0], resolutionContext, seen)
                    }
                    else -> UnknownType
                }
    }

    /**
     * The [TypeIdentifier] of [Any] / [java.lang.Object].
     */
    object TopType : TypeIdentifier() {
        override val name get() = "*"
        override fun getLocalType(classLoader: ClassLoader): Type = classLoader.loadClass("java.lang.Object")
        override fun toString() = "TopType"
    }

    private object UnboundedWildcardType : WildcardType {
        override fun getLowerBounds(): Array<Type> = emptyArray()
        override fun getUpperBounds(): Array<Type> = arrayOf(Any::class.java)
        override fun toString() = "?"
    }

    /**
     * The [TypeIdentifier] of an unbounded wildcard.
     */
    object UnknownType : TypeIdentifier() {
        override val name get() = "?"
        override fun getLocalType(classLoader: ClassLoader): Type = UnboundedWildcardType
        override fun toString() = "UnknownType"
    }

    /**
     * A type that closes a cycle in a graph of related types, e.g. type parameters that refer to the type of which
     * they are parameters.
     */
    data class Cycle(val type: Type): TypeIdentifier() {
        override val name get() = type.typeName
        override fun getLocalType(classLoader: ClassLoader): Type = type
        override fun toString() = type.toString()
        val follow: TypeIdentifier get() = forGenericType(type)
    }

    /**
     * Identifies a class with no type parameters.
     */
    data class Unparameterised(override val name: String) : TypeIdentifier() {

        companion object {
            private val primitives = listOf(
                    Byte::class,
                    Boolean:: class,
                    Char::class,
                    Int::class,
                    Short::class,
                    Long::class,
                    Float::class,
                    Double::class).associate {
                it.javaPrimitiveType!!.name to it.javaPrimitiveType
            }
        }
        override fun toString() = "Unparameterised($name)"
        override fun getLocalType(classLoader: ClassLoader): Type = primitives[name] ?: classLoader.loadClass(name)

        val isPrimitive get() = name in primitives
    }

    /**
     * Identifies a parameterised class such as List<Int>, for which we cannot obtain the type parameters at runtime
     * because they have been erased.
     */
    data class Erased(override val name: String, val erasedParameterCount: Int) : TypeIdentifier() {
        /**
         * Get a parameterised version of this type, with the type parameters populated with [TopType].
         */
        fun toParameterized(): TypeIdentifier = toParameterized((0 until erasedParameterCount).map { TopType })

        fun toParameterized(vararg parameters: TypeIdentifier): TypeIdentifier = toParameterized(parameters.toList())

        fun toParameterized(parameters: List<TypeIdentifier>): TypeIdentifier {
            if (parameters.size != erasedParameterCount) throw IncompatibleTypeIdentifierException(
                    "Erased type $name takes $erasedParameterCount parameters, but ${parameters.size} supplied"
            )
            return Parameterised(name, null, parameters)
        }

        override fun toString() = "Erased($name)"

        // Populate erased type parameters when creating local type.
        override fun getLocalType(classLoader: ClassLoader): Type = classLoader.loadClass(name)
    }

    private class ReconstitutedGenericArrayType(private val componentType: Type) : GenericArrayType {
        override fun getGenericComponentType(): Type = componentType
        override fun toString() = "$componentType[]"
        override fun equals(other: Any?): Boolean =
                other is GenericArrayType && componentType == other.genericComponentType
        override fun hashCode(): Int = Objects.hashCode(componentType)
    }

    /**
     * Identifies a type which is an array of some other type.
     *
     * @param componentType The [TypeIdentifier] of the component type of this array.
     */
    data class ArrayOf(val componentType: TypeIdentifier) : TypeIdentifier() {
        override val name get() = componentType.name + "[]"
        override fun toString() = "ArrayOf(${componentType.prettyPrint()})"
        override fun getLocalType(classLoader: ClassLoader): Type {
            val component = componentType.getLocalType(classLoader)
            return when (componentType) {
                is Parameterised -> ReconstitutedGenericArrayType(component)
                else -> java.lang.reflect.Array.newInstance(component.asClass(), 0).javaClass
            }
        }
    }

    private class ReconstitutedParameterizedType(
            private val _rawType: Type,
            private val _ownerType: Type?,
            private val _actualTypeArguments: Array<Type>) : ParameterizedType {
        override fun getRawType(): Type = _rawType
        override fun getOwnerType(): Type? = _ownerType
        override fun getActualTypeArguments(): Array<Type> = _actualTypeArguments
        override fun toString(): String = TypeIdentifier.forGenericType(this).prettyPrint(false)
        override fun equals(other: Any?): Boolean =
                other is ParameterizedType &&
                        other.rawType == rawType &&
                        other.ownerType == ownerType &&
                        Arrays.equals(other.actualTypeArguments, actualTypeArguments)
        override fun hashCode(): Int =
                Arrays.hashCode(actualTypeArguments) xor Objects.hashCode(ownerType) xor Objects.hashCode(rawType)
    }

    /**
     * A parameterised class such as Map<String, String> for which we have resolved type parameter values.
     *
     * @param parameters [TypeIdentifier]s for each of the resolved type parameter values of this type.
     */
    data class Parameterised(override val name: String, val owner: TypeIdentifier?, val parameters: List<TypeIdentifier>) : TypeIdentifier() {
        /**
         * Get the type-erased equivalent of this type.
         */
        override val erased: TypeIdentifier get() = Erased(name, parameters.size)

        override fun toString() = "Parameterised(${prettyPrint()})"
        override fun getLocalType(classLoader: ClassLoader): Type {
            val rawType = classLoader.loadClass(name)
            if (rawType.typeParameters.size != parameters.size) {
                throw IncompatibleTypeIdentifierException(
                        "Class $rawType expects ${rawType.typeParameters.size} type arguments, " +
                                "but type ${this.prettyPrint(false)} has ${parameters.size}")
            }
            return ReconstitutedParameterizedType(
                    rawType,
                    owner?.getLocalType(classLoader),
                    parameters.map { it.getLocalType(classLoader) }.toTypedArray())
        }
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