package net.corda.serialization.internal.model

import net.corda.core.serialization.ClassWhitelist
import net.corda.serialization.internal.amqp.*
import java.lang.reflect.*

/**
 * A [LocalTypeModel] maintains a registry of [LocalTypeInformation] for all [Type]s which have been observed within a
 * given classloader context.
 */
interface LocalTypeModel : LocalTypeLookup {
    /**
     * Look for a [Type] in the registry, and return its associated [LocalTypeInformation] if found. If the [Type] is
     * not in the registry, build [LocalTypeInformation] for that type, using this [LocalTypeModel] as the [LocalTypeLookup]
     * for recursively resolving dependencies, place it in the registry and return it.
     *
     * @param type The [Type] to get [LocalTypeInformation] for.
     */
    fun inspect(type: Type): LocalTypeInformation

    /**
     * Get [LocalTypeInformation] directly from the registry by [TypeIdentifier], returning null if no type information
     * is registered for that identifier.
     */
    operator fun get(typeIdentifier: TypeIdentifier): LocalTypeInformation?
}

/**
 * A [LocalTypeLookup] that is configurable with [LocalTypeModelConfiguration], which controls which types are seen as "opaque"
 * and which are "excluded" (see docs for [LocalTypeModelConfiguration] for explanation of these terms.
 *
 * @param typeModelConfiguration Configuration controlling the behaviour of the [LocalTypeModel]'s type inspection.
 */
class ConfigurableLocalTypeModel(private val typeModelConfiguration: LocalTypeModelConfiguration): LocalTypeModel {

    private val typeInformationCache = DefaultCacheProvider.createCache<TypeIdentifier, LocalTypeInformation>()

    override fun isExcluded(type: Type): Boolean = typeModelConfiguration.isExcluded(type)

    override fun inspect(type: Type): LocalTypeInformation = LocalTypeInformation.forType(type, this)

    override fun lookup(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation): LocalTypeInformation =
            this[typeIdentifier] ?: buildIfNotOpaque(type, typeIdentifier, builder).apply {
                typeInformationCache.putIfAbsent(typeIdentifier, this)
            }

    private fun buildIfNotOpaque(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation) =
            if (typeModelConfiguration.isOpaque(type)) LocalTypeInformation.Opaque(type.asClass(), typeIdentifier)
            else builder()

    override operator fun get(typeIdentifier: TypeIdentifier): LocalTypeInformation? = typeInformationCache[typeIdentifier]
}

/**
 * Configuration which controls how a [LocalTypeModel] inspects classes to build [LocalTypeInformation].
 */
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

/**
 * [LocalTypeModelConfiguration] based on a [ClassWhitelist]
 */
class WhitelistBasedTypeModelConfiguration(
        private val whitelist: ClassWhitelist,
        private val opaqueTest: (Type) -> Boolean = { !it.asClass().isCollectionOrMap && it.typeName.startsWith("java") })
    : LocalTypeModelConfiguration {
    override fun isExcluded(type: Type): Boolean = whitelist.isNotWhitelisted(type.asClass())
    override fun isOpaque(type: Type): Boolean = opaqueTest(type)
}
