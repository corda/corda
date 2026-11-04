package net.corda.nodeapitests.internal.persistence

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.PersistentStateRef
import net.corda.node.internal.DataSourceFactory
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.SchemaMigration
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

class SchemaMigrationSameDatabaseTest {
    object TestSchemaFamily

    object CorDappSchema : MappedSchema(schemaFamily = TestSchemaFamily.javaClass, version = 1, mappedTypes = listOf(State::class.java)) {
        @Entity
        @Table(name = "State")
        class State(
                @Column
                var id: String
        ) : PersistentState(PersistentStateRef(UniqueIdentifier().toString(), 0))

        override val migrationResource: String? = "goodschema.testmigration"
    }

    @Test(timeout = 300_000)
    fun `CorDapp schema change sets are run when the core schemas migration before them found nothing to run`() {
        // This is what a node does in run-migration-scripts: it migrates the core schemas and then the CorDapp schemas, using the same
        // database. When the core schemas are already up to date, Liquibase remembers that for the database, and that must not stop the
        // CorDapp schemas' change sets from being run.
        val dataSource = DataSourceFactory.createDataSource(MockServices.makeTestDataSourceProperties())
        val schemaMigration = SchemaMigration(dataSource, null, null, ALICE_NAME)
        val coreSchemas = NodeSchemaService().internalSchemas
        val appSchemas = setOf<MappedSchema>(CorDappSchema)

        schemaMigration.runMigration(false, coreSchemas, true)
        // Nothing left to run for the core schemas
        schemaMigration.runMigration(false, coreSchemas, true)
        assertThat(schemaMigration.getPendingChangesCount(appSchemas, true)).isGreaterThan(0)

        schemaMigration.runMigration(false, appSchemas, true)

        assertThat(schemaMigration.getPendingChangesCount(appSchemas, true)).isEqualTo(0)
    }
}
