package net.corda.serialization.internal.model

import net.corda.core.serialization.ClassWhitelist
import net.corda.serialization.internal.amqp.*
import java.lang.reflect.*

class LocalTypeModel(
        private val typeModelConfiguration: TypeModelConfiguration): LocalTypeLookup {

    private val typeInformationCache = DefaultCacheProvider.createCache<TypeIdentifier, LocalTypeInformation>()

    override fun isExcluded(type: Type): Boolean = typeModelConfiguration.isExcluded(type)

    fun inspect(type: Type): LocalTypeInformation = LocalTypeInformation.forType(type, this)

    override fun lookup(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation): LocalTypeInformation =
            typeInformationCache[typeIdentifier] ?: buildIfNotOpaque(type, typeIdentifier, builder).apply {
                typeInformationCache.putIfAbsent(typeIdentifier, this)
            }

    private fun buildIfNotOpaque(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation) =
            if (typeModelConfiguration.isOpaque(type)) LocalTypeInformation.Opaque(type.asClass(), typeIdentifier)
            else builder()

    operator fun get(typeIdentifier: TypeIdentifier): LocalTypeInformation? = typeInformationCache[typeIdentifier]
}

typealias Fingerprint = String

interface CustomTypeDescriptorLookup {
    fun getCustomTypeDescriptor(type: Type): String?
}

interface TypeModelConfiguration {
    fun isOpaque(type: Type): Boolean
    fun isExcluded(type: Type): Boolean
}

class WhitelistBasedTypeModelConfiguration(
        private val whitelist: ClassWhitelist,
        private val opaqueTest: (Type) -> Boolean = { !it.asClass().isCollectionOrMap && it.typeName.startsWith("java") })
    : TypeModelConfiguration {
    override fun isExcluded(type: Type): Boolean = whitelist.isNotWhitelisted(type.asClass())
    override fun isOpaque(type: Type): Boolean = opaqueTest(type)
}
