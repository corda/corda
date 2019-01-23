package net.corda.node.migration

import com.codahale.metrics.MetricRegistry
import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.containsAny
import net.corda.core.node.services.Vault
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.utilities.contextLogger
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.persistence.AbstractPartyToX500NameAsStringConverter
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.PublicKeyToTextConverter
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.currentDBSession
import org.hibernate.Session
import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger
import javax.sql.DataSource

class VaultStateMigration : CustomTaskChange {
    companion object {
        private val logger = contextLogger()
    }

    override fun validate(database: Database?): ValidationErrors? {
        return null
    }

    override fun setUp() {
        // No setup required.
    }

    override fun setFileOpener(resourceAccessor: ResourceAccessor?) {
        // No file opener required
    }

    override fun getConfirmationMessage(): String? {
        return null
    }

    private fun createDatabase(jdbcUrl: String, cacheFactory: MigrationNamedCacheFactory, identityService: PersistentIdentityService): CordaPersistence {
        val configDefaults = DatabaseConfig()
        val attributeConverters = listOf(
                PublicKeyToTextConverter(),
                AbstractPartyToX500NameAsStringConverter(identityService::wellKnownPartyFromX500Name, identityService::wellKnownPartyFromAnonymous))
        return CordaPersistence(configDefaults, setOf(VaultMigrationSchemaV1, VaultSchemaV1), jdbcUrl, cacheFactory, attributeConverters)
    }

    lateinit var cordaDB: CordaPersistence

    private fun getVaultStates(session: Session): List<VaultSchemaV1.VaultStates> {
        return cordaDB.transaction {
            val criteriaQuery = cordaDB.entityManagerFactory.criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
            val queryRootStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
            criteriaQuery.select(queryRootStates)
            val query = session.createQuery(criteriaQuery)

            //TODO: Paging?

            query.resultList
        }
    }

    private fun addStateParties(session: Session, stateAndRef: StateAndRef<ContractState>) {
        val state = stateAndRef.state.data
        val persistentStateRef = PersistentStateRef(stateAndRef.ref)
        state.participants.groupBy { it.owningKey }.forEach { participants ->
            val persistentParty = VaultSchemaV1.PersistentParty(persistentStateRef, participants.value.first())
            session.save(persistentParty)
        }
    }

    private fun getStateAndRef(dbTransactions: DBTransactionStorage,
                               persistentState: VaultSchemaV1.VaultStates): StateAndRef<ContractState> {
        val txHash = SecureHash.parse(persistentState.stateRef!!.txId)
        val tx = dbTransactions.getTransaction(txHash)!!
        val state = tx.coreTransaction.outputs[persistentState.stateRef!!.index]
        val stateRef = StateRef(txHash, persistentState.stateRef!!.index)
        return StateAndRef(state, stateRef)
    }

    private fun persistentStateToStateRef(persistentState: VaultSchemaV1.VaultStates): StateRef {
        return StateRef(SecureHash.parse(persistentState.stateRef!!.txId), persistentState.stateRef!!.index)
    }

    override fun execute(database: Database?) {
        logger.info("Migrating vault state data to V4 tables")
        val url = (database?.connection as JdbcConnection).url
        logger.info("Database URL is $url")
        val dataSource = MigrationDataSource(database)
        val metricRegistry = MetricRegistry()
        val cacheFactory = MigrationNamedCacheFactory(metricRegistry, null)
        val identities = PersistentIdentityService(cacheFactory)
        cordaDB = createDatabase(url, cacheFactory, identities)
        cordaDB.start(dataSource)

        cordaDB.transaction {
            identities.database = cordaDB
            val myKeystore = BasicHSMKeyManagementService.createKeyMap(cacheFactory)
            identities.ourNames = myKeystore.allPersisted().mapNotNull { identities.certificateFromKey(it.first)?.name}.toSet()
            val dbTransactions = DBTransactionStorage(cordaDB, cacheFactory)

            val session = currentDBSession()
            val persistentStates = getVaultStates(session)
            val refPersistentMapping = persistentStates.map { Pair(persistentStateToStateRef(it), it) }.toMap()
            val states = persistentStates.map { getStateAndRef(dbTransactions, it)}

            logger.debug("Adding all states to state party table")
            states.forEach { addStateParties(session, it) }

            logger.debug("Updating not relevant states in the vault")
            states.filter {
                val myKeys = identities.stripNotOurKeys(it.state.data.participants.map { it.owningKey })
                //TODO: Refactor isRelevant to be visible. Do we need KeyManagementService to do this?
                !it.state.data.participants.map {it.owningKey}.any { it.containsAny(myKeys)}
            }.forEach {
                refPersistentMapping[it.ref]?.relevancyStatus = Vault.RelevancyStatus.NOT_RELEVANT
            }
        }
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