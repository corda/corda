package net.corda.nodeapi.internal.persistence

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.castIfPossible
import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.factory.CordaSessionFactoryFactory
import org.hibernate.SessionFactory
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataBuilder
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.service.UnknownUnwrapTypeException
import java.lang.management.ManagementFactory
import java.sql.Connection
import java.util.ServiceLoader
import javax.management.ObjectName
import javax.persistence.AttributeConverter

class HibernateConfiguration(
        schemas: Set<MappedSchema>,
        private val exportHibernateJMXStatistics: Boolean,
        private val attributeConverters: Collection<AttributeConverter<*, *>>,
        jdbcUrl: String,
        cacheFactory: NamedCacheFactory,
        val customClassLoader: ClassLoader? = null,
        val allowHibernateToManageAppSchema: Boolean = false
) {
    companion object {
        private val logger = contextLogger()

        // Will be used in open core
        fun buildHibernateMetadata(metadataBuilder: MetadataBuilder, jdbcUrl: String, attributeConverters: Collection<AttributeConverter<*, *>>): Metadata {
            val sff = findSessionFactoryFactory(jdbcUrl, null)
            return sff.buildHibernateMetadata(metadataBuilder, attributeConverters)
        }

        private fun findSessionFactoryFactory(jdbcUrl: String, customClassLoader: ClassLoader?): CordaSessionFactoryFactory {
            val serviceLoader = if (customClassLoader != null)
                ServiceLoader.load(CordaSessionFactoryFactory::class.java, customClassLoader)
            else
                ServiceLoader.load(CordaSessionFactoryFactory::class.java)

            val sessionFactories = serviceLoader.filter { it.canHandleDatabase(jdbcUrl) }
            when (sessionFactories.size) {
                0 -> throw HibernateConfigException("Failed to find a SessionFactoryFactory to handle $jdbcUrl " +
                        "- factories present for ${serviceLoader.map { it.databaseType }}")
                1 -> return sessionFactories.single()
                else -> throw HibernateConfigException("Found several SessionFactoryFactory classes to handle $jdbcUrl " +
                        "- classes ${sessionFactories.map { it.javaClass.canonicalName }}")
            }
        }
    }

    val sessionFactoryFactory = findSessionFactoryFactory(jdbcUrl, customClassLoader)

    private val sessionFactories = cacheFactory.buildNamed<Set<MappedSchema>, SessionFactory>(Caffeine.newBuilder(), "HibernateConfiguration_sessionFactories")

    val sessionFactoryForRegisteredSchemas = schemas.let {
        logger.info("Init HibernateConfiguration for schemas: $it")
        sessionFactoryForSchemas(it)
    }

    /** @param key must be immutable, not just read-only. */
    fun sessionFactoryForSchemas(key: Set<MappedSchema>): SessionFactory = sessionFactories.get(key, ::makeSessionFactoryForSchemas)!!

    private fun makeSessionFactoryForSchemas(schemas: Set<MappedSchema>): SessionFactory {
        val sessionFactory = sessionFactoryFactory.makeSessionFactoryForSchemas(schemas, customClassLoader, attributeConverters, allowHibernateToManageAppSchema)

        // export Hibernate JMX statistics
        if (exportHibernateJMXStatistics)
            initStatistics(sessionFactory)

        return sessionFactory
    }

    // NOTE: workaround suggested to overcome deprecation of StatisticsService (since Hibernate v4.0)
    // https://stackoverflow.com/questions/23606092/hibernate-upgrade-statisticsservice
    private fun initStatistics(sessionFactory: SessionFactory) {
        val statsName = ObjectName("org.hibernate:type=statistics")
        val mbeanServer = ManagementFactory.getPlatformMBeanServer()

        val statisticsMBean = DelegatingStatisticsService(sessionFactory.statistics)
        statisticsMBean.isStatisticsEnabled = true

        try {
            mbeanServer.registerMBean(statisticsMBean, statsName)
        } catch (e: Exception) {
            logger.warn(e.message)
        }
    }

    // Supply Hibernate with connections from our underlying Exposed database integration.  Only used
    // during schema creation / update.
    class NodeDatabaseConnectionProvider : ConnectionProvider {
        override fun closeConnection(conn: Connection) {
            conn.autoCommit = false
            contextTransaction.run {
                commit()
                close()
            }
        }

        override fun supportsAggressiveRelease(): Boolean = true

        override fun getConnection(): Connection {
            return contextDatabase.newTransaction().connection
        }

        override fun <T : Any?> unwrap(unwrapType: Class<T>): T {
            return unwrapType.castIfPossible(this) ?: throw UnknownUnwrapTypeException(unwrapType)
        }

        override fun isUnwrappableAs(unwrapType: Class<*>?): Boolean = unwrapType == NodeDatabaseConnectionProvider::class.java
    }

    fun getExtraConfiguration(key: String ) = sessionFactoryFactory.getExtraConfiguration(key)
}
