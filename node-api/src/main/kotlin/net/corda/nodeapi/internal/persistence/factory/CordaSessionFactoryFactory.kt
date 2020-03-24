package net.corda.nodeapi.internal.persistence.factory

import net.corda.core.schemas.MappedSchema
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import org.hibernate.SessionFactory
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataBuilder
import javax.persistence.AttributeConverter

interface CordaSessionFactoryFactory {
    val databaseType: String
    fun canHandleDatabase(jdbcUrl: String): Boolean
    fun makeSessionFactoryForSchemas(
            databaseConfig: DatabaseConfig,
            schemas: Set<MappedSchema>,
            customClassLoader: ClassLoader?,
            attributeConverters: Collection<AttributeConverter<*, *>>): SessionFactory
    fun getExtraConfiguration(key: String): Any?
    fun buildHibernateMetadata(metadataBuilder: MetadataBuilder, attributeConverters: Collection<AttributeConverter<*, *>>): Metadata
}