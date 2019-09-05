package net.corda.notary.standalonejpa

import com.codahale.metrics.MetricRegistry
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.hibernate.jpa.HibernatePersistenceProvider
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*
import javax.persistence.EntityManagerFactory
import javax.persistence.SharedCacheMode
import javax.persistence.ValidationMode
import javax.persistence.spi.ClassTransformer
import javax.persistence.spi.PersistenceUnitInfo
import javax.persistence.spi.PersistenceUnitTransactionType
import javax.sql.DataSource

/**
 * HibernateEntityManagerFactoryProvider provides methods to construct a data source as well as an EntityManagerFactory using that DataSource
 */
object HibernateEntityManagerFactoryProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun isH2Database(jdbcUrl: String) = jdbcUrl.startsWith("jdbc:h2:")

    /**
     * Create a Hikari pool data source, which will then be used to interface with the actual database.
     * Note that any unsupported data source properties will cause an exception to be thrown.
     */
    fun createDataSource(hikariProperties: Properties, metricRegistry: MetricRegistry? = null): DataSource {
        val path = System.getProperty("user.dir")
        println("Working Directory = $path")
        log.info("Working Directory = $path")
        val config = HikariConfig(hikariProperties)
        val dataSource = HikariDataSource(config)
        if (metricRegistry != null) {
            dataSource.metricRegistry = metricRegistry
        }
        return dataSource
    }

    /**
     * Create an EntityManagerFactory from the specified data source, data source properties and database configuration
     * The property datasource.url (which represents the JDBC Url) is required.
     */
    fun createEntityManagerFactory(dataSource: DataSource, dataSourceProperties: Properties, databaseConfig: StandaloneJPANotaryDatabaseConfig, maxBatchSize: Int): EntityManagerFactory {
        val jdbcUrl: String = dataSourceProperties.getProperty("dataSource.url")
        //Unfortunately, this line needs to remain here in order for H2 to work for the JPA notary
        //What seems to be happening is that Hibernate does not attempt to open its own connection to the data source
        //if one is present, it creates the necessary tables. If not, it silently does nothing and then
        //complains that the tables do not exist
        if (isH2Database(jdbcUrl)) {
            val connection = dataSource.connection
        }
        if (databaseConfig.initialiseSchema == SchemaInitializationType.NONE) {
            dataSourceProperties.setProperty("hibernate.hbm2ddl.auto", "none")
        } else if (databaseConfig.initialiseSchema == SchemaInitializationType.VALIDATE || (!isH2Database(jdbcUrl) && databaseConfig.initialiseSchema == SchemaInitializationType.UPDATE_H2_ONLY)) {
            dataSourceProperties.setProperty("hibernate.hbm2ddl.auto", "validate")
        } else if ((isH2Database(jdbcUrl) && databaseConfig.initialiseSchema == SchemaInitializationType.UPDATE_H2_ONLY) || databaseConfig.initialiseSchema == SchemaInitializationType.UPDATE) {
            dataSourceProperties.setProperty("hibernate.hbm2ddl.auto", "update")
        }

        databaseConfig.schema?.apply {
            // Enterprise only - preserving case-sensitive schema name for PostgreSQL by wrapping in double quotes, schema without double quotes would be treated as case-insensitive (lower cases)
            val schemaName = if (jdbcUrl.contains(":postgresql:", ignoreCase = true) && databaseConfig.schema?.startsWith("\"") == false) {
                "\"" + databaseConfig.schema + "\""
            } else {
                databaseConfig.schema
            }
            dataSourceProperties.setProperty("hibernate.default_schema", schemaName)
        }

        databaseConfig.hibernateDialect?.apply {
            dataSourceProperties.setProperty("hibernate.dialect", this)
        }

        dataSourceProperties.setProperty("hibernate.jdbc.batch_size", maxBatchSize.toString())
        dataSourceProperties.setProperty("hibernate.jdbc.use_streams_for_binary", "false")

        val persistenceOptions = PersistenceOptions(dataSource, dataSourceProperties)
        return EntityManagerFactoryBuilderImpl(PersistenceUnitInfoDescriptor(persistenceOptions), null).build()
    }
}

/**
 * PersistenceOptions represents the configuration passed into Hibernate to build an EntityManagerFactory
 * It includes all mapped tables, as well as the data source itself
 * This approach allows us to pass in a DataSource of our choice without needing a ThreadLocal to refer to it.
 */
class PersistenceOptions(private val dataSource: DataSource, private val dataSourceProperties: Properties) : PersistenceUnitInfo {

    override fun getJtaDataSource(): DataSource {
        return dataSource
    }

    override fun getNonJtaDataSource(): DataSource {
        return dataSource
    }

    override fun getMappingFileNames(): MutableList<String> {
        return mutableListOf()
    }

    override fun getNewTempClassLoader(): ClassLoader {
        return PersistenceOptions::class.java.classLoader
    }

    override fun getPersistenceUnitName(): String {
        return PersistenceOptions::class.java.canonicalName
    }

    override fun getSharedCacheMode(): SharedCacheMode {
        return SharedCacheMode.NONE
    }

    override fun getClassLoader(): ClassLoader {
        return PersistenceOptions::class.java.classLoader
    }

    override fun getTransactionType(): PersistenceUnitTransactionType {
        return PersistenceUnitTransactionType.RESOURCE_LOCAL
    }

    override fun getProperties(): Properties {
        return dataSourceProperties
    }

    override fun getPersistenceXMLSchemaVersion(): String {
        return "1.0"
    }

    override fun addTransformer(transformer: ClassTransformer?) {
        
    }

    override fun getManagedClassNames(): MutableList<String> {
        return mutableListOf(CommittedState::class.java.canonicalName, CommittedTransaction::class.java.canonicalName, Request::class.java.canonicalName)
    }

    override fun getJarFileUrls(): MutableList<URL> {
        return mutableListOf()
    }

    override fun getPersistenceProviderClassName(): String {
        return HibernatePersistenceProvider::class.java.canonicalName
    }

    override fun excludeUnlistedClasses(): Boolean {
        //This should be true so that Hibernate only maps specified classes listed in getManagedClassNames above
        //Setting this to false will result in Hibernate searching through the JAR and mapping all tables, which results in the tables above being mapped twice
        return true
    }

    override fun getValidationMode(): ValidationMode {
        return ValidationMode.AUTO
    }

    override fun getPersistenceUnitRootUrl(): URL {
        return PersistenceOptions::class.java.getProtectionDomain().getCodeSource().getLocation()
    }
}