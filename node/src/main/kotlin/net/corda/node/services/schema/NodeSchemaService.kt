package net.corda.node.services.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.api.SchemaService
import net.corda.schemas.CashSchemaV1

/**
 * Most basic implementation of [SchemaService].
 *
 * TODO: support loading schema options from node configuration.
 * TODO: support configuring what schemas are to be selected for persistence.
 * TODO: support plugins for schema version upgrading or custom mapping not supported by original [QueryableState].
 * TODO: create whitelisted tables when a CorDapp is first installed
 */
class NodeSchemaService : SchemaService, SingletonSerializeAsToken() {
    // Currently does not support configuring schema options.

    // Whitelisted tables are those required by internal Corda services
    // For example, cash is used by the vault for coin selection
    // This whitelist will grow as we add further functionality (eg. other fungible assets)
    override val schemaOptions: Map<MappedSchema, SchemaService.SchemaOptions> = mapOf(Pair(CashSchemaV1, SchemaService.SchemaOptions()))

    // Currently returns all schemas supported by the state, with no filtering or enrichment.
    override fun selectSchemas(state: QueryableState): Iterable<MappedSchema> {
        return state.supportedSchemas()
    }

    // Because schema is always one supported by the state, just delegate.
    override fun generateMappedObject(state: QueryableState, schema: MappedSchema): PersistentState {
        return state.generateMappedObject(schema)
    }
}
