package net.corda.nodeapitests.internal.persistence

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.PersistentStateRef
import net.corda.node.internal.DataSourceFactory
import net.corda.node.internal.startHikariPool
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseMigrationException
import net.corda.nodeapi.internal.persistence.HibernateSchemaChangeException
import net.corda.nodeapi.internal.persistence.SchemaMigration
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table
import javax.sql.DataSource

class MigrationSchemaSyncTest{
    object TestSchemaFamily

    object GoodSchema : MappedSchema(schemaFamily = TestSchemaFamily.javaClass, version = 1, mappedTypes = listOf(State::class.java)) {
        @Entity
        @Table(name = "State")
        class State(
                @Column
                var id: String
        ) : PersistentState(PersistentStateRef(UniqueIdentifier().toString(), 0 ))

        override val migrationResource: String? = "goodschema.testmigration"
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
    fun testSchemaScript(){
        schemaMigration().runMigration(false, setOf(GoodSchema), true)
        val persistence = CordaPersistence(
                false,
                setOf(GoodSchema),
                hikariProperties.getProperty("dataSource.url"),
                TestingNamedCacheFactory()
                )
        persistence.startHikariPool(hikariProperties){ _, _ -> Unit}

        persistence.transaction {
            this.entityManager.persist(GoodSchema.State("id"))
        }
    }


    @Test(timeout=300_000)
    fun checkThatSchemaSyncFixesLiquibaseException(){
        // Schema is missing if no migration is run and hibernate not allowed to create
        val persistenceBlank = CordaPersistence(
                false,
                setOf(GoodSchema),
                hikariProperties.getProperty("dataSource.url"),
                TestingNamedCacheFactory()
        )
        persistenceBlank.startHikariPool(hikariProperties){ _, _ -> Unit}
        assertThatThrownBy{ persistenceBlank.transaction {this.entityManager.persist(GoodSchema.State("id"))}}
                .isInstanceOf(HibernateSchemaChangeException::class.java)
                .hasMessageContaining("Incompatible schema")

        // create schema via hibernate - now schema gets created and we can write
        val persistenceHibernate = CordaPersistence(
                false,
                setOf(GoodSchema),
                hikariProperties.getProperty("dataSource.url"),
                TestingNamedCacheFactory(),
                allowHibernateToManageAppSchema = true
        )
        persistenceHibernate.startHikariPool(hikariProperties){ _, _ -> Unit}
        persistenceHibernate.transaction { entityManager.persist(GoodSchema.State("id_hibernate")) }

        // if we try to run schema migration now, the changelog and the schemas are out of sync
        assertThatThrownBy { schemaMigration().runMigration(false, setOf(GoodSchema), true) }
                .isInstanceOf(DatabaseMigrationException::class.java)
                .hasMessageContaining("Table \"STATE\" already exists")

        // update the change log with schemas we know exist
        schemaMigration().synchroniseSchemas(setOf(GoodSchema), true)

        // now run migration runs clean
        schemaMigration().runMigration(false, setOf(GoodSchema), true)
    }


}