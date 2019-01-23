package net.corda.node.migration

import com.codahale.metrics.MetricRegistry
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import net.corda.core.schemas.MappedSchema
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.persistence.AbstractPartyToX500NameAsStringConverter
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.PublicKeyToTextConverter
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger
import javax.sql.DataSource


/*
 * Provide a set of node services for use when migrating items in the database.
 *
 * For more complex migrations, information such as the transaction data may need to be extracted from the database. In order to do this,
 * many node services need to be initialised. This sets up enough of the node services to access items in the database using hibernate
 * queries.
 */
abstract class CordaMigration {

    val identityService: PersistentIdentityService
        get() = _identityService

    private lateinit var _identityService: PersistentIdentityService

    val cordaDB: CordaPersistence
        get() = _cordaDB

    private lateinit var _cordaDB: CordaPersistence

    val dbTransactions: DBTransactionStorage
        get() = _dbTransactions

    private lateinit var _dbTransactions: DBTransactionStorage


    fun initialiseNodeServices(database: Database) {
        val url = (database.connection as JdbcConnection).url
        val dataSource = MigrationDataSource(database)
        val metricRegistry = MetricRegistry()
        val cacheFactory = MigrationNamedCacheFactory(metricRegistry, null)
        _identityService = PersistentIdentityService(cacheFactory)
        _cordaDB = createDatabase(url, cacheFactory, identityService)
        cordaDB.start(dataSource)
        identityService.database = cordaDB

        cordaDB.transaction {
            val myKeystore = BasicHSMKeyManagementService.createKeyMap(cacheFactory)
            identityService.ourNames = myKeystore.allPersisted().mapNotNull { identityService.certificateFromKey(it.first)?.name}.toSet()
            _dbTransactions = DBTransactionStorage(cordaDB, cacheFactory)
        }
    }

    private fun createDatabase(jdbcUrl: String, cacheFactory: MigrationNamedCacheFactory, identityService: PersistentIdentityService): CordaPersistence {
        val configDefaults = DatabaseConfig()
        val attributeConverters = listOf(
                PublicKeyToTextConverter(),
                AbstractPartyToX500NameAsStringConverter(identityService::wellKnownPartyFromX500Name, identityService::wellKnownPartyFromAnonymous))
        return CordaPersistence(configDefaults, setOf(VaultMigrationSchemaV1, VaultSchemaV1), jdbcUrl, cacheFactory, attributeConverters, closeConnection = false)
    }
}

class MigrationDataSource(val database: Database) : DataSource {
    override fun getConnection(): Connection {
        return (database.connection as JdbcConnection).wrappedConnection
    }

    override fun getConnection(username: String?, password: String?): Connection {
        return this.connection
    }

    private var _loginTimeout: Int = 0


    override fun setLoginTimeout(seconds: Int) {
        _loginTimeout = 0
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


object VaultMigrationSchema

object VaultMigrationSchemaV1 : MappedSchema(schemaFamily = VaultMigrationSchema.javaClass, version = 1,
        mappedTypes = listOf(
                DBTransactionStorage.DBTransaction::class.java,
                PersistentIdentityService.PersistentIdentity::class.java,
                PersistentIdentityService.PersistentIdentityNames::class.java,
                BasicHSMKeyManagementService.PersistentKey::class.java
        ))
