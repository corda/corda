package net.corda.core.internal.reflection

import net.corda.core.KeepForDJVM
import java.lang.reflect.*

/**
 * Provides a means for looking up [LocalTypeInformation] by [Type] and [TypeIdentifier], falling back to building it
 * if the lookup can't supply it.
 *
 * The purpose of this class is to make a registry of [LocalTypeInformation] usable by a [LocalTypeInformationBuilder] that
 * recursively builds [LocalTypeInformation] for all of the types visible by traversing the DAG of related types of a given
 * [Type].
 */
@KeepForDJVM
interface LocalTypeLookup {

    /**
     * Either return the [LocalTypeInformation] held in the registry for the given [Type] and [TypeIdentifier] or, if
     * no such information is registered, call the supplied builder to construct the type information, add it to the
     * registry and then return it.
     */
    fun findOrBuild(type: Type, typeIdentifier: TypeIdentifier, builder: (Boolean) -> LocalTypeInformation): LocalTypeInformation

    /**
     * Indicates whether a type should be excluded from lists of interfaces associated with inspected types, i.e.
     * because it is not whitelisted.
     */
    fun isExcluded(type: Type): Boolean
}

/**
 * A [LocalTypeModel] maintains a registry of [LocalTypeInformation] for all [Type]s which have been observed within a
 * given classloader context.
 */
@KeepForDJVM
interface LocalTypeModel {

    companion object {
        /**
         * Obtain a [LocalTypeModel] configured with the provided [LocalTypeModelConfiguration]
         */
        fun configuredWith(typeModelConfiguration: LocalTypeModelConfiguration): LocalTypeModel =
                ConfigurableLocalTypeModel(typeModelConfiguration)

        /**
         * A [LocalTypeModel] configured with no whitelist exclusions, and no opaque types (i.e. types for which we do not
         * ordinarily need detailed type information, for example because they are handled by custom serialisers).
         */
        val unconstrained = configuredWith(NoExclusionNoOpacity)
    }

    /**
     * Look for a [Type] in the registry, and return its associated [LocalTypeInformation] if found. If the [Type] is
     * not in the registry, build [LocalTypeInformation] for that type, using this [LocalTypeModel] as the [LocalTypeLookup]
     * for recursively resolving dependencies, place it in the registry, and return it.
     *
     * @param type The [Type] to get [LocalTypeInformation] for.
     */
    fun inspect(type: Type): LocalTypeInformation = inspect(type, false)

    /**
     * Look for a [Type] in the registry, and return its associated [LocalTypeInformation] if found. If the [Type] is
     * not in the registry, build [LocalTypeInformation] for that type, using this [LocalTypeModel] as the [LocalTypeLookup]
     * for recursively resolving dependencies, place it in the registry, and return it.
     *
     * @param type The [Type] to get [LocalTypeInformation] for.
     */
    fun inspect(type: Type, suppressWarnings: Boolean): LocalTypeInformation
}

/**
 * A [LocalTypeLookup] that is configurable with [LocalTypeModelConfiguration], which controls which types are seen as "opaque"
 * and which are "excluded" (see docs for [LocalTypeModelConfiguration] for explanation of these terms.
 *
 * @param typeModelConfiguration Configuration controlling the behaviour of the [LocalTypeModel]'s type inspection.
 */
@KeepForDJVM
private class ConfigurableLocalTypeModel(private val typeModelConfiguration: LocalTypeModelConfiguration): LocalTypeModel {

    private val typeInformationCache = DefaultCacheProvider.createCache<TypeIdentifier, LocalTypeInformation>()

    /**
     * We need to provide the [TypeInformationBuilder] with a temporary local cache, so that it doesn't leak
     * [LocalTypeInformation] with unpatched cycles into the global cache where other threads can access them
     * before we've patched the cycles up.
     */
    private class BuilderLookup(
            private val globalCache: MutableMap<TypeIdentifier, LocalTypeInformation>,
            private val typeModelConfiguration: LocalTypeModelConfiguration) : LocalTypeLookup {

        private val localCache: MutableMap<TypeIdentifier, LocalTypeInformation> = mutableMapOf()

        /**
         * Read from the global cache (which contains only cycle-resolved type information), falling through
         * to the local cache if the type isn't there yet.
         */
        override fun findOrBuild(type: Type, typeIdentifier: TypeIdentifier, builder: (Boolean) -> LocalTypeInformation): LocalTypeInformation =
                globalCache[typeIdentifier] ?:
                localCache.getOrPut(typeIdentifier) { builder(typeModelConfiguration.isOpaque(type)) }

        override fun isExcluded(type: Type): Boolean = typeModelConfiguration.isExcluded(type)

        /**
         * Merge the local cache back into the global cache, once we've finished traversal (and patched all cycles).
         */
        fun merge() {
            localCache.forEach { (identifier, information) ->
                globalCache.putIfAbsent(identifier, information)
            }
        }
    }

    override fun inspect(type: Type, suppressWarnings: Boolean): LocalTypeInformation {
        val typeIdentifier = TypeIdentifier.forGenericType(type)

        return typeInformationCache.getOrPut(typeIdentifier) {
            val lookup = BuilderLookup(typeInformationCache, typeModelConfiguration)
            val result = LocalTypeInformation.forType(type, typeIdentifier, lookup, suppressWarnings)
            lookup.merge()
            result
        }
    }
}

/**
 * Configuration which controls how a [LocalTypeModel] inspects classes to build [LocalTypeInformation].
 */
@KeepForDJVM
interface LocalTypeModelConfiguration {
    /**
     * [Type]s which are flagged as "opaque" are converted into instances of [LocalTypeInformation.Opaque] without
     * further inspection - the type model doesn't attempt to inspect their superclass/interface hierarchy, locate
     * constructors or enumerate their properties. Usually this will be because the type is handled by a custom
     * serializer, so we don't need detailed information about it to help us build one.
     */
    fun isOpaque(type: Type): Boolean

    /**
     * [Type]s which are excluded are silently omitted from the superclass/interface hierarchy of other types'
     * [LocalTypeInformation], usually because they are not included in a whitelist.
     */
    fun isExcluded(type: Type): Boolean
}

@KeepForDJVM
private object NoExclusionNoOpacity : LocalTypeModelConfiguration {
    override fun isOpaque(type: Type): Boolean = false

    override fun isExcluded(type: Type): Boolean = false
}
