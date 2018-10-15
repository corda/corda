package net.corda.serialization.internal.model

import net.corda.core.serialization.ClassWhitelist
import net.corda.serialization.internal.amqp.*
import java.lang.reflect.*

class LocalTypeModel(private val typeModelConfiguration: TypeModelConfiguration): LocalTypeLookup {

    private val cache = DefaultCacheProvider.createCache<TypeIdentifier, LocalTypeInformation>()

    override fun isExcluded(type: Type): Boolean = typeModelConfiguration.isExcluded(type)

    fun inspect(type: Type): LocalTypeInformation = LocalTypeInformation.forType(type, this)

    override fun lookup(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation): LocalTypeInformation =
            cache[typeIdentifier] ?: buildIfNotOpaque(type, typeIdentifier, builder).apply {
                cache.putIfAbsent(typeIdentifier, this)
            }

    private fun buildIfNotOpaque(type: Type, typeIdentifier: TypeIdentifier, builder: () -> LocalTypeInformation) =
            if (typeModelConfiguration.isOpaque(type)) LocalTypeInformation.Opaque(type.asClass(), typeIdentifier)
            else builder()


    operator fun get(typeIdentifier: TypeIdentifier): LocalTypeInformation? = cache[typeIdentifier]
}

interface TypeModelConfiguration {
    fun isOpaque(type: Type): Boolean = !type.asClass().isCollectionOrMap && type.typeName.startsWith("java")
    fun isExcluded(type: Type): Boolean
}

class WhitelistBasedTypeModelConfiguration(private val whitelist: ClassWhitelist): TypeModelConfiguration {
    override fun isExcluded(type: Type): Boolean = whitelist.isNotWhitelisted(type.asClass())
}
