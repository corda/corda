package net.corda.node.services.schema

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.internal.VisibleForTesting
import net.corda.core.node.services.Vault
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.services.api.SchemaService
import net.corda.nodeapi.internal.persistence.HibernateConfiguration
import net.corda.nodeapi.internal.persistence.contextTransaction
import org.hibernate.FlushMode
import rx.Observable

/**
 * Small data class bundling together a ContractState and a StateRef (as opposed to a TransactionState and StateRef
 * in StateAndRef)
 */
data class ContractStateAndRef(val state: ContractState, val ref: StateRef)

/**
 * A vault observer that extracts Object Relational Mappings for contract states that support it, and persists them with Hibernate.
 */
// TODO: Manage version evolution of the schemas via additional tooling.
class HibernateObserver private constructor(private val config: HibernateConfiguration, private val schemaService: SchemaService) {
    companion object {
        private val log = contextLogger()
        fun install(vaultUpdates: Observable<Vault.Update<ContractState>>, config: HibernateConfiguration, schemaService: SchemaService): HibernateObserver {
            val observer = HibernateObserver(config, schemaService)
            vaultUpdates.subscribe { observer.persist(it.produced) }
            return observer
        }
    }

    private fun persist(produced: Set<StateAndRef<ContractState>>) {
        val stateBySchema: MutableMap<MappedSchema, MutableList<ContractStateAndRef>> = mutableMapOf()
        // map all states by their referenced schemas
        produced.forEach {
            val contractStateAndRef = ContractStateAndRef(it.state.data, it.ref)
            log.debug { "Asked to persist state ${it.ref}" }
            schemaService.selectSchemas(contractStateAndRef.state).forEach {
                stateBySchema.getOrPut(it) { mutableListOf() }.add(contractStateAndRef)
            }
        }
        // then persist all states for each schema
        stateBySchema.forEach { persistStatesWithSchema(it.value, it.key) }
    }

    @VisibleForTesting
    internal fun persistStatesWithSchema(statesAndRefs: List<ContractStateAndRef>, schema: MappedSchema) {
        val sessionFactory = config.sessionFactoryForSchemas(setOf(schema))
        val session = sessionFactory.withOptions().connection(contextTransaction.connection).flushMode(FlushMode.MANUAL).openSession()
        session.use { thisSession ->
            statesAndRefs.forEach {
                val mappedObject = schemaService.generateMappedObject(it.state, schema)
                mappedObject.stateRef = PersistentStateRef(it.ref)
                thisSession.persist(mappedObject)
            }
            thisSession.flush()
        }
    }
}
