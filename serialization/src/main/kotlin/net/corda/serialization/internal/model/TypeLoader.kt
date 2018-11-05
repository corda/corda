package net.corda.serialization.internal.model

import net.corda.serialization.internal.carpenter.*
import java.io.NotSerializableException
import java.lang.ClassCastException
import java.lang.reflect.Type

/**
 * A [TypeLoader] obtains local types whose [TypeIdentifier]s will reflect those of remote types.
 */
interface TypeLoader {
    /**
     * Obtains local types which will have the same [TypeIdentifier]s as the remote types.
     *
     * @param remoteTypeInformation The type information for the remote types.
     */
    fun load(remoteTypeInformation: Collection<RemoteTypeInformation>): Map<TypeIdentifier, Type>
}

/**
 * A [TypeLoader] that uses the [ClassCarpenter] to build a class matching the supplied [RemoteTypeInformation] if none
 * is visible from the current classloader.
 */
class ClassCarpentingTypeLoader(private val carpenter: RemoteTypeCarpenter, private val classLoader: ClassLoader): TypeLoader {

    val cache = DefaultCacheProvider.createCache<TypeIdentifier, Type>()

    override fun load(remoteTypeInformation: Collection<RemoteTypeInformation>): Map<TypeIdentifier, Type> {
        val remoteInformationByIdentifier = remoteTypeInformation.associateBy { it.typeIdentifier }
        val noCarpentryRequired = remoteInformationByIdentifier.asSequence().mapNotNull { (identifier, _) ->
            try {
                identifier to (cache[identifier] ?: identifier.getLocalType(classLoader))
            } catch (e: ClassNotFoundException) {
                    null
            }
        }.toMap()

        if (noCarpentryRequired.size == remoteTypeInformation.size) return noCarpentryRequired

        val requiringCarpentry = remoteInformationByIdentifier.mapNotNull { (identifier, information) ->
            if (identifier in noCarpentryRequired) null else information
        }

        val carpented = CarpentryDependencyGraph.carpentInOrder(carpenter, cache, requiringCarpentry)
        return noCarpentryRequired + carpented
    }
}

