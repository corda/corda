package net.corda.nodeapi.internal.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.MigrationHelpers.getMigrationResource
import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.cordapp.CordappLoader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.sql.Statement
import javax.sql.DataSource
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// Migrate the database to the current version, using liquibase.
//
// A note on the ourName parameter: This is used by the vault state migration to establish what the node's legal identity is when setting up
// its copy of the identity service. It is passed through using a system property. When multiple identity support is added, this will need
// reworking so that multiple identities can be passed to the migration.
class SchemaMigration(
        val schemas: Set<MappedSchema>,
        val dataSource: DataSource,
        private val databaseConfig: DatabaseConfig,
        cordappLoader: CordappLoader? = null,
        private val currentDirectory: Path?,
        private val ourName: CordaX500Name) {

    companion object {
        private val logger = contextLogger()
        const val NODE_BASE_DIR_KEY = "liquibase.nodeDaseDir"
        const val NODE_X500_NAME = "liquibase.nodeName"
        val loader = ThreadLocal<CordappLoader>()
        private val mutex = ReentrantLock()
    }

    init {
        loader.set(cordappLoader)
    }

    private val classLoader = cordappLoader?.appClassLoader ?: Thread.currentThread().contextClassLoader

    /**
     * Main entry point to the schema migration.
     * Called during node startup.
     */
    fun nodeStartup(existingCheckpoints: Boolean) {
        when {
            databaseConfig.initialiseSchema -> {
                migrateOlderDatabaseToUseLiquibase(existingCheckpoints)
                runMigration(existingCheckpoints)
            }
            else -> checkState()
        }
    }

    /**
     * Will run the Liquibase migration on the actual database.
     */
    private fun runMigration(existingCheckpoints: Boolean) = doRunMigration(run = true, check = false, existingCheckpoints = existingCheckpoints)

    /**
     * Ensures that the database is up to date with the latest migration changes.
     */
    private fun checkState() = doRunMigration(run = false, check = true)

    /**  Create a resourse accessor that aggregates the changelogs included in the schemas into one dynamic stream. */
    private class CustomResourceAccessor(val dynamicInclude: String, val changelogList: List<String?>, classLoader: ClassLoader) : ClassLoaderResourceAccessor(classLoader) {
        override fun getResourcesAsStream(path: String): Set<InputStream> {
            if (path == dynamicInclude) {
                // Create a map in Liquibase format including all migration files.
                val includeAllFiles = mapOf("databaseChangeLog" to changelogList.filter { it != null }.map { file -> mapOf("include" to mapOf("file" to file)) })

                // Transform it to json.
                val includeAllFilesJson = ObjectMapper().writeValueAsBytes(includeAllFiles)

                // Return the json as a stream.
                return setOf(ByteArrayInputStream(includeAllFilesJson))
            }
            return super.getResourcesAsStream(path)?.take(1)?.toSet() ?: emptySet()
        }
    }

    private fun doRunMigration(run: Boolean, check: Boolean, existingCheckpoints: Boolean? = null) {

        // Virtual file name of the changelog that includes all schemas.
        val dynamicInclude = "master.changelog.json"

        dataSource.connection.use { connection ->

            // Collect all changelog file referenced in the included schemas.
            // For backward compatibility reasons, when failOnMigrationMissing=false, we don't manage CorDapps via Liquibase but use the hibernate hbm2ddl=update.
            val changelogList = schemas.mapNotNull { mappedSchema ->
                val resource = getMigrationResource(mappedSchema, classLoader)
                when {
                    resource != null -> resource
                    // Corda OS FinanceApp in v3 has no Liquibase script, so no error is raised
                    (mappedSchema::class.qualifiedName == "net.corda.finance.schemas.CashSchemaV1" || mappedSchema::class.qualifiedName == "net.corda.finance.schemas.CommercialPaperSchemaV1") && mappedSchema.migrationResource == null -> null
                    else -> throw MissingMigrationException(mappedSchema)
                }
            }

            val path = currentDirectory?.toString()
            if (path != null) {
                System.setProperty(NODE_BASE_DIR_KEY, path) // base dir for any custom change set which may need to load a file (currently AttachmentVersionNumberMigration)
            }
            System.setProperty(NODE_X500_NAME, ourName.toString())
            val customResourceAccessor = CustomResourceAccessor(dynamicInclude, changelogList, classLoader)
            checkResourcesInClassPath(changelogList)

            // current version of Liquibase appears to be non-threadsafe
            // this is apparent when multiple in-process nodes are all running migrations simultaneously
            mutex.withLock {
                val liquibase = Liquibase(dynamicInclude, customResourceAccessor, getLiquibaseDatabase(JdbcConnection(connection)))

                val unRunChanges = liquibase.listUnrunChangeSets(Contexts(), LabelExpression())

                when {
                    (run && !check) && (unRunChanges.isNotEmpty() && existingCheckpoints!!) -> throw CheckpointsException() // Do not allow database migration when there are checkpoints
                    run && !check -> liquibase.update(Contexts())
                    check && !run && unRunChanges.isNotEmpty() -> throw OutstandingDatabaseChangesException(unRunChanges.size)
                    check && !run -> {
                    } // Do nothing will be interpreted as "check succeeded"
                    else -> throw IllegalStateException("Invalid usage.")
                }
            }
        }
    }

    private fun getLiquibaseDatabase(conn: JdbcConnection): Database {
        return DatabaseFactory.getInstance().findCorrectDatabaseImplementation(conn)
    }

    /** For existing database created before verions 4.0 add Liquibase support - creates DATABASECHANGELOG and DATABASECHANGELOGLOCK tables and marks changesets as executed. */
    private fun migrateOlderDatabaseToUseLiquibase(existingCheckpoints: Boolean): Boolean {
        val isFinanceAppWithLiquibase = schemas.any { schema ->
            (schema::class.qualifiedName == "net.corda.finance.schemas.CashSchemaV1"
                    || schema::class.qualifiedName == "net.corda.finance.schemas.CommercialPaperSchemaV1")
                    && schema.migrationResource != null
        }
        val noLiquibaseEntryLogForFinanceApp: (Statement) -> Boolean = {
            it.execute("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE FILENAME IN ('migration/cash.changelog-init.xml','migration/commercial-paper.changelog-init.xml')")
            if (it.resultSet.next())
                it.resultSet.getInt(1) == 0
            else
                true
        }

        val (isExistingDBWithoutLiquibase, isFinanceAppWithLiquibaseNotMigrated) = dataSource.connection.use {

            val existingDatabase = it.metaData.getTables(null, null, "NODE%", null).next()

            val hasLiquibase = it.metaData.getTables(null, null, "DATABASECHANGELOG%", null).next()

            val isFinanceAppWithLiquibaseNotMigrated = isFinanceAppWithLiquibase // If Finance App is pre v4.0 then no need to migrate it so no need to check.
                    && existingDatabase
                    && (!hasLiquibase // Migrate as other tables.
                         || (hasLiquibase && it.createStatement().use { noLiquibaseEntryLogForFinanceApp(it) })) // If Liquibase is already in the database check if Finance App schema log is missing.

            Pair(existingDatabase && !hasLiquibase, isFinanceAppWithLiquibaseNotMigrated)
        }

        if (isExistingDBWithoutLiquibase && existingCheckpoints)
            throw CheckpointsException()

        // Schema migrations pre release 4.0
        val preV4Baseline = mutableListOf<String>()
        if (isExistingDBWithoutLiquibase) {
            preV4Baseline.addAll(listOf("migration/common.changelog-init.xml",
                    "migration/node-info.changelog-init.xml",
                    "migration/node-info.changelog-v1.xml",
                    "migration/node-info.changelog-v2.xml",
                    "migration/node-core.changelog-init.xml",
                    "migration/node-core.changelog-v3.xml",
                    "migration/node-core.changelog-v4.xml",
                    "migration/node-core.changelog-v5.xml",
                    "migration/node-core.changelog-pkey.xml",
                    "migration/vault-schema.changelog-init.xml",
                    "migration/vault-schema.changelog-v3.xml",
                    "migration/vault-schema.changelog-v4.xml",
                    "migration/vault-schema.changelog-pkey.xml"))

            if (schemas.any { schema -> schema.migrationResource == "node-notary.changelog-master" })
                preV4Baseline.addAll(listOf("migration/node-notary.changelog-init.xml",
                        "migration/node-notary.changelog-v1.xml"))

            if (schemas.any { schema -> schema.migrationResource == "notary-raft.changelog-master" })
                preV4Baseline.addAll(listOf("migration/notary-raft.changelog-init.xml",
                        "migration/notary-raft.changelog-v1.xml"))

            if (schemas.any { schema -> schema.migrationResource == "notary-bft-smart.changelog-master" })
                preV4Baseline.addAll(listOf("migration/notary-bft-smart.changelog-init.xml",
                        "migration/notary-bft-smart.changelog-v1.xml"))
        }
        if (isFinanceAppWithLiquibaseNotMigrated) {
            preV4Baseline.addAll(listOf("migration/cash.changelog-init.xml",
                    "migration/cash.changelog-v1.xml",
                    "migration/commercial-paper.changelog-init.xml",
                    "migration/commercial-paper.changelog-v1.xml"))
        }

        if (preV4Baseline.isNotEmpty()) {
            val dynamicInclude = "master.changelog.json" // Virtual file name of the changelog that includes all schemas.
            checkResourcesInClassPath(preV4Baseline)
            dataSource.connection.use { connection ->
                val customResourceAccessor = CustomResourceAccessor(dynamicInclude, preV4Baseline, classLoader)
                val liquibase = Liquibase(dynamicInclude, customResourceAccessor, getLiquibaseDatabase(JdbcConnection(connection)))
                liquibase.changeLogSync(Contexts(), LabelExpression())
            }
        }
        return isExistingDBWithoutLiquibase || isFinanceAppWithLiquibaseNotMigrated
    }

    private fun checkResourcesInClassPath(resources: List<String?>) {
        for (resource in resources) {
            if (resource != null && classLoader.getResource(resource) == null) {
                throw DatabaseMigrationException("Could not find Liquibase database migration script $resource. Please ensure the jar file containing it is deployed in the cordapps directory.")
            }
        }
    }
}

open class DatabaseMigrationException(message: String) : IllegalArgumentException(message) {
    override val message: String = super.message!!
}

class MissingMigrationException(@Suppress("MemberVisibilityCanBePrivate") val mappedSchema: MappedSchema) : DatabaseMigrationException(errorMessageFor(mappedSchema)) {
    internal companion object {
        fun errorMessageFor(mappedSchema: MappedSchema): String = "No migration defined for schema: ${mappedSchema.name} v${mappedSchema.version}"
    }
}

class OutstandingDatabaseChangesException(@Suppress("MemberVisibilityCanBePrivate") private val count: Int) : DatabaseMigrationException(errorMessageFor(count)) {
    internal companion object {
        fun errorMessageFor(count: Int): String = "There are $count outstanding database changes that need to be run."
    }
}

class CheckpointsException : DatabaseMigrationException("Attempting to update the database while there are flows in flight. " +
        "This is dangerous because the node might not be able to restore the flows correctly and could consequently fail. " +
        "Updating the database would make reverting to the previous version more difficult. " +
        "Please drain your node first. See: https://docs.corda.net/upgrading-cordapps.html#flow-drains")

class DatabaseIncompatibleException(@Suppress("MemberVisibilityCanBePrivate") private val reason: String) : DatabaseMigrationException(errorMessageFor(reason)) {
    internal companion object {
        fun errorMessageFor(reason: String): String = "Incompatible database schema version detected, please run the node with configuration option database.initialiseSchema=true. Reason: $reason"
    }
}