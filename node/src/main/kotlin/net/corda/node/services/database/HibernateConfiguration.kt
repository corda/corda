package net.corda.node.services.database

import net.corda.core.internal.castIfPossible
import net.corda.core.node.services.IdentityService
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.converters.AbstractPartyToX500NameAsStringConverter
import net.corda.core.utilities.loggerFor
import net.corda.node.services.api.SchemaService
import net.corda.node.utilities.DatabaseTransactionManager
import net.corda.node.utilities.parserTransactionIsolationLevel
import org.hibernate.HibernateException
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.model.naming.*
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.cfg.Configuration
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.service.UnknownUnwrapTypeException
import java.sql.Connection
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class HibernateConfiguration(createSchemaService: () -> SchemaService, private val databaseProperties: Properties, private val createIdentityScervice: () -> IdentityService) {
    companion object {
        val logger = loggerFor<HibernateConfiguration>()
    }

    // TODO: make this a guava cache or similar to limit ability for this to grow forever.
    private val sessionFactories = ConcurrentHashMap<Set<MappedSchema>, SessionFactory>()

    private val transactionIsolationLevel = parserTransactionIsolationLevel(databaseProperties.getProperty("transactionIsolationLevel") ?:"")
    var schemaService = createSchemaService()

    init {
        logger.info("Init HibernateConfiguration for schemas: ${schemaService.schemaOptions.keys}")
        sessionFactoryForRegisteredSchemas()
    }

    fun sessionFactoryForRegisteredSchemas(): SessionFactory {
        return sessionFactoryForSchemas(*schemaService.schemaOptions.keys.toTypedArray())
    }

    fun sessionFactoryForSchema(schema: MappedSchema): SessionFactory {
        return sessionFactoryForSchemas(schema)
    }

    //vararg to set conversions left to preserve method signature for now
    fun sessionFactoryForSchemas(vararg schemas: MappedSchema): SessionFactory {
        val schemaSet: Set<MappedSchema> = schemas.toSet()
        return sessionFactories.computeIfAbsent(schemaSet, { makeSessionFactoryForSchemas(schemaSet) })
    }

    private fun makeSessionFactoryForSchemas(schemas: Set<MappedSchema>): SessionFactory {
        logger.info("Creating session factory for schemas: $schemas")
        val serviceRegistry = BootstrapServiceRegistryBuilder().build()
        val metadataSources = MetadataSources(serviceRegistry)
        // We set a connection provider as the auto schema generation requires it.  The auto schema generation will not
        // necessarily remain and would likely be replaced by something like Liquibase.  For now it is very convenient though.
        // TODO: replace auto schema generation as it isn't intended for production use, according to Hibernate docs.
        val config = Configuration(metadataSources).setProperty("hibernate.connection.provider_class", HibernateConfiguration.NodeDatabaseConnectionProvider::class.java.name)
                .setProperty("hibernate.hbm2ddl.auto", if (databaseProperties.getProperty("initDatabase","true") == "true") "update" else "validate")
                .setProperty("hibernate.format_sql", "true")
                .setProperty("hibernate.connection.isolation", transactionIsolationLevel.toString())

        val naming = databaseProperties.getProperty("serverNameTablePrefix",null)

        if (naming != null) {
            config.setImplicitNamingStrategy( object : ImplicitNamingStrategyJpaCompliantImpl() {

                override fun determinePrimaryTableName(source: ImplicitEntityNameSource): Identifier {
                    if (source == null) {
                        // should never happen, but to be defensive...
                        throw HibernateException("Entity naming information was not provided.")
                    }

                    val tableName = transformEntityName(source.entityNaming) ?:
                            throw HibernateException("Could not determine primary table name for entity")

                    return toIdentifier(naming + tableName, source.buildingContext)
                }
            })
        }

        schemas.forEach { schema ->
            // TODO: require mechanism to set schemaOptions (databaseSchema, tablePrefix) which are not global to session
            schema.mappedTypes.forEach { config.addAnnotatedClass(it) }
        }
        val sessionFactory = buildSessionFactory(config, metadataSources, "")
        logger.info("Created session factory for schemas: $schemas")
        return sessionFactory
    }

    private fun buildSessionFactory(config: Configuration, metadataSources: MetadataSources, tablePrefix: String): SessionFactory {
        config.standardServiceRegistryBuilder.applySettings(config.properties)
        val metadata = metadataSources.getMetadataBuilder(config.standardServiceRegistryBuilder.build()).run {
            applyPhysicalNamingStrategy(object : PhysicalNamingStrategyStandardImpl() {
                override fun toPhysicalTableName(name: Identifier?, context: JdbcEnvironment?): Identifier {
                    val default = super.toPhysicalTableName(name, context)
                    val naming = databaseProperties.getProperty("serverNameTablePrefix",null)
                    val prefix = if (naming != null) {
                        tablePrefix + naming
                    } else {
                        tablePrefix
                    }
                    return Identifier.toIdentifier(prefix + default.text, default.isQuoted)
                }
            })
            // register custom converters
            applyAttributeConverter(AbstractPartyToX500NameAsStringConverter(createIdentityScervice))

            build()
        }

        return metadata.sessionFactoryBuilder.run {
            allowOutOfTransactionUpdateOperations(true)
            applySecondLevelCacheSupport(false)
            applyQueryCacheSupport(false)
            enableReleaseResourcesOnCloseEnabled(true)
            build()
        }
    }

    // Supply Hibernate with connections from our underlying Exposed database integration.  Only used
    // during schema creation / update.
    class NodeDatabaseConnectionProvider : ConnectionProvider {
        override fun closeConnection(conn: Connection) {
            val tx = DatabaseTransactionManager.current()
            tx.commit()
            tx.close()
        }

        override fun supportsAggressiveRelease(): Boolean = true

        override fun getConnection(): Connection {
            return DatabaseTransactionManager.newTransaction().connection
        }

        override fun <T : Any?> unwrap(unwrapType: Class<T>): T {
            return unwrapType.castIfPossible(this) ?: throw UnknownUnwrapTypeException(unwrapType)
        }

        override fun isUnwrappableAs(unwrapType: Class<*>?): Boolean = unwrapType == NodeDatabaseConnectionProvider::class.java
    }
}