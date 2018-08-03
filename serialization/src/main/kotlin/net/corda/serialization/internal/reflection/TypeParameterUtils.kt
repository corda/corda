package net.corda.serialization.internal.reflection

import com.google.common.reflect.TypeResolver
import net.corda.serialization.internal.amqp.*
import java.lang.reflect.*

/**
 * Try and infer concrete types for any generics type variables for the actual class encountered,
 * based on the declared type.
 */
// TODO: test GenericArrayType
fun inferTypeVariables(actualClass: Class<*>,
                       declaredClass: Class<*>,
                       declaredType: Type): Type? = when (declaredType) {
    is ParameterizedType -> inferTypeVariables(actualClass, declaredClass, declaredType)
    is GenericArrayType -> {
        val declaredComponent = declaredType.genericComponentType
        inferTypeVariables(actualClass.componentType, declaredComponent.asClass(), declaredComponent)?.asArray()
    }
    // Nothing to infer, otherwise we'd have ParameterizedType
    is Class<*> -> actualClass
    is TypeVariable<*> -> actualClass
    is WildcardType -> actualClass
    else -> throw UnsupportedOperationException("Cannot infer type variables for type $declaredType")
}

/**
 * Try and infer concrete types for any generics type variables for the actual class encountered, based on the declared
 * type, which must be a [ParameterizedType].
 */
private fun inferTypeVariables(actualClass: Class<*>, declaredClass: Class<*>, declaredType: ParameterizedType): Type? {
    if (declaredClass == actualClass) {
        return null
    }

    if (!declaredClass.isAssignableFrom(actualClass)) {
        throw AMQPNotSerializableException(
                declaredType,
                "Found object of type $actualClass in a property expecting $declaredType")
    }

    if (actualClass.typeParameters.isEmpty()) {
        return actualClass
    }
    // The actual class can never have type variables resolved, due to the JVM's use of type erasure, so let's try and resolve them
    // Search for declared type in the inheritance hierarchy and then see if that fills in all the variables
    val implementationChain: List<Type> = findPathToDeclared(actualClass, declaredType)?.toList()
            ?: throw AMQPNotSerializableException(
                    declaredType,
                    "No inheritance path between actual $actualClass and declared $declaredType.")

    val start = implementationChain.last()
    val rest = implementationChain.dropLast(1).drop(1)
    val resolver = rest.reversed().fold(TypeResolver().where(start, declaredType)) { resolved, chainEntry ->
        val newResolved = resolved.resolveType(chainEntry)
        TypeResolver().where(chainEntry, newResolved)
    }
    // The end type is a special case as it is a Class, so we need to fake up a ParameterizedType for it to get the TypeResolver to do anything.
    val endType = DeserializedParameterizedType(actualClass, actualClass.typeParameters)
    return resolver.resolveType(endType)
}

// Stop when reach declared type or return null if we don't find it.
private fun findPathToDeclared(startingType: Type, declaredType: Type, chain: Sequence<Type> = emptySequence()): Sequence<Type>? {
    val extendedChain = chain + startingType
    val startingClass = startingType.asClass()

    if (startingClass == declaredType.asClass()) {
        // We're done...
        return extendedChain
    }

    val resolver = { type: Type ->
        TypeResolver().where(
                startingClass.asParameterizedType(),
                startingType.asParameterizedType())
                .resolveType(type)
    }

    // Now explore potential options of superclass and all interfaces
    return findPathViaGenericSuperclass(startingClass, resolver, declaredType, extendedChain)
        ?: findPathViaInterfaces(startingClass, resolver, declaredType, extendedChain)
}

private fun findPathViaInterfaces(startingClass: Class<*>, resolver: (Type) -> Type, declaredType: Type, extendedChain: Sequence<Type>): Sequence<Type>? =
    startingClass.genericInterfaces.asSequence().map {
        findPathToDeclared(resolver(it), declaredType, extendedChain)
    }.filterNotNull().firstOrNull()


private fun findPathViaGenericSuperclass(startingClass: Class<*>, resolver: (Type) -> Type, declaredType: Type, extendedChain: Sequence<Type>): Sequence<Type>? {
    val superClass = startingClass.genericSuperclass ?: return null
    return findPathToDeclared(resolver(superClass), declaredType, extendedChain)
}

