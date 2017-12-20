package net.corda.nodeapi.internal.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.core.MSSQLDatabase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.getMigrationResource
import net.corda.core.utilities.contextLogger
import java.io.*
import javax.sql.DataSource

private const val MIGRATION_PREFIX = "migration"

class SchemaMigration(val schemas: Set<MappedSchema>, val dataSource: DataSource, private val schemaName: String? = null) {

    companion object {
        private val logger = contextLogger()
    }

    fun generateMigrationScript(outputFile: File) = doRunMigration(PrintWriter(outputFile))

    fun runMigration() = doRunMigration()

    private fun doRunMigration(outputWriter: Writer? = null) {

        // virtual file name of the changelog that includes all schemas
        val dynamicInclude = "master.changelog.json"

        dataSource.connection.use { connection ->

            //collect all changelog file referenced in the included schemas
            val changelogList = schemas.map { mappedSchema ->
                getMigrationResource(mappedSchema).let {
                    "${MIGRATION_PREFIX}/${it}.xml"
                }
            }

            //create a resourse accessor that aggregates the changelogs included in the schemas into one dynamic stream
            val customResourceAccessor = object : ClassLoaderResourceAccessor() {
                override fun getResourcesAsStream(path: String): Set<InputStream> {

                    if (path == dynamicInclude) {
                        //create a map in liquibase format including all migration files
                        val includeAllFiles = mapOf("databaseChangeLog" to changelogList.map { file -> mapOf("include" to mapOf("file" to file)) })

                        // transform it to json
                        val includeAllFilesJson = ObjectMapper().writeValueAsBytes(includeAllFiles)

                        //return the json as a stream
                        return setOf(ByteArrayInputStream(includeAllFilesJson))
                    }
                    return super.getResourcesAsStream(path)?.take(1)?.toSet() ?: emptySet()
                }
            }

            val liquibase = Liquibase(dynamicInclude, customResourceAccessor, getLiquibaseDatabase(JdbcConnection(connection)))

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

            if (outputWriter != null) {
                liquibase.update(Contexts(), outputWriter)
            } else {
                liquibase.update(Contexts())
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
