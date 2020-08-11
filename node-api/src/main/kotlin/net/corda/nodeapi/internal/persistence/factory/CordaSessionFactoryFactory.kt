package net.corda.nodeapi.internal.persistence.factory

import net.corda.core.schemas.MappedSchema
import org.hibernate.SessionFactory
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataBuilder
import javax.persistence.AttributeConverter

interface CordaSessionFactoryFactory {
    val databaseType: String
    fun canHandleDatabase(jdbcUrl: String): Boolean
    fun makeSessionFactoryForSchemas(
            schemas: Set<MappedSchema>,
            customClassLoader: ClassLoader?,
            attributeConverters: Collection<AttributeConverter<*, *>>,
            allowHibernateToMananageAppSchema: Boolean): SessionFactory
    fun getExtraConfiguration(key: String): Any?
    fun buildHibernateMetadata(metadataBuilder: MetadataBuilder, attributeConverters: Collection<AttributeConverter<*, *>>): Metadata
}