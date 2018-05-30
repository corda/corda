package net.corda.node.services.schema

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.FungibleAsset
import net.corda.core.contracts.LinearState
import net.corda.core.schemas.CommonSchemaV1
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.internal.schemas.NodeInfoSchemaV1
import net.corda.node.services.api.SchemaService
import net.corda.node.services.api.SchemaService.SchemaOptions
import net.corda.node.services.events.NodeSchedulerService
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.PersistentKeyManagementService
import net.corda.node.services.messaging.P2PMessageDeduplicator
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.persistence.DBTransactionMappingStorage
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.transactions.BFTNonValidatingNotaryService
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.services.transactions.RaftUniquenessProvider
import net.corda.node.services.upgrade.ContractUpgradeServiceImpl
import net.corda.node.services.vault.VaultSchemaV1

/**
 * Most basic implementation of [SchemaService].
 * TODO: support loading schema options from node configuration.
 * TODO: support configuring what schemas are to be selected for persistence.
 * TODO: support plugins for schema version upgrading or custom mapping not supported by original [QueryableState].
 * TODO: create whitelisted tables when a CorDapp is first installed
 */
class NodeSchemaService(extraSchemas: Set<MappedSchema> = emptySet(), includeNotarySchemas: Boolean = false) : SchemaService, SingletonSerializeAsToken() {
    // Core Entities used by a Node
    object NodeCore

    object NodeCoreV1 : MappedSchema(schemaFamily = NodeCore.javaClass, version = 1,
            mappedTypes = listOf(DBCheckpointStorage.DBCheckpoint::class.java,
                    DBTransactionStorage.DBTransaction::class.java,
                    DBTransactionMappingStorage.DBTransactionMapping::class.java,
                    PersistentKeyManagementService.PersistentKey::class.java,
                    NodeSchedulerService.PersistentScheduledState::class.java,
                    NodeAttachmentService.DBAttachment::class.java,
                    P2PMessageDeduplicator.ProcessedMessage::class.java,
                    PersistentIdentityService.PersistentIdentity::class.java,
                    PersistentIdentityService.PersistentIdentityNames::class.java,
                    ContractUpgradeServiceImpl.DBContractUpgrade::class.java
            ))

    // Entities used by a Notary
    object NodeNotary

    object NodeNotaryV1 : MappedSchema(schemaFamily = NodeNotary.javaClass, version = 1,
            mappedTypes = listOf(PersistentUniquenessProvider.BaseComittedState::class.java,
                    PersistentUniquenessProvider.Request::class.java,
                    PersistentUniquenessProvider.CommittedState::class.java,
                    RaftUniquenessProvider.CommittedState::class.java,
                    BFTNonValidatingNotaryService.CommittedState::class.java
            ))

    // Required schemas are those used by internal Corda services
    private val requiredSchemas: Map<MappedSchema, SchemaService.SchemaOptions> =
            mapOf(Pair(CommonSchemaV1, SchemaOptions()),
                  Pair(VaultSchemaV1, SchemaOptions()),
                  Pair(NodeInfoSchemaV1, SchemaOptions()),
                  Pair(NodeCoreV1, SchemaOptions()))
    private val notarySchemas = if (includeNotarySchemas) mapOf(Pair(NodeNotaryV1, SchemaOptions())) else emptyMap<MappedSchema, SchemaService.SchemaOptions>()

    override val schemaOptions: Map<MappedSchema, SchemaService.SchemaOptions> = requiredSchemas + notarySchemas + extraSchemas.associateBy({ it }, { SchemaOptions() })

    // Currently returns all schemas supported by the state, with no filtering or enrichment.
    override fun selectSchemas(state: ContractState): Iterable<MappedSchema> {
        val schemas = mutableSetOf<MappedSchema>()
        if (state is QueryableState)
            schemas += state.supportedSchemas()
        if (state is LinearState)
            schemas += VaultSchemaV1   // VaultLinearStates
        if (state is FungibleAsset<*>)
            schemas += VaultSchemaV1   // VaultFungibleStates

        return schemas
    }

    // Because schema is always one supported by the state, just delegate.
    override fun generateMappedObject(state: ContractState, schema: MappedSchema): PersistentState {
        if ((schema === VaultSchemaV1) && (state is LinearState))
            return VaultSchemaV1.VaultLinearStates(state.linearId, state.participants)
        if ((schema === VaultSchemaV1) && (state is FungibleAsset<*>))
            return VaultSchemaV1.VaultFungibleStates(state.owner, state.amount.quantity, state.amount.token.issuer.party, state.amount.token.issuer.reference, state.participants)
        return (state as QueryableState).generateMappedObject(schema)
    }
}
