package net.corda.node.services.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.api.SchemaService

/**
 * Most basic implementation of [SchemaService].
 *
 * TODO: support loading schema options from node configuration.
 * TODO: support configuring what schemas are to be selected for persistence.
 * TODO: support plugins for schema version upgrading or custom mapping not supported by original [QueryableState].
 */
class NodeSchemaService : SchemaService, SingletonSerializeAsToken() {
    // Currently does not support configuring schema options.
    override val schemaOptions: Map<MappedSchema, SchemaService.SchemaOptions> = emptyMap()

    // Currently returns all schemas supported by the state, with no filtering or enrichment.
    override fun selectSchemas(state: QueryableState): Iterable<MappedSchema> {
        return state.supportedSchemas()
    }

    // Because schema is always one supported by the state, just delegate.
    override fun generateMappedObject(state: QueryableState, schema: MappedSchema): PersistentState {
        return state.generateMappedObject(schema)
    }
}
