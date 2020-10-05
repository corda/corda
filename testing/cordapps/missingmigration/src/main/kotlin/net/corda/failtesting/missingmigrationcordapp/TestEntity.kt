package net.corda.failtesting.missingmigrationcordapp

import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

class TestEntity(val randomValue: String, override val participants: List<AbstractParty>) : QueryableState {
    override fun supportedSchemas(): Iterable<MappedSchema> {
        return listOf(MissingMigrationSchemaV1)
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return MissingMigrationSchemaV1.TestEntity(randomValue)
    }
}