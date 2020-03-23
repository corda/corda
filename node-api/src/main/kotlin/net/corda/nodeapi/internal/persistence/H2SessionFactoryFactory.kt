package net.corda.nodeapi.internal.persistence

import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataBuilder
import org.hibernate.boot.MetadataSources
import org.hibernate.cfg.Configuration
import javax.persistence.AttributeConverter

class H2SessionFactoryFactory : BaseSessionFactoryFactory(){
    override fun buildHibernateConfig(databaseConfig: DatabaseConfig, metadataSources: MetadataSources): Configuration {
        val hbm2dll: String =
                if(databaseConfig.initialiseSchema && databaseConfig.initialiseAppSchema == SchemaInitializationType.UPDATE) {
                    "update"
                } else if((!databaseConfig.initialiseSchema && databaseConfig.initialiseAppSchema == SchemaInitializationType.UPDATE)
                        || databaseConfig.initialiseAppSchema == SchemaInitializationType.VALIDATE) {
                    "validate"
                } else {
                    "none"
                }

        // We set a connection provider as the auto schema generation requires it.  The auto schema generation will not
        // necessarily remain and would likely be replaced by something like Liquibase.  For now it is very convenient though.
        return Configuration(metadataSources).setProperty("hibernate.connection.provider_class", HibernateConfiguration.NodeDatabaseConnectionProvider::class.java.name)
                .setProperty("hibernate.format_sql", "true")
                .setProperty("hibernate.hbm2ddl.auto", hbm2dll)
                .setProperty("javax.persistence.validation.mode", "none")
                .setProperty("hibernate.connection.isolation", databaseConfig.transactionIsolationLevel.jdbcValue.toString())

    }

    override fun buildHibernateMetadata(metadataBuilder: MetadataBuilder, attributeConverters: Collection<AttributeConverter<*, *>>): Metadata {
        metadataBuilder.run {
            attributeConverters.forEach { applyAttributeConverter(it) }
            // Register a tweaked version of `org.hibernate.type.MaterializedBlobType` that truncates logged messages.
            // to avoid OOM when large blobs might get logged.
            applyBasicType(CordaMaterializedBlobType, CordaMaterializedBlobType.name)
            applyBasicType(CordaWrapperBinaryType, CordaWrapperBinaryType.name)
            applyBasicType(MapBlobToNormalBlob, MapBlobToNormalBlob.name)

            return build()
        }
    }


    override fun canHandleDatabase(jdbcUrl: String): Boolean = jdbcUrl.startsWith("jdbc:h2:")
}