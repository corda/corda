package net.corda.core.internal.reflection

import java.lang.UnsupportedOperationException
import java.lang.reflect.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wraps around `TypeVariable<?>` to ensure that any two type variables are equal as long as
 * they are declared by the same [java.lang.reflect.GenericDeclaration] and have the same
 * name, even if their bounds differ.
 *
 *
 * While resolving a type variable from a `var -> type` map, we don't care whether the
 * type variable's bound has been partially resolved. As long as the type variable "identity"
 * matches.
 *
 *
 * On the other hand, if for example we are resolving `List<A extends B>` to `List<A extends String>`, we need to compare that `<A extends B>` is unequal to `<A
 * extends String>` in order to decide to use the transformed type instead of the original type.
 */
data class TypeVariableKey(private var variable: TypeVariable<*>) {
    /**
     * Returns true if `type` is a `TypeVariable` with the same name and declared by the
     * same `GenericDeclaration`.
     */
    fun equalsType(type: Type): Boolean =
        type is TypeVariable<*> &&
            variable.genericDeclaration == type.genericDeclaration &&
            variable.name == type.name


    companion object {
        /** Wraps `t` in a `TypeVariableKey` if it's a type variable. */
        fun forLookup(t: Type): TypeVariableKey? = (t as? TypeVariable<*>)?.let(::TypeVariableKey)
    }
}

/** A TypeTable maintains mapping from [TypeVariable] to types.  */
data class TypeTable(private val map: Map<TypeVariableKey, Type>, private val guarded: List<TypeVariable<*>>) {

    companion object {
        val empty = TypeTable(emptyMap(), emptyList())
    }

    /** Returns a new `TypeResolver` with `variable` mapping to `type`.  */
    fun where(newMappings: Map<TypeVariableKey, Type>): TypeTable {
        newMappings.forEach { (key, type) ->
            require(!key.equalsType(type)) { "Type variable $key bound to itself" }
        }
        return copy(map = map + newMappings)
    }

    fun resolve(variable: TypeVariable<*>): Type {
        return resolveInternal(variable, copy(guarded = guarded + variable))
    }

    /**
     * Resolves `variable` using the encapsulated type mapping. If it maps to yet another
     * non-reified type or has bounds, `forDependants` is used to do further resolution, which
     * doesn't try to resolve any type variable on generic declarations that are already being
     * resolved.
     *
     * Should only be called by [.resolve].
     */
    private fun resolveInternal(variable: TypeVariable<*>, forDependants: TypeTable): Type {
        guarded.firstOrNull { it.genericDeclaration == variable.genericDeclaration }?.apply {
            return variable
        }

        map[TypeVariableKey(variable)]?.apply {
            return TypeResolver(forDependants).resolveType(this)
        }

        val bounds = variable.bounds
        if (bounds.isEmpty()) return variable

        val resolvedBounds = TypeResolver(forDependants).resolveTypes(bounds)
        return TypeVariableImpl(
                variable.genericDeclaration,
                variable.name,
                if (resolvedBounds.isEmpty()) listOf<Type>(Any::class.java) else resolvedBounds.toList())
    }
}

class TypeResolver(private val typeTable: TypeTable = TypeTable.empty) {

    /**
     * Returns a new `TypeResolver` with type variables in `formal` mapping to types in
     * `actual`.
     *
     *
     * For example, if `formal` is a `TypeVariable T`, and `actual` is `String.class`, then `new TypeResolver().where(formal, actual)` will [ ][.resolveType] `ParameterizedType List<T>` to `List<String>`, and resolve
     * `Map<T, Something>` to `Map<String, Something>` etc. Similarly, `formal` and
     * `actual` can be `Map<K, V>` and `Map<String, Integer>` respectively, or they
     * can be `E[]` and `String[]` respectively, or even any arbitrary combination
     * thereof.
     *
     * @param formal The type whose type variables or itself is mapped to other type(s). It's almost
     * always a bug if `formal` isn't a type variable and contains no type variable. Make
     * sure you are passing the two parameters in the right order.
     * @param actual The type that the formal type variable(s) are mapped to. It can be or contain yet
     * other type variables, in which case these type variables will be further resolved if
     * corresponding mappings exist in the current `TypeResolver` instance.
     */
    fun where(formal: Type, actual: Type): TypeResolver =
        where(populateTypeMappings(formal, actual).toMap())

    fun where(mappings: Map<TypeVariableKey, Type>) =
        TypeResolver(typeTable.where(mappings))

    /**
     * Resolves all type variables in `type` and all downstream types and returns a
     * corresponding type with type variables resolved.
     */
    fun resolveType(type: Type): Type = when(type) {
        is TypeVariable<*> -> typeTable.resolve(type)
        is ParameterizedType -> resolveParameterizedType(type)
        is GenericArrayType -> resolveGenericArrayType(type)
        is WildcardType -> resolveWildcardType(type)
        else -> type
    }.apply {
        println("Resolved $type to $this")
    }

    internal fun resolveTypes(types: Array<Type>): Array<Type> = types.map(::resolveType).toTypedArray()

    private fun resolveWildcardType(type: WildcardType): WildcardType =
        WildcardTypeImpl(resolveTypes(type.lowerBounds), resolveTypes(type.upperBounds))

    private fun resolveGenericArrayType(type: GenericArrayType): Type =
        GenericArrayTypeImpl(resolveType(type.genericComponentType))

    private fun resolveParameterizedType(type: ParameterizedType): ParameterizedType {
        val resolvedOwner = type.ownerType?.let(::resolveType)
        val resolvedRawType = resolveType(type.rawType)

        val args = type.actualTypeArguments
        val resolvedArgs = resolveTypes(args)
        return ParameterizedTypeImpl(resolvedRawType as Class<*>, resolvedOwner, resolvedArgs)
    }

    private object TypeMappingIntrospector {
        /**
         * Returns type mappings using type parameters and type arguments found in the generic
         * superclass and the super interfaces of `contextClass`.
         */
        internal fun getTypeMappings(contextType: Type): Map<TypeVariableKey, Type> {
            val result = mutableMapOf<TypeVariableKey, Type>()
            getMappings(contextType).forEach { (key, type) ->
                if (result.containsKey(key)) {
                    return@forEach
                }
                // First, check whether var -> arg forms a cycle
                var t: Type? = type
                while (t != null) {
                    if (key.equalsType(t)) {
                        // cycle detected, remove the entire cycle from the mapping so that
                        // each type variable resolves deterministically to itself.
                        // Otherwise, a F -> T cycle will end up resolving both F and T
                        // nondeterministically to either F or T.
                        var x: Type? = type
                        while (x != null) {
                            x = result.remove(TypeVariableKey.forLookup(x))
                        }
                        return@forEach
                    }
                    t = result[TypeVariableKey.forLookup(t)]
                }
                result[key] = type
            }
            return result
        }

        fun getMappings(type: Type): Sequence<Pair<TypeVariableKey, Type>> = when(type) {
            is Class<*> -> getClassMappings(type)
            is ParameterizedType -> getParameterizedTypeMappings(type)
            is TypeVariable<*> -> type.bounds.asSequence().flatMap(::getMappings)
            is WildcardType -> type.upperBounds.asSequence().flatMap(::getMappings)
            else -> emptySequence()
        }

        private fun getClassMappings(type: Class<*>) =
            (type.genericSuperclass?.let(::getMappings) ?: emptySequence()) +
            type.genericInterfaces.asSequence().flatMap(::getMappings)

        private fun getParameterizedTypeMappings(parameterizedType: ParameterizedType): Sequence<Pair<TypeVariableKey, Type>> {
            val rawClass = parameterizedType.rawType as Class<*>
            val vars = rawClass.typeParameters
            val typeArgs = parameterizedType.actualTypeArguments

            require(vars.size == typeArgs.size)
            val typeVariableMappings = vars.asSequence().zip(typeArgs.asSequence()).map { (typeVariable, type) ->
                TypeVariableKey(typeVariable) to type
            }
            val ownerTypeMapping = parameterizedType.ownerType?.let(::getMappings) ?: emptySequence()
            return getMappings(rawClass) + typeVariableMappings + ownerTypeMapping
        }
    }

    // This is needed when resolving types against a context with wildcards
    // For example:
    // class Holder<T> {
    //   void set(T data) {...}
    // }
    // Holder<List<?>> should *not* resolve the set() method to set(List<?> data).
    // Instead, it should create a capture of the wildcard so that set() rejects any List<T>.
    private class WildcardCapturer(private val captureAsTypeVariable: (Array<Type>) -> TypeVariable<*>) {

        companion object {
            val id: AtomicInteger = AtomicInteger()
            val instance = WildcardCapturer { upperBounds ->
                val name = "capture#" + id.incrementAndGet() + "-of ? extends " + upperBounds.joinToString("&")
                TypeVariableImpl(WildcardCapturer::class.java, name, upperBounds.toList())
            }
        }

        internal fun capture(type: Type): Type = when(type) {
            is Class<*> -> type
            is TypeVariable<*> -> type
            is GenericArrayType -> GenericArrayTypeImpl(notForTypeVariable().capture(type.genericComponentType))
            is ParameterizedType -> captureParameterizedType(type)
            is WildcardType -> captureWildcardType(type)
            else -> throw UnsupportedOperationException()
        }

        private fun captureWildcardType(type: WildcardType): Type {
            val lowerBounds = type.lowerBounds
            return if (lowerBounds.isEmpty()) { // ? extends something changes to capture-of
                captureAsTypeVariable(type.upperBounds)
            } else {
                type
            }
        }

        private fun captureParameterizedType(type: ParameterizedType): ParameterizedTypeImpl {
            val rawType = type.rawType as Class<*>
            val typeVars = rawType.typeParameters
            val typeArgs = type.actualTypeArguments
            for (i in typeArgs.indices) {
                typeArgs[i] = forTypeVariable(typeVars[i]).capture(typeArgs[i])
            }
            return ParameterizedTypeImpl(
                    rawType,
                    notForTypeVariable().captureNullable(type.ownerType),
                    typeArgs)
        }

        private fun forTypeVariable(typeParam: TypeVariable<*>): WildcardCapturer = WildcardCapturer { upperBounds ->
            val combined = LinkedHashSet<Type>(upperBounds.asList())
            // Since this is an artifically generated type variable, we don't bother checking
            // subtyping between declared type bound and actual type bound. So it's possible that we
            // may generate something like <capture#1-of ? extends Foo&SubFoo>.
            // Checking subtype between declared and actual type bounds
            // adds recursive isSubtypeOf() call and feels complicated.
            // There is no contract one way or another as long as isSubtypeOf() works as expected.
            combined.addAll(typeParam.bounds)
            if (combined.size > 1) { // Object is implicit and only useful if it's the only bound.
                combined.remove(Any::class.java)
            }
            captureAsTypeVariable(combined.toTypedArray())
        }

        private fun notForTypeVariable(): WildcardCapturer = WildcardCapturer.instance

        private fun captureNullable(type: Type?): Type? = type?.let(::capture)
    }

    companion object {
        /**
         * Returns a resolver that resolves types "invariantly".
         *
         *
         * For example, when resolving `List<T>` in the context of `ArrayList<?>`,
         * `<T>` cannot be invariantly resolved to `<?>` because otherwise the parameter type
         * of `List::set` will be `<?>` and it'll falsely say any object can be passed into
         * `ArrayList<?>::set`.
         *
         *
         * Instead, `<?>` will be resolved to a capture in the form of a type variable
         * `<capture-of-? extends Object>`, effectively preventing `set` from accepting any
         * type.
         */
        fun invariantly(contextType: Type): TypeResolver {
            val invariantContext = WildcardCapturer.instance.capture(contextType)
            return TypeResolver().where(TypeMappingIntrospector.getTypeMappings(invariantContext))
        }

        private fun populateTypeMappings(from: Type, to: Type): Sequence<Pair<TypeVariableKey, Type>> {
            if (from == to) {
                return emptySequence()
            }
            return when (from) {
                is TypeVariable<*> -> sequenceOf(TypeVariableKey(from) to to)
                is WildcardType -> if (to !is WildcardType) emptySequence() else {
                    val fromUpperBounds = from.upperBounds
                    val toUpperBounds = to.upperBounds
                    val fromLowerBounds = from.lowerBounds
                    val toLowerBounds = to.lowerBounds
                    require(fromUpperBounds.size == toUpperBounds.size && fromLowerBounds.size == toLowerBounds.size) {
                        "Incompatible type: $from vs. $to"
                    }
                    val uppers = fromUpperBounds.asSequence().zip(toUpperBounds.asSequence()).flatMap { (upper, lower) ->
                        populateTypeMappings(upper, lower)
                    }
                    val lowers = fromLowerBounds.asSequence().zip(toLowerBounds.asSequence()).flatMap { (upper, lower) ->
                        populateTypeMappings(upper, lower)
                    }
                    uppers + lowers
                }
                is ParameterizedType -> if (to is WildcardType) emptySequence() else {
                    to as ParameterizedType
                    if (from.ownerType != null && to.ownerType != null) {
                        return populateTypeMappings(from.ownerType, to.ownerType)
                    }
                    require(from.rawType == to.rawType) {
                        "Inconsistent raw type: $from vs. $to"
                    }
                    val fromArgs = from.actualTypeArguments
                    val toArgs = to.actualTypeArguments
                    require(fromArgs.size == toArgs.size) {
                        "$from not compatible with $to"
                    }
                    fromArgs.asSequence().zip(toArgs.asSequence()).flatMap { (fromArg, toArg) ->
                        populateTypeMappings(fromArg, toArg)
                    }
                }
                is GenericArrayType -> if (to is WildcardType) emptySequence() else {
                    val componentType = (to as? GenericArrayType)?.genericComponentType
                        ?: (to as? Class<*>)?.componentType
                        ?: throw IllegalArgumentException("$to is not an array type")
                    populateTypeMappings(from.genericComponentType, componentType)
                }
                is Class<*> -> if (to is WildcardType) emptySequence()
                    else throw IllegalArgumentException("No type mapping from $from to $to")
                else -> throw UnsupportedOperationException()
            }
        }
    }
}

private data class TypeVariableImpl<D : GenericDeclaration>(private val _genericDeclaration: D, private val _name: String, private val boundsList: List<Type>): TypeVariable<D> {
    override fun getName(): String = _name

    override fun getAnnotatedBounds(): Array<AnnotatedType> {
        throw UnsupportedOperationException("getAnnotatedBounds")
    }

    override fun getDeclaredAnnotations(): Array<Annotation> = emptyArray()
    override fun getGenericDeclaration(): D = _genericDeclaration
    override fun <T : Annotation?> getAnnotation(annotationClass: Class<T>?): T? = null

    override fun getAnnotations(): Array<Annotation> = emptyArray()
    override fun getBounds(): Array<Type> = boundsList.toTypedArray()
    override fun getTypeName(): String = _name
    override fun toString(): String = _name
}

private class WildcardTypeImpl(val _lowerBounds: Array<Type>, val _upperBounds: Array<Type>) : WildcardType {

    override fun getLowerBounds(): Array<Type> = _lowerBounds
    override fun getUpperBounds(): Array<Type> = _upperBounds

    override fun equals(obj: Any?): Boolean =
            obj is WildcardType &&
                    Arrays.equals(_lowerBounds, obj.lowerBounds) &&
                    Arrays.equals(_upperBounds, obj.upperBounds)

    override fun hashCode(): Int {
        return lowerBounds.hashCode() xor upperBounds.hashCode()
    }

    override fun toString(): String {
        val supers = lowerBounds.joinToString { " super ${it.typeName}" }
        val extends = upperBounds.joinToString { " extends ${it.typeName}" }
        return "?$supers$extends"
    }
}
