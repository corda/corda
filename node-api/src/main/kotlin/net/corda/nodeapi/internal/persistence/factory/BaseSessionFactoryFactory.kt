package net.corda.nodeapi.internal.persistence.factory

import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.toHexString
import net.corda.nodeapi.internal.persistence.HibernateConfiguration
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import org.hibernate.SessionFactory
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataBuilder
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.classloading.internal.ClassLoaderServiceImpl
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService
import org.hibernate.cfg.Configuration
import org.hibernate.type.AbstractSingleColumnStandardBasicType
import org.hibernate.type.MaterializedBlobType
import org.hibernate.type.descriptor.java.PrimitiveByteArrayTypeDescriptor
import org.hibernate.type.descriptor.sql.BlobTypeDescriptor
import org.hibernate.type.descriptor.sql.VarbinaryTypeDescriptor
import javax.persistence.AttributeConverter

abstract class BaseSessionFactoryFactory : CordaSessionFactoryFactory {
    companion object {
        private val logger = contextLogger()
    }

    open fun buildHibernateConfig(metadataSources: MetadataSources, allowHibernateToManageAppSchema: Boolean): Configuration {
        val hbm2dll: String =
                if (allowHibernateToManageAppSchema) {
                    "update"
                } else  {
                    "validate"
                }
        // We set a connection provider as the auto schema generation requires it.  The auto schema generation will not
        // necessarily remain and would likely be replaced by something like Liquibase.  For now it is very convenient though.
        return Configuration(metadataSources).setProperty("hibernate.connection.provider_class", HibernateConfiguration.NodeDatabaseConnectionProvider::class.java.name)
                .setProperty("hibernate.format_sql", "true")
                .setProperty("javax.persistence.validation.mode", "none")
                .setProperty("hibernate.connection.isolation", TransactionIsolationLevel.default.jdbcValue.toString())
                .setProperty("hibernate.hbm2ddl.auto", hbm2dll)
                .setProperty("hibernate.jdbc.time_zone", "UTC")
    }

    override fun buildHibernateMetadata(metadataBuilder: MetadataBuilder, attributeConverters: Collection<AttributeConverter<*, *>>): Metadata {
        return metadataBuilder.run {
            attributeConverters.forEach { applyAttributeConverter(it) }
            // Register a tweaked version of `org.hibernate.type.MaterializedBlobType` that truncates logged messages.
            // to avoid OOM when large blobs might get logged.
            applyBasicType(CordaMaterializedBlobType, CordaMaterializedBlobType.name)
            applyBasicType(CordaWrapperBinaryType, CordaWrapperBinaryType.name)
            applyBasicType(MapBlobToNormalBlob, MapBlobToNormalBlob.name)

            build()
        }
    }

    fun buildSessionFactory(
            config: Configuration,
            metadataSources: MetadataSources,
            customClassLoader: ClassLoader?,
            attributeConverters: Collection<AttributeConverter<*, *>>): SessionFactory {
        config.standardServiceRegistryBuilder.applySettings(config.properties)

        if (customClassLoader != null) {
            config.standardServiceRegistryBuilder.addService(
                    ClassLoaderService::class.java,
                    ClassLoaderServiceImpl(customClassLoader))
        }

        @Suppress("DEPRECATION")
        val metadataBuilder = metadataSources.getMetadataBuilder(config.standardServiceRegistryBuilder.build())
        val metadata = buildHibernateMetadata(metadataBuilder, attributeConverters)
        return metadata.sessionFactoryBuilder.run {
            allowOutOfTransactionUpdateOperations(true)
            applySecondLevelCacheSupport(false)
            applyQueryCacheSupport(false)
            enableReleaseResourcesOnCloseEnabled(true)
            build()
        }
    }

    final override fun makeSessionFactoryForSchemas(
            schemas: Set<MappedSchema>,
            customClassLoader: ClassLoader?,
            attributeConverters: Collection<AttributeConverter<*, *>>,
            allowHibernateToMananageAppSchema: Boolean): SessionFactory {
        logger.info("Creating session factory for schemas: $schemas")
        val serviceRegistry = BootstrapServiceRegistryBuilder().build()
        val metadataSources = MetadataSources(serviceRegistry)

        val config = buildHibernateConfig(metadataSources, allowHibernateToMananageAppSchema)
        schemas.forEach { schema ->
            schema.mappedTypes.forEach { config.addAnnotatedClass(it) }
        }
        val sessionFactory = buildSessionFactory(config, metadataSources, customClassLoader, attributeConverters)
        logger.info("Created session factory for schemas: $schemas")
        return sessionFactory
    }

    override fun getExtraConfiguration(key: String): Any? {
        return null
    }

    // A tweaked version of `org.hibernate.type.WrapperBinaryType` that deals with ByteArray (java primitive byte[] type).
    object CordaWrapperBinaryType : AbstractSingleColumnStandardBasicType<ByteArray>(VarbinaryTypeDescriptor.INSTANCE, PrimitiveByteArrayTypeDescriptor.INSTANCE) {
        override fun getRegistrationKeys(): Array<String> {
            return arrayOf(name, "ByteArray", ByteArray::class.java.name)
        }

        override fun getName(): String {
            return "corda-wrapper-binary"
        }
    }

    object MapBlobToNormalBlob : MaterializedBlobType() {
        override fun getName(): String {
            return "corda-blob"
        }
    }

    // A tweaked version of `org.hibernate.type.descriptor.java.PrimitiveByteArrayTypeDescriptor` that truncates logged messages.
    private object CordaPrimitiveByteArrayTypeDescriptor : PrimitiveByteArrayTypeDescriptor() {
        private const val LOG_SIZE_LIMIT = 1024

        override fun extractLoggableRepresentation(value: ByteArray?): String {
            return if (value == null) {
                super.extractLoggableRepresentation(value)
            } else {
                if (value.size <= LOG_SIZE_LIMIT) {
                    "[size=${value.size}, value=${value.toHexString()}]"
                } else {
                    "[size=${value.size}, value=${value.copyOfRange(0, LOG_SIZE_LIMIT).toHexString()}...truncated...]"
                }
            }
        }
    }

    // A tweaked version of `org.hibernate.type.MaterializedBlobType` that truncates logged messages.  Also logs in hex.
    object CordaMaterializedBlobType : AbstractSingleColumnStandardBasicType<ByteArray>(BlobTypeDescriptor.DEFAULT, CordaPrimitiveByteArrayTypeDescriptor) {
        override fun getName(): String {
            return "materialized_blob"
        }
    }
}