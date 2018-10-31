package net.corda.serialization.internal.model

import net.corda.serialization.internal.carpenter.ClassCarpenter
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
class ClassCarpentingTypeLoader(private val carpenter: ClassCarpenter, private val classLoader: ClassLoader): TypeLoader {

    val cache = DefaultCacheProvider.createCache<TypeIdentifier, Type>()

    override fun load(remoteTypeInformation: RemoteTypeInformation): Type =
            cache[remoteTypeInformation.typeIdentifier] ?:
            loadUncached(remoteTypeInformation)

    private fun loadUncached(remoteTypeInformation: RemoteTypeInformation): Type {
        val typeGraph = remoteTypeInformation.traverse.toSet()
        val uncached = typeGraph.filter { it.typeIdentifier !in cache }

        // Find or carpent classes for all TypeIdentifiers we don't have cached types for.
        val requiringCarpentry = mutableListOf<RemoteTypeInformation>()

        for (information in uncached) {
            try {
                cache[information.typeIdentifier] = information.typeIdentifier.getLocalType(classLoader)
            } catch (e: ClassNotFoundException) {
                requiringCarpentry += information
            }
        }

        carpent(requiringCarpentry)

        // Try again for classes that had to be carpented into existence.
        for (information in requiringCarpentry) {
            cache[information.typeIdentifier] = information.typeIdentifier.getLocalType(classLoader)
        }

        return cache[remoteTypeInformation.typeIdentifier]!!
    }

    @Suppress("unused")
    private fun carpent(types: List<RemoteTypeInformation>) {
        if (types.isEmpty()) return
        types.forEach { println(it.prettyPrint()) }
        throw UnsupportedOperationException("Not implemented yet") // TODO: convert remote type information to carpenter schema
    }

    // Recursively traverse all of the types connected to this type in the remote type DAG.
    private val RemoteTypeInformation.traverse: Sequence<RemoteTypeInformation> get() = sequenceOf(this) + when(this) {
        is RemoteTypeInformation.AnInterface -> typeParameters.traverse + interfaces.traverse
        is RemoteTypeInformation.AnArray -> componentType.traverse
        is RemoteTypeInformation.Composable -> typeParameters.traverse + interfaces.traverse + properties.traverse
        else -> emptySequence()
    }

    private val Collection<RemoteTypeInformation>.traverse: Sequence<RemoteTypeInformation> get() =
        asSequence().flatMap { it.traverse }

    private val Map<String, RemotePropertyInformation>.traverse: Sequence<RemoteTypeInformation> get() =
        values.asSequence().flatMap { it.type.traverse }
}