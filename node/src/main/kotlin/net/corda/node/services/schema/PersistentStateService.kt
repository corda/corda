package net.corda.node.services.schema

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.internal.VisibleForTesting
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.services.api.SchemaService
import net.corda.nodeapi.internal.persistence.currentDBSession

/**
 * Small data class bundling together a ContractState and a StateRef (as opposed to a TransactionState and StateRef
 * in StateAndRef)
 */
data class ContractStateAndRef(val state: ContractState, val ref: StateRef)

/**
 * A vault observer that extracts Object Relational Mappings for contract states that support it, and persists them with Hibernate.
 */
// TODO: Manage version evolution of the schemas via additional tooling.
class PersistentStateService(private val schemaService: SchemaService) {
    companion object {
        private val log = contextLogger()
    }

    fun persist(produced: Set<StateAndRef<ContractState>>) {
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
        statesAndRefs.forEach {
            val mappedObject = schemaService.generateMappedObject(it.state, schema)
            mappedObject.stateRef = PersistentStateRef(it.ref)
            currentDBSession().persist(mappedObject)
        }
    }
}
