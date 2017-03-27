package net.corda.node.services.schema

import kotlinx.support.jdk7.use
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.node.services.VaultService
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.schemas.QueryableState
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.node.services.api.SchemaService
import org.hibernate.SessionFactory
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
import org.hibernate.cfg.Configuration
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.service.UnknownUnwrapTypeException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

/**
 * A vault observer that extracts Object Relational Mappings for contract states that support it, and persists them with Hibernate.
 */
// TODO: Manage version evolution of the schemas via additional tooling.
class HibernateObserver(vaultService: VaultService, val schemaService: SchemaService) {
    companion object {
        val logger = loggerFor<HibernateObserver>()
    }

    // TODO: make this a guava cache or similar to limit ability for this to grow forever.
    val sessionFactories = ConcurrentHashMap<MappedSchema, SessionFactory>()

    init {
        schemaService.schemaOptions.map { it.key }.forEach {
            makeSessionFactoryForSchema(it)
        }
        vaultService.rawUpdates.subscribe { persist(it.produced) }
    }

    private fun sessionFactoryForSchema(schema: MappedSchema): SessionFactory {
        return sessionFactories.computeIfAbsent(schema, { makeSessionFactoryForSchema(it) })
    }

    private fun makeSessionFactoryForSchema(schema: MappedSchema): SessionFactory {
        logger.info("Creating session factory for schema $schema")
        // We set a connection provider as the auto schema generation requires it.  The auto schema generation will not
        // necessarily remain and would likely be replaced by something like Liquibase.  For now it is very convenient though.
        // TODO: replace auto schema generation as it isn't intended for production use, according to Hibernate docs.
        val config = Configuration().setProperty("hibernate.connection.provider_class", NodeDatabaseConnectionProvider::class.java.name)
                .setProperty("hibernate.hbm2ddl.auto", "update")
                .setProperty("hibernate.show_sql", "false")
                .setProperty("hibernate.format_sql", "true")
        val options = schemaService.schemaOptions[schema]
        val databaseSchema = options?.databaseSchema
        if (databaseSchema != null) {
            logger.debug { "Database schema = $databaseSchema" }
            config.setProperty("hibernate.default_schema", databaseSchema)
        }
        val tablePrefix = options?.tablePrefix ?: "contract_" // We always have this as the default for aesthetic reasons.
        logger.debug { "Table prefix = $tablePrefix" }
        config.setPhysicalNamingStrategy(object : PhysicalNamingStrategyStandardImpl() {
            override fun toPhysicalTableName(name: Identifier?, context: JdbcEnvironment?): Identifier {
                val default = super.toPhysicalTableName(name, context)
                return Identifier.toIdentifier(tablePrefix + default.text, default.isQuoted)
            }
        })
        schema.mappedTypes.forEach { config.addAnnotatedClass(it) }
        val sessionFactory = config.buildSessionFactory()
        logger.info("Created session factory for schema $schema")
        return sessionFactory
    }

    private fun persist(produced: Set<StateAndRef<ContractState>>) {
        produced.forEach { persistState(it) }
    }

    private fun persistState(stateAndRef: StateAndRef<ContractState>) {
        val state = stateAndRef.state.data
        if (state is QueryableState) {
            logger.debug { "Asked to persist state ${stateAndRef.ref}" }
            schemaService.selectSchemas(state).forEach { persistStateWithSchema(state, stateAndRef.ref, it) }
        }
    }

    private fun persistStateWithSchema(state: QueryableState, stateRef: StateRef, schema: MappedSchema) {
        val sessionFactory = sessionFactoryForSchema(schema)
        val session = sessionFactory.openStatelessSession(TransactionManager.current().connection)
        session.use {
            val mappedObject = schemaService.generateMappedObject(state, schema)
            mappedObject.stateRef = PersistentStateRef(stateRef)
            session.insert(mappedObject)
        }
    }

    // Supply Hibernate with connections from our underlying Exposed database integration.  Only used
    // during schema creation / update.
    class NodeDatabaseConnectionProvider : ConnectionProvider {
        override fun closeConnection(conn: Connection) {
            val tx = TransactionManager.current()
            tx.commit()
            tx.close()
        }

        override fun supportsAggressiveRelease(): Boolean = true

        override fun getConnection(): Connection {
            val tx = TransactionManager.manager.newTransaction(Connection.TRANSACTION_REPEATABLE_READ)
            return tx.connection
        }

        override fun <T : Any?> unwrap(unwrapType: Class<T>): T {
            try {
                return unwrapType.cast(this)
            } catch(e: ClassCastException) {
                throw UnknownUnwrapTypeException(unwrapType)
            }
        }

        override fun isUnwrappableAs(unwrapType: Class<*>?): Boolean = (unwrapType == NodeDatabaseConnectionProvider::class.java)
    }
}
