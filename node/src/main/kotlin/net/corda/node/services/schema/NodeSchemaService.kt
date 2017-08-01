package net.corda.node.services.schema

import net.corda.contracts.DealState
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.FungibleAsset
import net.corda.core.contracts.LinearState
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.api.SchemaService
import net.corda.core.schemas.CommonSchemaV1
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.persistence.DBTransactionMappingStorage
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.schemas.CashSchemaV1

/**
 * Most basic implementation of [SchemaService].
 *
 * TODO: support loading schema options from node configuration.
 * TODO: support configuring what schemas are to be selected for persistence.
 * TODO: support plugins for schema version upgrading or custom mapping not supported by original [QueryableState].
 * TODO: create whitelisted tables when a CorDapp is first installed
 */
class NodeSchemaService(customSchemas: Set<MappedSchema> = emptySet()) : SchemaService, SingletonSerializeAsToken() {

    // Currently does not support configuring schema options.

    // Required schemas are those used by internal Corda services
    // For example, cash is used by the vault for coin selection (but will be extracted as a standalone CorDapp in future)
    val requiredSchemas: Map<MappedSchema, SchemaService.SchemaOptions> =
            mapOf(Pair(CashSchemaV1, SchemaService.SchemaOptions()),
                  Pair(CommonSchemaV1, SchemaService.SchemaOptions()),
                  Pair(VaultSchemaV1, SchemaService.SchemaOptions()),
                  Pair(DBTransactionStorage.TransactionSchemaV1, SchemaService.SchemaOptions()),
                  Pair(DBTransactionMappingStorage.TransactionMappingSchemaV1, SchemaService.SchemaOptions()),
                  Pair(DBCheckpointStorage.CheckpointSchemaV1, SchemaService.SchemaOptions()))


    override val schemaOptions: Map<MappedSchema, SchemaService.SchemaOptions> = requiredSchemas.plus(customSchemas.map {
        mappedSchema -> Pair(mappedSchema, SchemaService.SchemaOptions())
    })

    // Currently returns all schemas supported by the state, with no filtering or enrichment.
    override fun selectSchemas(state: ContractState): Iterable<MappedSchema> {
        val schemas = mutableSetOf<MappedSchema>()
        if (state is QueryableState)
            schemas += state.supportedSchemas()
        if (state is LinearState)
            schemas += VaultSchemaV1   // VaultLinearStates
        // TODO: DealState to be deprecated (collapsed into LinearState)
        if (state is DealState)
            schemas += VaultSchemaV1   // VaultLinearStates
        if (state is FungibleAsset<*>)
            schemas += VaultSchemaV1   // VaultFungibleStates

        return schemas
    }

    // Because schema is always one supported by the state, just delegate.
    override fun generateMappedObject(state: ContractState, schema: MappedSchema): PersistentState {
        // TODO: DealState to be deprecated (collapsed into LinearState)
        if ((schema is VaultSchemaV1) && (state is DealState))
            return VaultSchemaV1.VaultLinearStates(state.linearId, state.ref, state.participants)
        if ((schema is VaultSchemaV1) && (state is LinearState))
            return VaultSchemaV1.VaultLinearStates(state.linearId, "", state.participants)
        if ((schema is VaultSchemaV1) && (state is FungibleAsset<*>))
            return VaultSchemaV1.VaultFungibleStates(state.owner, state.amount.quantity, state.amount.token.issuer.party, state.amount.token.issuer.reference, state.participants)
        return (state as QueryableState).generateMappedObject(schema)
    }
}
