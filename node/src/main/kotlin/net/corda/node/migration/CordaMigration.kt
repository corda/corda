package net.corda.node.migration

import com.codahale.metrics.MetricRegistry
import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import net.corda.core.schemas.MappedSchema
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.persistence.AbstractPartyToX500NameAsStringConverter
import net.corda.node.services.persistence.PublicKeyToTextConverter
import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * Provide a set of node services for use when migrating items in the database.
 *
 * For more complex migrations, information such as the transaction data may need to be extracted from the database. In order to do this,
 * some node services need to be initialised. This sets up enough of the node services to access items in the database using hibernate
 * queries.
 */
abstract class CordaMigration : CustomTaskChange {
    val identityService: PersistentIdentityService
        get() = _identityService

    private lateinit var _identityService: PersistentIdentityService

    val cordaDB: CordaPersistence
        get() = _cordaDB

    private lateinit var _cordaDB: CordaPersistence

    /**
     * Initialise a subset of node services so that data from these can be used to perform migrations.
     *
     * This function should not be called unless the NODE_X500_NAME property is set (which should happen
     * as part of running migrations via the SchemaMigration class).
     */
    fun initialiseNodeServices(database: Database,
                               schema: Set<MappedSchema>) {
        val url = (database.connection as JdbcConnection).url
        val dataSource = MigrationDataSource(database)
        val metricRegistry = MetricRegistry()
        val cacheFactory = MigrationNamedCacheFactory(metricRegistry, null)
        _identityService = PersistentIdentityService(cacheFactory)
        _cordaDB = createDatabase(url, cacheFactory, identityService, schema)
        cordaDB.start(dataSource)
        identityService.database = cordaDB
    }

    private fun createDatabase(jdbcUrl: String,
                               cacheFactory: MigrationNamedCacheFactory,
                               identityService: PersistentIdentityService,
                               schema: Set<MappedSchema>): CordaPersistence {
        val attributeConverters = listOf(
                PublicKeyToTextConverter(),
                AbstractPartyToX500NameAsStringConverter(
                        identityService::wellKnownPartyFromX500Name,
                        identityService::wellKnownPartyFromAnonymous)
        )
        // Liquibase handles closing the database connection when migrations are finished. If the connection is closed here, then further
        // migrations may fail.
        return CordaPersistence(false, schema, jdbcUrl, cacheFactory, attributeConverters, closeConnection = false)
    }

    override fun validate(database: Database?): ValidationErrors? {
        return null
    }

    override fun setUp() {
    }

    override fun setFileOpener(resourceAccessor: ResourceAccessor?) {
    }

    override fun getConfirmationMessage(): String? {
        return null
    }
}

/**
 * Wrap the liquibase database as a DataSource, so it can be used with a CordaPersistence instance
 */
class MigrationDataSource(val database: Database) : DataSource {
    override fun getConnection(): Connection {
        return (database.connection as JdbcConnection).wrappedConnection
    }

    override fun getConnection(username: String?, password: String?): Connection {
        return this.connection
    }

    private var _loginTimeout: Int = 0


    override fun setLoginTimeout(seconds: Int) {
        _loginTimeout = seconds
    }

    override fun getLoginTimeout(): Int {
        return _loginTimeout
    }

    override fun setLogWriter(out: PrintWriter?) {
        // No implementation required.
    }

    override fun isWrapperFor(iface: Class<*>?): Boolean {
        return this.connection.isWrapperFor(iface)
    }

    override fun getLogWriter(): PrintWriter? {
        return null
    }

    override fun <T : Any?> unwrap(iface: Class<T>?): T {
        return this.connection.unwrap(iface)
    }

    override fun getParentLogger(): Logger {
        throw SQLFeatureNotSupportedException()
    }
}

class MigrationException(msg: String?, cause: Exception? = null): Exception(msg, cause)
