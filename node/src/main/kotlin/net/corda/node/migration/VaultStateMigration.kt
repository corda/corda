package net.corda.node.migration

import liquibase.database.Database
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.Vault
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._allEnabledSerializationEnvs
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.utilities.contextLogger
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.nodeapi.internal.persistence.currentDBSession
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_STORAGE_CONTEXT
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.amqpMagic
import org.hibernate.Session
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.RecursiveAction

class VaultStateMigration : CordaMigration() {
    companion object {
        private val logger = contextLogger()
    }

    private fun addStateParties(session: Session, stateAndRef: StateAndRef<ContractState>) {
        val state = stateAndRef.state.data
        val persistentStateRef = PersistentStateRef(stateAndRef.ref)
        try {
            state.participants.groupBy { it.owningKey }.forEach { participants ->
                val persistentParty = VaultSchemaV1.PersistentParty(persistentStateRef, participants.value.first())
                session.persist(persistentParty)
            }
        } catch (e: AbstractMethodError) {
            throw VaultStateMigrationException("Cannot add state parties as state class is not on the classpath " +
                    "and participants cannot be synthesised")
        }
    }

    private fun getStateAndRef(persistentState: VaultSchemaV1.VaultStates): StateAndRef<ContractState> {
        val persistentStateRef = persistentState.stateRef ?:
                throw VaultStateMigrationException("Persistent state ref missing from state")
        val txHash = SecureHash.parse(persistentStateRef.txId)
        val tx = dbTransactions.getTransaction(txHash) ?:
                throw VaultStateMigrationException("Transaction $txHash not present in vault")
        val state = tx.coreTransaction.outputs[persistentStateRef.index]
        val stateRef = StateRef(txHash, persistentStateRef.index)
        return StateAndRef(state, stateRef)
    }

    override fun execute(database: Database?) {
        logger.info("Migrating vault state data to V4 tables")
        if (database == null) {
            logger.warn("Cannot migrate vault states: Liquibase failed to provide a suitable database connection")
            return
        }
        effectiveSerializationEnv.serializationFactory.withCurrentContext(effectiveSerializationEnv.storageContext.withLenientCarpenter()) {
            initialiseNodeServices(database, setOf(VaultMigrationSchemaV1, VaultSchemaV1))

            val myKeys = cordaDB.transaction {
                identityService.ourNames.mapNotNull { identityService.wellKnownPartyFromX500Name(it)?.owningKey }.toSet()
            }

            val persistentStates = VaultStateIterator(cordaDB)
            persistentStates.parallelForEach {
                val session = currentDBSession()
                try {
                    val stateAndRef = getStateAndRef(it)

                    addStateParties(session, stateAndRef)

                    // Can get away without checking for AbstractMethodErrors here as these will have already occurred when trying to add
                    // state parties.
                    if (!NodeVaultService.isRelevant(stateAndRef.state.data, myKeys)) {
                        it.relevancyStatus = Vault.RelevancyStatus.NOT_RELEVANT
                        session.merge(it)
                    }
                } catch (e: VaultStateMigrationException) {
                    logger.warn("An error occurred while migrating a vault state: ${e.message}. Skipping")
                }
            }
            logger.info("Finished performing vault state data migration for ${persistentStates.numStates} states")
        }
    }
}


/*
 * A minimal set of schema for retrieving data from the database.
 *
 * Note that adding an extra schema here may cause migrations to fail if it ends up creating a table before the same table
 * is created in a migration script. As such, this migration must be run after the tables for the following have been created (and,
 * if they are removed in the future, before they are deleted).
 */
object VaultMigrationSchema

object VaultMigrationSchemaV1 : MappedSchema(schemaFamily = VaultMigrationSchema.javaClass, version = 1,
        mappedTypes = listOf(
                DBTransactionStorage.DBTransaction::class.java,
                PersistentIdentityService.PersistentIdentity::class.java,
                PersistentIdentityService.PersistentIdentityNames::class.java,
                BasicHSMKeyManagementService.PersistentKey::class.java
        )
)


// Can this be generalised (add extra criteria etc)? May also want to move into own file
class VaultStateIterator(private val database: CordaPersistence) : Iterator<VaultSchemaV1.VaultStates> {

    companion object {
        val logger = contextLogger()

        private object AMQPInspectorSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
            override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
                return magic == amqpMagic
            }

            override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
            override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
        }

        private fun initialiseSerialization() {
            // Deserialise with the lenient carpenter as we only care for the AMQP field getters
            _contextSerializationEnv.set(SerializationEnvironment.with(
                    SerializationFactoryImpl().apply {
                        registerScheme(AMQPInspectorSerializationScheme)
                    },
                    p2pContext = AMQP_P2P_CONTEXT.withLenientCarpenter(),
                    storageContext = AMQP_STORAGE_CONTEXT.withLenientCarpenter()
            ))
        }
    }
    private val criteriaBuilder = database.entityManagerFactory.criteriaBuilder
    val numStates = getTotalStates()

    private fun getTotalStates(): Long {
        return database.transaction {
            val session = currentDBSession()
            val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
            val queryRootStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
            criteriaQuery.select(criteriaBuilder.count(queryRootStates))
            val query = session.createQuery(criteriaQuery)
            val result = query.singleResult
            logger.debug("Found $result total states in the vault")
            result
        }
    }

    private val pageSize = 1000
    private var pageNumber = 0
    private var transaction: DatabaseTransaction? = null
    private var currentPage = getNextPage()

    private fun endTransaction() {
        try {
            transaction?.commit()
        } catch (e: Exception) {
            transaction?.rollback()
            logger.error("Failed to commit transaction while iterating vault states: ${e.message}", e)
        } finally {
            transaction?.close()
        }
    }

    private fun getNextPage(): List<VaultSchemaV1.VaultStates> {
        endTransaction()
        transaction = database.newTransaction()
        val session = currentDBSession()
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
        val queryRootStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        criteriaQuery.select(queryRootStates)
        val query = session.createQuery(criteriaQuery)
        query.firstResult = (pageNumber * pageSize)
        query.maxResults = pageSize
        pageNumber++
        val result = query.resultList
        logger.debug("Current page has ${result.size} vault states")
        return result
    }

    private var currentIndex = 0

    override fun hasNext(): Boolean {
        val nextElementPresent = currentIndex + ((pageNumber - 1) * pageSize) < numStates
        if (!nextElementPresent) {
            endTransaction()
        }
        return nextElementPresent
    }

    override fun next(): VaultSchemaV1.VaultStates {
        if (currentIndex == pageSize) {
            currentPage = getNextPage()
            currentIndex = 0
        }
        val stateToReturn = currentPage[currentIndex]
        currentIndex++
        return stateToReturn
    }

    private val pool = ForkJoinPool.commonPool()

    private class VaultPageTask(val database: CordaPersistence,
                                val page: List<VaultSchemaV1.VaultStates>,
                                val block: (VaultSchemaV1.VaultStates) -> Unit): RecursiveAction() {

        private val pageSize = page.size
        private val tolerance = 10

        override fun compute() {
            if (pageSize > tolerance) {
                ForkJoinTask.invokeAll(createSubtasks())
            } else {
                applyBlock()
            }

        }

        private fun createSubtasks(): List<VaultPageTask> {
            return listOf(VaultPageTask(database, page.subList(0, pageSize / 2), block), VaultPageTask(database, page.subList(pageSize / 2, pageSize), block))
        }

        private fun applyBlock() {
            if (_allEnabledSerializationEnvs.isEmpty()) {
                initialiseSerialization()
            }
            effectiveSerializationEnv.serializationFactory.withCurrentContext(effectiveSerializationEnv.storageContext.withLenientCarpenter()) {
                database.transaction {
                    page.forEach { block(it) }
                }
            }
        }
    }

    private fun hasNextPage(): Boolean {
        val nextPagePresent = pageNumber * pageSize < numStates
        if (!nextPagePresent) {
            endTransaction()
        }
        return nextPagePresent
    }

    // Split up each page and execute the logic in parallel on each chunk.
    fun parallelForEach(block: (VaultSchemaV1.VaultStates) -> Unit) {
        pool.invoke(VaultPageTask(database, currentPage, block))
        while (hasNextPage()) {
            currentPage = getNextPage()
            pool.invoke(VaultPageTask(database, currentPage, block))
        }
    }
}

class VaultStateMigrationException(msg: String) : Exception(msg)