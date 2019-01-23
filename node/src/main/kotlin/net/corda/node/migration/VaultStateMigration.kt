package net.corda.node.migration

import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.containsAny
import net.corda.core.node.services.Vault
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.utilities.contextLogger
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.nodeapi.internal.persistence.currentDBSession
import org.hibernate.Session

class VaultStateMigration : CustomTaskChange, CordaMigration() {
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

    private fun getStateAndRef(persistentState: VaultSchemaV1.VaultStates): StateAndRef<ContractState> {
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
        initialiseNodeServices(database!!)

        cordaDB.transaction {
            val session = currentDBSession()
            val persistentStates = getVaultStates(session)
            val refPersistentMapping = persistentStates.map { Pair(persistentStateToStateRef(it), it) }.toMap()
            val states = persistentStates.map { getStateAndRef(it)}

            logger.debug("Adding all states to state party table")
            states.forEach { addStateParties(session, it) }

            logger.debug("Updating not relevant states in the vault")
            states.filter {
                val myKeys = identityService.stripNotOurKeys(it.state.data.participants.map { it.owningKey })
                //TODO: Refactor isRelevant to be visible. Do we need KeyManagementService to do this?
                !it.state.data.participants.map {it.owningKey}.any { it.containsAny(myKeys)}
            }.forEach {
                refPersistentMapping[it.ref]?.relevancyStatus = Vault.RelevancyStatus.NOT_RELEVANT
            }
        }
    }
}