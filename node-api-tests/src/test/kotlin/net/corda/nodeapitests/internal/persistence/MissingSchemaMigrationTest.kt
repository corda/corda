package net.corda.nodeapitests.internal.persistence

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.node.internal.DataSourceFactory
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.MissingMigrationException
import net.corda.nodeapi.internal.persistence.SchemaMigration
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.sql.DataSource

class MissingSchemaMigrationTest {
    object TestSchemaFamily

    object GoodSchema : MappedSchema(schemaFamily = TestSchemaFamily.javaClass, version = 1, mappedTypes = listOf(State::class.java)) {
        @Entity
        class State(
                @Column
                var id: String
        ) : PersistentState()
    }

    lateinit var hikariProperties: Properties
    lateinit var dataSource: DataSource

    @Before
    fun setUp() {
        hikariProperties = MockServices.makeTestDataSourceProperties()
        dataSource = DataSourceFactory.createDataSource(hikariProperties)
    }

    private fun schemaMigration() = SchemaMigration(dataSource, null, null,
            TestIdentity(ALICE_NAME, 70).name)


    @Test(timeout=300_000)
	fun `test that an error is thrown when forceThrowOnMissingMigration is set and a mapped schema is missing a migration`() {
        assertThatThrownBy {
            schemaMigration().runMigration(dataSource.connection.use { DBCheckpointStorage.getCheckpointCount(it) != 0L }, setOf(GoodSchema), true)
        }.isInstanceOf(MissingMigrationException::class.java)
    }

    @Test(timeout=300_000)
	fun `test that an error is not thrown when forceThrowOnMissingMigration is not set and a mapped schema is missing a migration`() {
        assertDoesNotThrow {
            schemaMigration().runMigration(dataSource.connection.use { DBCheckpointStorage.getCheckpointCount(it) != 0L }, setOf(GoodSchema), false)
        }
    }

    @Test(timeout=300_000)
	fun `test that there are no missing migrations for the node`() {
        assertDoesNotThrow("This test failure indicates " +
                "a new table has been added to the node without the appropriate migration scripts being present") {
            schemaMigration().runMigration(dataSource.connection.use { DBCheckpointStorage.getCheckpointCount(it) != 0L }, NodeSchemaService().internalSchemas, true)
        }
    }

}