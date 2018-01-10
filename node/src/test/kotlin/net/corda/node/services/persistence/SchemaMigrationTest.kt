package net.corda.node.services.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.node.internal.configureDatabase
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.SchemaMigration
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import java.math.BigInteger

class SchemaMigrationTest {

    @Test
    fun `Ensure that runMigration is disabled by default`() {
        assertThat(DatabaseConfig().runMigration).isFalse()
    }

    @Test
    fun `Migration is run when runMigration is disabled, and database is H2`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val db = configureDatabase(dataSourceProps, DatabaseConfig(runMigration = false), rigorousMock())
        checkMigrationRun(db)
    }

    @Test
    fun `Migration is run when runMigration is enabled`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val db = configureDatabase(dataSourceProps, DatabaseConfig(runMigration = true), rigorousMock())
        checkMigrationRun(db)
    }

    @Test
    fun `Verification passes when migration is run as a separate step`() {
        val schemaService = NodeSchemaService()
        val dataSourceProps = MockServices.makeTestDataSourceProperties()

        //run the migration on the database
        val migration = SchemaMigration(schemaService.schemaOptions.keys, HikariDataSource(HikariConfig(dataSourceProps)), true, DatabaseConfig())
        migration.runMigration()

        //start the node with "runMigration = false" and check that it started correctly
        val db = configureDatabase(dataSourceProps, DatabaseConfig(runMigration = false), rigorousMock(), schemaService)
        checkMigrationRun(db)
    }

    private fun checkMigrationRun(db: CordaPersistence) {
        //check that the hibernate_sequence was created which means the migration was run
        db.transaction {
            val value = this.session.createNativeQuery("SELECT NEXT VALUE FOR hibernate_sequence").uniqueResult() as BigInteger
            assertThat(value).isGreaterThan(BigInteger.ZERO)
        }
    }
}