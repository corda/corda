package net.corda.nodeapi.internal.persistence.factory

import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.HibernateConfiguration
import net.corda.nodeapi.internal.persistence.JavaxPersistenceTransformer
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
    protected open val preferredUuidJdbcType: String = HibernateCompatibility.DEFAULT_UUID_JDBC_TYPE

    open fun buildHibernateConfig(metadataSources: MetadataSources, allowHibernateToManageAppSchema: Boolean): Configuration {
        val hbm2dll: String =
                if (allowHibernateToManageAppSchema) {
                    "update"
                } else  {
                    "validate"
                }
        // We set a connection provider as the auto schema generation requires it.  The auto schema generation will not
        // necessarily remain and would likely be replaced by something like Liquibase.  For now it is very convenient though.
        val config = Configuration(metadataSources).setProperty("hibernate.connection.provider_class", HibernateConfiguration.NodeDatabaseConnectionProvider::class.java.name)
                .setProperty("hibernate.format_sql", "true")
                .setProperty("jakarta.persistence.validation.mode", "none")
                .setProperty("hibernate.connection.isolation", TransactionIsolationLevel.default.jdbcValue.toString())
                .setProperty("hibernate.hbm2ddl.auto", hbm2dll)
                .setProperty("hibernate.jdbc.time_zone", "UTC")

        // Retain the behaviour of Hibernate 5, so that existing databases continue to work without any changes.
        HibernateCompatibility.settings(preferredUuidJdbcType).forEach { (name, value) -> config.setProperty(name, value) }

        return config
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
                checkConvertedIfBuiltForJavaxPersistence(schema, it)
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
     * annotations only (i.e. a CorDapp built for Corda 4.14 or earlier) is converted to `jakarta.persistence` by the node's CorDapp class
     * loader as it is loaded ([JavaxPersistenceTransformer]), which shows in the annotations of the loaded class. A class whose class file
     * is built for `javax.persistence` but whose loaded form is not converted did not come through that class loader, which happens when
     * the CorDapp's classes are on the node's own classpath (MockNetwork and driver tests); Hibernate would silently ignore such a class
     * and then fail in obscure ways, so fail early with a message that explains what to do.
     *
     * A class that carries both sets of annotations (a CorDapp built to run on Corda 4.14 and 4.15 alike) is left alone by the class
     * loader and accepted here: Hibernate reads the `jakarta.persistence` annotations and the JVM ignores the `javax.persistence` ones,
     * whose classes are not on the classpath.
     */
    private fun checkConvertedIfBuiltForJavaxPersistence(schema: MappedSchema, type: Class<*>) {
        val classBytes = type.classLoader
                ?.getResourceAsStream(type.name.replace('.', '/') + ".class")
                ?.use { it.readBytes() }
                ?: return
        if (!JavaxPersistenceTransformer.isBuiltForJavaxPersistenceOnly(classBytes)) return
        check(type.annotations.any { it.annotationClass.java.name.startsWith("jakarta.persistence.") }) {
            "The class ${type.name} of the schema ${schema::class.java.name} was built for Corda 4.14 or earlier, as it uses the " +
                    "javax.persistence annotations, and was not loaded through the node's CorDapp class loader, which converts such " +
                    "classes for Hibernate 7 (Corda 4.15). This happens when the CorDapp's classes are on the node's own classpath, " +
                    "for example in a MockNetwork or driver test. Build the CorDapp for Corda 4.15 to test it there - see the " +
                    "\"Upgrading to Hibernate 7\" documentation."
        }
        logger.info("The class ${type.name} of the schema ${schema::class.java.name} was built for Corda 4.14 or earlier " +
                "(javax.persistence) and has been converted for Hibernate 7 by the CorDapp class loader")
    }
}
