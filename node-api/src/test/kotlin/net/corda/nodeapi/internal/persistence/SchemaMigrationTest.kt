package net.corda.nodeapi.internal.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.cordapp.Cordapp
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.schemas.CommonSchemaV1
import net.corda.core.schemas.MappedSchema
import net.corda.node.internal.createCordaPersistence
import net.corda.node.internal.startHikariPool
import net.corda.node.services.persistence.MigrationExporter
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.node.MockServices
import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.math.BigInteger
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.*
import javax.persistence.*

class SchemaMigrationTest {

    private fun configureDatabase(hikariProperties: Properties,
                                  databaseConfig: DatabaseConfig,
                                  schemaService: NodeSchemaService = NodeSchemaService(),
                                  cordappLoader: CordappLoader? = null): CordaPersistence =
            createCordaPersistence(databaseConfig, { null }, { null }, schemaService, TestingNamedCacheFactory(),
                    cordappLoader?.appClassLoader).apply {
                startHikariPool(hikariProperties, databaseConfig, schemaService.schemas,
                        ourName = TestIdentity(ALICE_NAME, 70).name, cordappLoader = cordappLoader)
            }

    @Test
    fun `Ensure that runMigration is disabled by default`() {
        assertThat(DatabaseConfig().runMigration).isFalse()
    }

    @Test
    fun `Migration is run when runMigration is disabled, and database is H2`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val db = configureDatabase(dataSourceProps, DatabaseConfig(runMigration = false))
        checkMigrationRun(db)
    }

    @Test
    fun `Migration is run when runMigration is enabled`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val db = configureDatabase(dataSourceProps, DatabaseConfig(runMigration = true))
        checkMigrationRun(db)
    }

    @Test
    fun `Verification passes when migration is run as a separate step`() {
        val schemaService = NodeSchemaService()
        val dataSourceProps = MockServices.makeTestDataSourceProperties()

        //run the migration on the database
        val migration = SchemaMigration(
                schemaService.schemas,
                HikariDataSource(HikariConfig(dataSourceProps)),
                DatabaseConfig(),
                currentDirectory = null,
                ourName = ALICE_NAME
        )
        migration.runMigration(false)

        //start the node with "runMigration = false" and check that it started correctly
        val db = configureDatabase(dataSourceProps, DatabaseConfig(runMigration = false), schemaService)
        checkMigrationRun(db)
    }

    @Test
    fun `The migration picks up migration files on the classpath if they follow the convention`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()

        // create a migration file for the DummyTestSchemaV1 and add it to the classpath
        val tmpFolder = Files.createTempDirectory("test")
        val fileName = MigrationExporter(tmpFolder, dataSourceProps, Thread.currentThread().contextClassLoader,
                HikariDataSource(HikariConfig(dataSourceProps)))
                .generateMigrationForCorDapp(DummyTestSchemaV1).second.fileName
        // dynamically add new folder to classpath
        val customClassloader = URLClassLoader(arrayOf(tmpFolder.toUri().toURL()), Thread.currentThread().contextClassLoader)
        // create dummy CordappLoader to force passing of custom classloader to Hikari pool setup in configureDatabase
        val dummyCordappLoader = object : CordappLoader {
            override val appClassLoader: ClassLoader get() = customClassloader
            override val cordapps: List<CordappImpl> get() = emptyList()
            override val flowCordappMap: Map<Class<out FlowLogic<*>>, Cordapp> get() = emptyMap()
            override val cordappSchemas: Set<MappedSchema> get() = emptySet()
            override fun close() {}
        }

        // run the migrations for DummyTestSchemaV1, which should pick up the migration file
        val db = configureDatabase(dataSourceProps, DatabaseConfig(runMigration = true),
                NodeSchemaService(extraSchemas = setOf(DummyTestSchemaV1)), dummyCordappLoader)

        // check that the file was picked up
        val nrOfChangesOnDiscoveredFile = db.dataSource.connection.use {
            it.createStatement().executeQuery("select count(*) from DATABASECHANGELOG where filename ='migration/$fileName'").use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
        assertThat(nrOfChangesOnDiscoveredFile).isGreaterThan(0)

        //clean up
        FileUtils.deleteDirectory(tmpFolder.toFile())
    }

    private fun checkMigrationRun(db: CordaPersistence) {
        //check that the hibernate_sequence was created which means the migration was run
        db.transaction {
            val value = this.session.createNativeQuery("SELECT NEXT VALUE FOR hibernate_sequence").uniqueResult() as BigInteger
            assertThat(value).isGreaterThan(BigInteger.ZERO)
        }
    }

    object DummyTestSchema
    object DummyTestSchemaV1 : MappedSchema(schemaFamily = DummyTestSchema.javaClass, version = 1, mappedTypes = listOf(PersistentDummyTestState::class.java)) {

        @Entity
        @Table(name = "dummy_test_states")
        class PersistentDummyTestState(

                @ElementCollection
                @Column(name = "participants")
                @CollectionTable(name = "dummy_test_states_parts", joinColumns = [
                    JoinColumn(name = "output_index", referencedColumnName = "output_index"),
                    JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id")])
                override var participants: MutableSet<AbstractParty>? = null,

                @Transient
                val uid: UniqueIdentifier

        ) : CommonSchemaV1.LinearState(uuid = uid.id, externalId = uid.externalId, participants = participants)
    }
}