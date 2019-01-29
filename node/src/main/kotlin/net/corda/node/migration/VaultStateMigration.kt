package net.corda.node.migration

import liquibase.database.Database
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_SIZE
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.utilities.contextLogger
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.currentDBSession
import org.hibernate.Session

class VaultStateMigration : CordaMigration() {
    companion object {
        private val logger = contextLogger()
    }

    private fun addStateParties(session: Session, stateAndRef: StateAndRef<ContractState>) {
        val state = stateAndRef.state.data
        val persistentStateRef = PersistentStateRef(stateAndRef.ref)
        state.participants.groupBy { it.owningKey }.forEach { participants ->
            val persistentParty = VaultSchemaV1.PersistentParty(persistentStateRef, participants.value.first())
            session.save(persistentParty)
        }
    }

    private fun getStateAndRef(persistentState: VaultSchemaV1.VaultStates): StateAndRef<ContractState> {
        val txHash = SecureHash.parse(persistentState.stateRef!!.txId)
        val tx = dbTransactions.getTransaction(txHash)!!
        val state = tx.coreTransaction.outputs[persistentState.stateRef!!.index]
        val stateRef = StateRef(txHash, persistentState.stateRef!!.index)
        return StateAndRef(state, stateRef)
    }

    override fun execute(database: Database?) {
        logger.info("Migrating vault state data to V4 tables")
        initialiseNodeServices(database!!, setOf(VaultMigrationSchemaV1, VaultSchemaV1))

        cordaDB.transaction {
            val persistentStates = VaultStateIterator(cordaDB)
            val myKeys = identityService.ourNames.mapNotNull { identityService.wellKnownPartyFromX500Name(it)?.owningKey }.toSet()
            persistentStates.forEach {
                val session = currentDBSession()
                val stateAndRef = getStateAndRef(it)

                addStateParties(session, stateAndRef)

                if (!NodeVaultService.isRelevant(stateAndRef.state.data, myKeys)) {
                    it.relevancyStatus = Vault.RelevancyStatus.NOT_RELEVANT
                }
            }
        }
        logger.info("Finished performing vault state data migration")
    }
}


/*
 * A minimal set of schema for retrieving data from the database.
 *
 * Note that adding an extra schema here may cause migrations to fail if it ends up creating a table before the same table
 * is created in a migration script.
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
    }
    private val criteriaBuilder = database.entityManagerFactory.criteriaBuilder
    private val numStates = getTotalStates()

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

    private val pageSize = DEFAULT_PAGE_SIZE
    private var pageNumber = 0
    private var currentPage = getNextPage()

    private fun getNextPage(): List<VaultSchemaV1.VaultStates> {
        return database.transaction {
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
            result
        }
    }

    private var currentIndex = 0

    override fun hasNext(): Boolean {
        return currentIndex + ((pageNumber - 1) * pageSize) < numStates
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
}