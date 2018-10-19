package net.corda.serialization.internal.model

import net.corda.serialization.internal.carpenter.ClassCarpenter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * A [TypeLoader] obtains local types whose [TypeIdentifier]s will reflect those of remote types.
 */
interface TypeLoader {
    /**
     * Obtain a local type which will have the same [TypeIdentifier] as the remote type.
     *
     * @param remoteTypeInformation The type information for the remote type.
     */
    fun load(remoteTypeInformation: RemoteTypeInformation): Type
}

/**
 * A [TypeLoader] that uses the [ClassCarpenter] to build a class matching the supplied [RemoteTypeInformation] if none
 * is visible from the current classloader.
 */
class ClassCarpentingTypeLoader(private val carpenter: ClassCarpenter): TypeLoader {

    val cache = DefaultCacheProvider.createCache<TypeIdentifier, Type>()

    override fun load(remoteTypeInformation: RemoteTypeInformation): Type =
            cache[remoteTypeInformation.typeIdentifier] ?:
            loadUncached(remoteTypeInformation)

    private fun loadUncached(remoteTypeInformation: RemoteTypeInformation): Type {
        val typeGraph = remoteTypeInformation.traverse.toSet()
        val uncached = typeGraph.filter { it.typeIdentifier !in cache }

        // Find or carpent classes for all TypeIdentifiers we don't have cached types for.
        val existingClasses = mutableMapOf<TypeIdentifier, Class<*>>()
        val requiringCarpentry = mutableListOf<RemoteTypeInformation>()

        for (information in uncached) {
            try {
                existingClasses[information.typeIdentifier] = information.typeIdentifier.rawClass
            } catch (e: ClassNotFoundException) {
                requiringCarpentry += information
            }
        }

        carpent(requiringCarpentry)

        // Try again for classes that had to be carpented into existence.
        for (information in requiringCarpentry) {
            existingClasses[information.typeIdentifier] = information.typeIdentifier.rawClass
        }

        return makeType(remoteTypeInformation.typeIdentifier, existingClasses)
    }

    private fun carpent(types: List<RemoteTypeInformation>) {
        throw UnsupportedOperationException("Not implemented yet") // TODO: convert remote type information to carpenter schema
    }

    private fun makeType(typeIdentifier: TypeIdentifier, classLookup: Map<TypeIdentifier, Class<*>>): Type =
            cache[typeIdentifier] ?: when(typeIdentifier) {
                is TypeIdentifier.Parameterised -> RemoteParameterisedType(
                        classLookup[typeIdentifier]!!,
                        typeIdentifier.parameters.map { makeType(it, classLookup) })
                else -> classLookup[typeIdentifier]!!
            }.apply { cache.putIfAbsent(typeIdentifier, this) }

    private val TypeIdentifier.rawClass get() = when(this) {
        is TypeIdentifier.Top,
        is TypeIdentifier.Unknown -> Any::class.java
        else -> Class.forName(name)
    }

    // Recursively traverse all of the types connected to this type in the remote type DAG.
    private val RemoteTypeInformation.traverse: Sequence<RemoteTypeInformation> get() = sequenceOf(this) + when(this) {
        is RemoteTypeInformation.AnInterface -> typeParameters.traverse + interfaces.traverse
        is RemoteTypeInformation.AnEnum -> interfaces.traverse
        is RemoteTypeInformation.AnArray -> componentType.traverse
        is RemoteTypeInformation.APojo -> typeParameters.traverse + interfaces.traverse + properties.traverse
        else -> emptySequence()
    }

    private val Collection<RemoteTypeInformation>.traverse: Sequence<RemoteTypeInformation> get() =
        asSequence().flatMap { it.traverse }

    private val Map<String, RemotePropertyInformation>.traverse: Sequence<RemoteTypeInformation> get() =
        values.asSequence().flatMap { it.type.traverse }
}

private class RemoteParameterisedType(private val rawType: Class<*>, private val typeParameters: List<Type>): ParameterizedType {
    override fun getRawType(): Type = rawType
    override fun getOwnerType(): Type? = null
    override fun getActualTypeArguments(): Array<Type> = typeParameters.toTypedArray()
}