/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.CommonSchemaV1
import net.corda.core.schemas.MappedSchema
import net.corda.node.internal.configureDatabase
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.SchemaMigration
import net.corda.testing.node.MockServices
import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.lang.reflect.Method
import java.math.BigInteger
import java.net.URL
import javax.persistence.*
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class SchemaMigrationTest {

    @Test
    fun `Ensure that runMigration is disabled by default`() {
        assertThat(DatabaseConfig().runMigration).isFalse()
    }

    @Test
    fun `Migration is run when runMigration is disabled, and database is H2`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val db = configureDatabase(dataSourceProps, DatabaseConfig(runMigration = false), { null }, { null })
        checkMigrationRun(db)
    }

    @Test
    fun `Migration is run when runMigration is enabled`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val db = configureDatabase(dataSourceProps, DatabaseConfig(runMigration = true), { null }, { null })
        checkMigrationRun(db)
    }

    @Test
    fun `Verification passes when migration is run as a separate step`() {
        val schemaService = NodeSchemaService()
        val dataSourceProps = MockServices.makeTestDataSourceProperties()

        //run the migration on the database
        val migration = SchemaMigration(schemaService.schemaOptions.keys, HikariDataSource(HikariConfig(dataSourceProps)), true, DatabaseConfig())
        migration.runMigration(false)

        //start the node with "runMigration = false" and check that it started correctly
        val db = configureDatabase(dataSourceProps, DatabaseConfig(runMigration = false), { null }, { null }, schemaService)
        checkMigrationRun(db)
    }

    @Test
    fun `The migration picks up migration files on the classpath if they follow the convention`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()

        // create a migration file for the DummyTestSchemaV1 and add it to the classpath
        val tmpFolder = Files.createTempDirectory("test")
        val fileName = MigrationExporter(tmpFolder, dataSourceProps, Thread.currentThread().contextClassLoader, HikariDataSource(HikariConfig(dataSourceProps))).generateMigrationForCorDapp(DummyTestSchemaV1).fileName
        addToClassPath(tmpFolder)

        // run the migrations for DummyTestSchemaV1, which should pick up the migration file
        val db = configureDatabase(dataSourceProps, DatabaseConfig(runMigration = true), { null }, { null }, NodeSchemaService(extraSchemas = setOf(DummyTestSchemaV1)))

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

    //hacky way to add a folder to the classpath
    private fun addToClassPath(file: Path): Method {
        return URLClassLoader::class.java.getDeclaredMethod("addURL", URL::class.java).apply {
            isAccessible = true
            invoke(ClassLoader.getSystemClassLoader(), file.toUri().toURL())
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