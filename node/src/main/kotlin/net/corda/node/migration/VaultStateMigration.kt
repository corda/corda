package net.corda.node.migration

import liquibase.database.Database
import net.corda.core.node.services.Vault
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.DBNetworkParametersStorage
import net.corda.node.internal.schemas.NodeInfoSchemaV1
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.network.PersistentNetworkMapCache
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.currentDBSession
import org.hibernate.query.Query
import javax.persistence.criteria.Root
import javax.persistence.criteria.Selection

class VaultStateMigration : CordaMigration() {
    override fun execute(database: Database) {
        initialiseNodeServices(database, setOf(VaultMigrationSchemaV1, VaultSchemaV1, NodeInfoSchemaV1))
        val persistentStates = VaultStateIterator(cordaDB)
        if (persistentStates.numStates > 0) {
            throw VaultStateMigrationException("Found ${persistentStates.numStates} states that need to be updated to V4. Please upgrade " +
                    "to an older version of Corda first to perform this migration.")
        }
    }
}


/**
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
                PersistentIdentityService.PersistentPublicKeyHashToCertificate::class.java,
                PersistentIdentityService.PersistentPublicKeyHashToParty::class.java,
                BasicHSMKeyManagementService.PersistentKey::class.java,
                NodeAttachmentService.DBAttachment::class.java,
                DBNetworkParametersStorage.PersistentNetworkParameters::class.java,
                PersistentNetworkMapCache.PersistentPartyToPublicKeyHash::class.java
        )
)

/**
 * Provides a mechanism for iterating through all persistent vault states.
 *
 * This class ensures that changes to persistent states are periodically committed and flushed. This prevents out of memory issues when
 * there are a large number of states.
 *
 * Currently, this class filters out those persistent states that have entries in the state party table. This behaviour is required for the
 * vault state migration, as entries in this table should not be duplicated. Unconsumed states are also filtered out for performance.
 */
class VaultStateIterator(private val database: CordaPersistence) {
    companion object {
        val logger = contextLogger()
    }
    private val criteriaBuilder = database.entityManagerFactory.criteriaBuilder
    val numStates = getTotalStates()

    // Create a query on the vault states that does the following filtering:
    // - Returns only those states without corresponding entries in the state_party table
    // - Returns only unconsumed states (for performance reasons)
    private fun <T>createVaultStatesQuery(returnClass: Class<T>, selection: (Root<VaultSchemaV1.VaultStates>) -> Selection<T>): Query<T> {
        val session = currentDBSession()
        val criteriaQuery = criteriaBuilder.createQuery(returnClass)
        val queryRootStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        val subQuery = criteriaQuery.subquery(Long::class.java)
        val subRoot = subQuery.from(VaultSchemaV1.PersistentParty::class.java)
        subQuery.select(criteriaBuilder.count(subRoot))
        subQuery.where(criteriaBuilder.equal(
                subRoot.get<VaultSchemaV1.PersistentStateRefAndKey>(VaultSchemaV1.PersistentParty::compositeKey.name)
                        .get<PersistentStateRef>(VaultSchemaV1.PersistentStateRefAndKey::stateRef.name),
                queryRootStates.get<PersistentStateRef>(VaultSchemaV1.VaultStates::stateRef.name)))
        criteriaQuery.select(selection(queryRootStates))
        criteriaQuery.where(criteriaBuilder.and(
                criteriaBuilder.equal(subQuery, 0),
                criteriaBuilder.equal(queryRootStates.get<Vault.StateStatus>(VaultSchemaV1.VaultStates::stateStatus.name),
                        Vault.StateStatus.UNCONSUMED)))
        return session.createQuery(criteriaQuery)
    }

    private fun getTotalStates(): Long {
        return database.transaction {
            val query = createVaultStatesQuery(Long::class.java, criteriaBuilder::count)
            val result = query.singleResult
            logger.debug("Found $result total states in the vault")
            result
        }
    }
}

class VaultStateMigrationException(msg: String, cause: Exception? = null) : Exception(msg, cause)