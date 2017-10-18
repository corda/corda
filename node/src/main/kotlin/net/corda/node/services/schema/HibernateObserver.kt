package net.corda.node.services.schema

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.node.services.Vault
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.node.services.persistence.HibernateConfiguration
import net.corda.node.utilities.DatabaseTransactionManager
import org.hibernate.FlushMode
import rx.Observable

/**
 * A vault observer that extracts Object Relational Mappings for contract states that support it, and persists them with Hibernate.
 */
// TODO: Manage version evolution of the schemas via additional tooling.
class HibernateObserver(vaultUpdates: Observable<Vault.Update<ContractState>>, val config: HibernateConfiguration) {

    companion object {
        val logger = loggerFor<HibernateObserver>()
    }

    init {
        vaultUpdates.subscribe { persist(it.produced) }
    }

    private fun persist(produced: Set<StateAndRef<ContractState>>) {
        produced.forEach { persistState(it) }
    }

    private fun persistState(stateAndRef: StateAndRef<ContractState>) {
        val state = stateAndRef.state.data
        logger.debug { "Asked to persist state ${stateAndRef.ref}" }
        config.schemaService.selectSchemas(state).forEach { persistStateWithSchema(state, stateAndRef.ref, it) }
    }

    fun persistStateWithSchema(state: ContractState, stateRef: StateRef, schema: MappedSchema) {
        val sessionFactory = config.sessionFactoryForSchemas(setOf(schema))
        val session = sessionFactory.withOptions().
                connection(DatabaseTransactionManager.current().connection).
                flushMode(FlushMode.MANUAL).
                openSession()
        session.use {
            val mappedObject = config.schemaService.generateMappedObject(state, schema)
            mappedObject.stateRef = PersistentStateRef(stateRef)
            it.persist(mappedObject)
            it.flush()
        }
    }
}
