package net.corda.failtesting.missingmigrationcordapp

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object MissingMigrationSchema

object MissingMigrationSchemaV1 : MappedSchema(
        schemaFamily = MissingMigrationSchema.javaClass,
        version = 1,
        mappedTypes = listOf(MissingMigrationSchemaV1.TestEntity::class.java)) {

    @Entity
    @Table(name = "test_table")
    class TestEntity(
            @Column(name = "random_value")
            var randomValue: String
    ) : PersistentState() {
        constructor() : this("")
    }
}