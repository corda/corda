package net.corda.serialization.internal.model

import net.corda.core.serialization.SerializationContext
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
    fun load(remoteTypeInformation: Collection<RemoteTypeInformation>, context: SerializationContext): Map<TypeIdentifier, Type>
}

/**
 * A [TypeLoader] that uses the [ClassCarpenter] to build a class matching the supplied [RemoteTypeInformation] if none
 * is visible from the current classloader.
 *
 * @param mustCarpentMissingTypes Controlled by feature flag DISABLE_JIRA_CORDA_2707 - if this flag is set, then a [ClassNotFoundException]
 * will be thrown if any unknown type is mentioned in the schema of a received message and [carpenterDisabled] is set to `true`. This will
 * prevent any attempt at deserialisation where some of the types in the received message are unknown.
 *
 * If the flag is not set, then deserialisation in the presence of unknown types is permitted, and will only fail if a value of an
 * unknown type is required in the construction of a deserialised object (i.e. if the value is not discarded during type evolution).
 */
class ClassCarpentingTypeLoader(private val carpenter: RemoteTypeCarpenter,
                                private val classLoader: ClassLoader,
                                private val mustCarpentMissingTypes: Boolean): TypeLoader {

    val cache = DefaultCacheProvider.createCache<TypeIdentifier, Type>()

    override fun load(
            remoteTypeInformation: Collection<RemoteTypeInformation>,
            context: SerializationContext
    ): Map<TypeIdentifier, Type> {
        val remoteInformationByIdentifier = remoteTypeInformation.associateBy { it.typeIdentifier }

        // Grab all the types we can from the cache, or the classloader.
        val noCarpentryRequired = remoteInformationByIdentifier.asSequence().mapNotNull { (identifier, _) ->
            try {
                identifier to cache.computeIfAbsent(identifier) { identifier.getLocalType(classLoader) }
            } catch (e: ClassNotFoundException) {
                if (context.carpenterDisabled && mustCarpentMissingTypes) {
                    throw e
                }
                null
            }
        }.toMap()

        // If we have everything we need, or carpentry is disabled, return immediately.
        if (context.carpenterDisabled || noCarpentryRequired.size == remoteTypeInformation.size) return noCarpentryRequired

        // Identify the types which need carpenting up.
        val requiringCarpentry = remoteInformationByIdentifier.asSequence().mapNotNull { (identifier, information) ->
            if (identifier in noCarpentryRequired) null else information
        }.toSet()

        // Build the types requiring carpentry in reverse-dependency order.
        // Something else might be trying to carpent these types at the same time as us, so we always consult
        // (and populate) the cache.
        val carpented = CarpentryDependencyGraph.buildInReverseDependencyOrder(requiringCarpentry) { typeToCarpent ->
            cache.computeIfAbsent(typeToCarpent.typeIdentifier) {
                carpenter.carpent(typeToCarpent)
            }
        }

        // Return the complete map of types.
        return noCarpentryRequired + carpented
    }
}