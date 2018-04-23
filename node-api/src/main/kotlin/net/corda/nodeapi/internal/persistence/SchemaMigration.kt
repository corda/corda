/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.core.MSSQLDatabase
import liquibase.database.jvm.JdbcConnection
import liquibase.lockservice.LockServiceFactory
import liquibase.resource.ClassLoaderResourceAccessor
import net.corda.core.internal.MigrationHelpers.getMigrationResource
import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.contextLogger
import java.io.*
import javax.sql.DataSource

class SchemaMigration(
        val schemas: Set<MappedSchema>,
        val dataSource: DataSource,
        val failOnMigrationMissing: Boolean,
        private val databaseConfig: DatabaseConfig,
        private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader) {

    companion object {
        private val logger = contextLogger()
    }

    /**
     * Main entry point to the schema migration.
     * Called during node startup.
     */
    fun nodeStartup() {
        when {
            databaseConfig.runMigration -> runMigration()
            failOnMigrationMissing -> checkState()
        }
    }

    /**
     * will run the liquibase migration on the actual database
     */
    fun runMigration() = doRunMigration(run = true, outputWriter = null, check = false)

    /**
     * will write the migration to a Writer
     */
    fun generateMigrationScript(writer: Writer) = doRunMigration(run = false, outputWriter = writer, check = false)

    /**
     * ensures that the database is up to date with the latest migration changes
     */
    fun checkState() = doRunMigration(run = false, outputWriter = null, check = true)

    /**
     * can be used from an external tool to release the lock in case something went terribly wrong
     */
    fun forceReleaseMigrationLock() {
        dataSource.connection.use { connection ->
            LockServiceFactory.getInstance().getLockService(getLiquibaseDatabase(JdbcConnection(connection))).forceReleaseLock()
        }
    }

    private fun doRunMigration(run: Boolean, outputWriter: Writer?, check: Boolean) {

        // virtual file name of the changelog that includes all schemas
        val dynamicInclude = "master.changelog.json"

        dataSource.connection.use { connection ->

            // collect all changelog file referenced in the included schemas
            // for backward compatibility reasons, when failOnMigrationMissing=false, we don't manage CorDapps via Liquibase but use the hibernate hbm2ddl=update
            val changelogList = schemas.map { mappedSchema ->
                val resource = getMigrationResource(mappedSchema, classLoader)
                when {
                    resource != null -> resource
                    failOnMigrationMissing -> throw IllegalStateException("No migration defined for schema: ${mappedSchema.name} v${mappedSchema.version}")
                    else -> {
                        logger.warn("No migration defined for schema: ${mappedSchema.name} v${mappedSchema.version}")
                        null
                    }
                }
            }

            //create a resourse accessor that aggregates the changelogs included in the schemas into one dynamic stream
            val customResourceAccessor = object : ClassLoaderResourceAccessor(classLoader) {
                override fun getResourcesAsStream(path: String): Set<InputStream> {

                    if (path == dynamicInclude) {
                        //create a map in liquibase format including all migration files
                        val includeAllFiles = mapOf("databaseChangeLog" to changelogList.filter { it != null }.map { file -> mapOf("include" to mapOf("file" to file)) })

                        // transform it to json
                        val includeAllFilesJson = ObjectMapper().writeValueAsBytes(includeAllFiles)

                        //return the json as a stream
                        return setOf(ByteArrayInputStream(includeAllFilesJson))
                    }
                    return super.getResourcesAsStream(path)?.take(1)?.toSet() ?: emptySet()
                }
            }

            val liquibase = Liquibase(dynamicInclude, customResourceAccessor, getLiquibaseDatabase(JdbcConnection(connection)))

            val schemaName: String? = databaseConfig.schema
            if (!schemaName.isNullOrBlank()) {
                if (liquibase.database.defaultSchemaName != schemaName) {
                    logger.debug("defaultSchemaName=${liquibase.database.defaultSchemaName} changed to $schemaName")
                    liquibase.database.defaultSchemaName = schemaName
                }
                if (liquibase.database.liquibaseSchemaName != schemaName) {
                    logger.debug("liquibaseSchemaName=${liquibase.database.liquibaseSchemaName} changed to $schemaName")
                    liquibase.database.liquibaseSchemaName = schemaName
                }
            }
            logger.info("defaultSchemaName=${liquibase.database.defaultSchemaName}")
            logger.info("liquibaseSchemaName=${liquibase.database.liquibaseSchemaName}")
            logger.info("outputDefaultSchema=${liquibase.database.outputDefaultSchema}")

            when {
                run && !check -> liquibase.update(Contexts())
                check && !run -> {
                    val unRunChanges = liquibase.listUnrunChangeSets(Contexts(), LabelExpression())
                    if (unRunChanges.isNotEmpty()) {
                        throw IllegalStateException("There are ${unRunChanges.size} outstanding database changes that need to be run. Please use the advanced migration tool. See: https://docs.corda.r3.com/database-migration.html")
                    }
                }
                (outputWriter != null) && !check && !run -> liquibase.update(Contexts(), outputWriter)
                else -> throw IllegalStateException("Invalid usage.")
            }
        }
    }

    private fun getLiquibaseDatabase(conn: JdbcConnection): Database {

        // the standard MSSQLDatabase in liquibase does not support sequences for Ms Azure
        // this class just overrides that behaviour
        class AzureDatabase(conn: JdbcConnection) : MSSQLDatabase() {
            init {
                this.connection = conn
            }

            override fun getShortName(): String = "azure"

            override fun supportsSequences(): Boolean = true
        }

        val liquibaseDbImplementation = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(conn)

        return if (liquibaseDbImplementation is MSSQLDatabase) AzureDatabase(conn) else liquibaseDbImplementation
    }
}
