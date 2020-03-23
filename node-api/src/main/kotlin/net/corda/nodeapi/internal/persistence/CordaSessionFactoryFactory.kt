package net.corda.nodeapi.internal.persistence

import net.corda.core.schemas.MappedSchema
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import javax.persistence.AttributeConverter

interface CordaSessionFactoryFactory {
    fun canHandleDatabase( jdbcUrl: String): Boolean
    fun makeSessionFactoryForSchemas(
            databaseConfig: DatabaseConfig,
            schemas: Set<MappedSchema>,
            customClassLoader: ClassLoader?,
            attributeConverters: Collection<AttributeConverter<*, *>>) : SessionFactory
}