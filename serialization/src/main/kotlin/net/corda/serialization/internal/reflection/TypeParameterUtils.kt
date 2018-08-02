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
        inferTypeVariables(actualClass.componentType, declaredComponent.asClass()!!, declaredComponent)?.asArray()
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
    val implementationChain: List<Type> = findPathToDeclared(actualClass, declaredType)
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
private fun findPathToDeclared(startingType: Type, declaredType: Type, chain: List<Type> = emptyList()): List<Type>? {
    val extendedChain = chain + startingType
    val startingClass = startingType.asClass() ?: return null

    if (startingClass == declaredType.asClass()) {
        // We're done...
        return extendedChain
    }

    // Now explore potential options of superclass and all interfaces
    return findPathViaGenericSuperclass(startingClass, startingType, declaredType, extendedChain)
        ?: findPathViaInterfaces(startingClass, startingType, declaredType, extendedChain)
}

private fun findPathViaInterfaces(startingClass: Class<*>, startingType: Type, declaredType: Type, extendedChain: List<Type>): List<Type>? {
    for (iface in startingClass.genericInterfaces) {
        val resolved = TypeResolver().where(startingClass.asParameterizedType(), startingType.asParameterizedType()).resolveType(iface)
        return findPathToDeclared(resolved, declaredType, extendedChain) ?: continue
    }
    return null
}

private fun findPathViaGenericSuperclass(startingClass: Class<*>, startingType: Type, declaredType: Type, extendedChain: List<Type>): List<Type>? {
    val superClass = startingClass.genericSuperclass ?: return null
    val resolved = TypeResolver().where(startingClass.asParameterizedType(), startingType.asParameterizedType()).resolveType(superClass)
    return findPathToDeclared(resolved, declaredType, extendedChain)
}
/*
if (startingClass == null) {
    return null
}

// Now explore potential options of superclass and all interfaces
return getPathToGenericSuperclass(startingClass, startingType, declaredType, extendedChain)
        ?: findPathToGenericInterface(startingClass, startingType, declaredType, extendedChain)
}

private fun findPathToGenericInterface(startingClass: Class<*>, startingType: Type, declaredType: Type, extendedChain: List<Type>): List<Type>? {
for (iface in startingClass.genericInterfaces) {
    val resolved = TypeResolver().where(startingClass.asParameterizedType(), startingType.asParameterizedType()).resolveType(iface)
    return findPathToDeclared(resolved, declaredType, extendedChain) ?: continue
}
return null
}

private fun getPathToGenericSuperclass(startingClass: Class<*>, startingType: Type, declaredType: Type, extendedChain: List<Type>): List<Type>? {
val superClass = startingClass.genericSuperclass
val resolved = TypeResolver().where(superClass.asParameterizedType(), startingType.asParameterizedType()).resolveType(superClass)
return findPathToDeclared(resolved, declaredType, extendedChain)
}
    */