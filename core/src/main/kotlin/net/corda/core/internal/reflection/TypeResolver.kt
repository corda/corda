package net.corda.core.internal.reflection

import java.lang.reflect.*

/**
 * Take all type parameters to their upper bounds, recursively resolving type variables against the provided context.
 */
fun Type.resolveAgainst(context: Type): Type = when (this) {
    is WildcardType -> this.upperBound
    is ArtificialParameterizedType -> this
    is ParameterizedType,
    is TypeVariable<*> -> {
        val resolved = TypeResolver.against(context).resolveType(this).upperBound
        if (resolved !is TypeVariable<*> || resolved == this) resolved
        else resolved.resolveAgainst(context)
    }
    else -> this
}

internal val Type.upperBound: Type
    get() = when (this) {
        is TypeVariable<*> -> when {
            this.bounds.isEmpty() || this.bounds.size > 1 -> this
            else -> this.bounds[0]
        }
        is WildcardType -> when {
            this.upperBounds.isEmpty() || this.upperBounds.size > 1 -> this
            else -> this.upperBounds[0]
        }
        is ParameterizedType -> ArtificialParameterizedType(
                rawType,
                ownerType,
                actualTypeArguments.map { it.upperBound }.toTypedArray())
        else -> this
    }

/** A TypeTable maintains mappings from [TypeVariable]s to types. */
data class TypeTable(private val map: Map<TypeVariable<*>, Type>, private val guarded: List<TypeVariable<*>>) {

    companion object {
        val empty = TypeTable(emptyMap(), emptyList())
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
        if (guarded.any { it.genericDeclaration == variable.genericDeclaration }) return variable

        map[variable]?.apply {
            return TypeResolver(forDependants).resolveType(this)
        }

        val bounds = variable.bounds
        if (bounds.isEmpty()) return variable

        val resolvedBounds = TypeResolver(forDependants).resolveTypes(bounds)
        return ArtificialTypeVariable(
                variable.genericDeclaration,
                variable.name,
                if (resolvedBounds.isEmpty()) listOf<Type>(Any::class.java) else resolvedBounds.toList())
    }
}

internal class TypeResolver(private val typeTable: TypeTable) {

    companion object {
        fun against(contextType: Type): TypeResolver {
            return TypeResolver(TypeTable(getTypeMappings(contextType), emptyList()))
        }

        /**
         * Returns type mappings using type parameters and type arguments found in the generic
         * superclass and the super interfaces of `contextClass`.
         */
        private fun getTypeMappings(contextType: Type): Map<TypeVariable<*>, Type> {
            val result = mutableMapOf<TypeVariable<*>, Type>()

            getMappings(contextType).forEach { (key, type) ->
                if (result.containsKey(key)) return@forEach

                // First, check whether var -> arg forms a cycle
                var t: Type? = type
                while (t != null) {
                    if (t is TypeVariable<*> && t.name == key.name && t.genericDeclaration == key.genericDeclaration) {
                        // cycle detected, remove the entire cycle from the mapping so that
                        // each type variable resolves deterministically to itself.
                        // Otherwise, a F -> T cycle will end up resolving both F and T
                        // nondeterministically to either F or T.
                        var x: Type? = type
                        while (x != null && x is TypeVariable<*>) {
                            x = result.remove(x)
                        }
                        return@forEach
                    }
                    t = (t as? TypeVariable<*>)?.let { result[it] }
                }
                result[key] = type
            }
            return result
        }

        fun getMappings(type: Type): Sequence<Pair<TypeVariable<*>, Type>> = when(type) {
            is Class<*> -> getClassMappings(type)
            is ParameterizedType -> getParameterizedTypeMappings(type)
            is TypeVariable<*> -> type.bounds.asSequence().flatMap(::getMappings)
            is WildcardType -> type.upperBounds.asSequence().flatMap(::getMappings)
            else -> emptySequence()
        }

        private fun getClassMappings(type: Class<*>) =
                (type.genericSuperclass?.let(::getMappings) ?: emptySequence()) +
                        type.genericInterfaces.asSequence().flatMap(::getMappings)

        private fun getParameterizedTypeMappings(parameterizedType: ParameterizedType): Sequence<Pair<TypeVariable<*>, Type>> {
            val rawClass = parameterizedType.rawType as Class<*>
            val vars = rawClass.typeParameters
            val typeArgs = parameterizedType.actualTypeArguments

            require(vars.size == typeArgs.size)
            val typeVariableMappings = vars.asSequence().zip(typeArgs.asSequence()).map { (typeVariable, type) ->
                typeVariable to type
            }
            val ownerTypeMapping = parameterizedType.ownerType?.let(::getMappings) ?: emptySequence()
            return getMappings(rawClass) + typeVariableMappings + ownerTypeMapping
        }
    }

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
    }

    internal fun resolveTypes(types: Array<Type>): Array<Type> = types.map(::resolveType).toTypedArray()

    private fun resolveWildcardType(type: WildcardType): WildcardType =
        ArtificialWildcardType(resolveTypes(type.lowerBounds), resolveTypes(type.upperBounds))

    private fun resolveGenericArrayType(type: GenericArrayType): Type =
        ArtificialGenericArrayType(resolveType(type.genericComponentType))

    private fun resolveParameterizedType(type: ParameterizedType): ParameterizedType {
        val resolvedOwner = type.ownerType?.let(::resolveType)
        val resolvedRawType = resolveType(type.rawType)

        val args = type.actualTypeArguments
        val resolvedArgs = resolveTypes(args)
        return ArtificialParameterizedType(resolvedRawType as Class<*>, resolvedOwner, resolvedArgs)
    }
}