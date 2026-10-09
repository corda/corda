package net.corda.nodeapi.internal.persistence.factory

import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.HibernateConfiguration
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import org.hibernate.SessionFactory
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataBuilder
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.cfg.Configuration
import jakarta.persistence.AttributeConverter

abstract class BaseSessionFactoryFactory : CordaSessionFactoryFactory {
    companion object {
        private val logger = contextLogger()
    }

    /** The JDBC type used for [java.util.UUID]s that have no explicit mapping. See "hibernate.type.preferred_uuid_jdbc_type". */
    protected open val preferredUuidJdbcType: String = "BINARY"

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
                .setProperty("jakarta.persistence.validation.mode", "none")
                .setProperty("hibernate.connection.isolation", TransactionIsolationLevel.default.jdbcValue.toString())
                .setProperty("hibernate.hbm2ddl.auto", hbm2dll)
                .setProperty("hibernate.jdbc.time_zone", "UTC")
                // The remaining properties retain the behaviour of Hibernate 5, so that existing databases (and CorDapps' tables) continue to
                // work without any changes. Hibernate 6 and later changed these defaults.
                //
                // Entities with a plain @GeneratedValue used a single sequence called "hibernate_sequence" with an increment of 1. Hibernate 6+
                // gives each entity its own sequence (e.g. node_infos_SEQ) with an increment of 50.
                .setProperty("hibernate.id.db_structure_naming_strategy", "legacy")
                .setProperty("hibernate.id.sequence.increment_size_mismatch_strategy", "FIX")
                // Instants were stored as a plain TIMESTAMP, normalised to UTC using the "hibernate.jdbc.time_zone" above. Hibernate 6+
                // uses TIMESTAMP WITH TIME ZONE, which is read and written using the session time zone of the database.
                .setProperty("hibernate.type.preferred_instant_jdbc_type", "TIMESTAMP")
                // UUIDs (without an explicit mapping) were stored as BINARY(16) apart from on PostgreSQL, where they were stored as a uuid.
                // Hibernate 6+ uses the database's native uuid type where there is one.
                .setProperty("hibernate.type.preferred_uuid_jdbc_type", preferredUuidJdbcType)
                // Doubles and floats on Oracle were stored as FLOAT. Hibernate 6+ uses BINARY_FLOAT and BINARY_DOUBLE.
                .setProperty("hibernate.dialect.oracle.use_binary_floats", "false")
                // Hibernate 6+ alters existing columns in "update" mode. Only create what is missing as before.
                .setProperty("hibernate.schema_management_tool", CordaSchemaManagementTool::class.java.name)
    }

    override fun buildHibernateMetadata(metadataBuilder: MetadataBuilder, attributeConverters: Collection<AttributeConverter<*, *>>): Metadata {
        return metadataBuilder.run {
            attributeConverters.forEach { applyAttributeConverter(it) }
            build()
        }
    }

    fun buildSessionFactory(
            config: Configuration,
            metadataSources: MetadataSources,
            attributeConverters: Collection<AttributeConverter<*, *>>): SessionFactory {
        config.standardServiceRegistryBuilder.applySettings(config.properties)

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
        // HHH-15693 (Hibernate 5.6.15): ClassLoaderService lookups on the StandardServiceRegistry
        // now fast-path to the parent BootstrapServiceRegistry, bypassing any service registered
        // directly on the StandardServiceRegistryBuilder. Register the custom classloader on the
        // BootstrapServiceRegistryBuilder so it is visible via that fast-path.
        val bootstrapServiceRegistryBuilder = BootstrapServiceRegistryBuilder()
        if (customClassLoader != null) {
            bootstrapServiceRegistryBuilder.applyClassLoader(customClassLoader)
        }
        val serviceRegistry = bootstrapServiceRegistryBuilder.build()
        val metadataSources = MetadataSources(serviceRegistry)

        val config = buildHibernateConfig(metadataSources, allowHibernateToMananageAppSchema)
        schemas.forEach { schema ->
            schema.mappedTypes.forEach {
                checkNotBuiltForJavaxPersistence(schema, it)
                config.addAnnotatedClass(it)
            }
        }
        val sessionFactory = buildSessionFactory(config, metadataSources, attributeConverters)
        logger.info("Created session factory for schemas: $schemas")
        return sessionFactory
    }

    override fun getExtraConfiguration(key: String): Any? {
        return null
    }

    /**
     * Hibernate 7 only understands the annotations of Jakarta Persistence. A class that was built against the `javax.persistence`
     * annotations (i.e. a CorDapp built for Corda 4.14 or earlier) would be silently ignored by Hibernate and then fail in obscure ways,
     * so fail early with a message that explains what to do.
     */
    private fun checkNotBuiltForJavaxPersistence(schema: MappedSchema, type: Class<*>) {
        val classBytes = type.classLoader
                ?.getResourceAsStream(type.name.replace('.', '/') + ".class")
                ?.use { it.readBytes() }
                ?: return
        check(!referencesJavaxPersistence(classBytes)) {
            "The class ${type.name} of the schema ${schema::class.java.name} was built for Corda 4.14 or earlier, as it uses the " +
                    "javax.persistence annotations. Corda 4.15 uses Hibernate 7, which requires jakarta.persistence. " +
                    "The CorDapp that contains the schema needs to be rebuilt for Corda 4.15 - see the \"Upgrading to Hibernate 7\" documentation."
        }
    }
}

/** Whether the bytes of a class file contain a reference to a `javax.persistence` class (constant pool entries are ASCII-compatible). */
internal fun referencesJavaxPersistence(classBytes: ByteArray): Boolean {
    return String(classBytes, Charsets.ISO_8859_1).contains("javax/persistence/")
}
