package net.corda.nodeapi.internal.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.LiquibaseException
import liquibase.resource.ClassLoaderResourceAccessor
import net.corda.core.identity.CordaX500Name
import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.MigrationHelpers.getMigrationResource
import net.corda.nodeapi.internal.cordapp.CordappLoader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.sql.Connection
import java.sql.Statement
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import kotlin.concurrent.withLock

// Migrate the database to the current version, using liquibase.
open class SchemaMigration(
        val dataSource: DataSource,
        cordappLoader: CordappLoader? = null,
        private val currentDirectory: Path?,
        // This parameter is used by the vault state migration to establish what the node's legal identity is when setting up
        // its copy of the identity service. It is passed through using a system property. When multiple identity support is added, this will need
        // reworking so that multiple identities can be passed to the migration.
        private val ourName: CordaX500Name? = null,
        // This parameter forces an error to be thrown if there are missing migrations. When using H2, Hibernate will automatically create schemas where they are
        // missing, so no need to throw unless you're specifically testing whether all the migrations are present.
        private val forceThrowOnMissingMigration: Boolean = false,
        protected val databaseFactory: LiquibaseDatabaseFactory = LiquibaseDatabaseFactoryImpl()) {

    companion object {
        private val logger = contextLogger()
        const val NODE_BASE_DIR_KEY = "liquibase.nodeDaseDir"
        const val NODE_X500_NAME = "liquibase.nodeName"
        val loader = ThreadLocal<CordappLoader>()
        @JvmStatic
        protected val mutex = ReentrantLock()
    }

    init {
        loader.set(cordappLoader)
    }

    private val classLoader = cordappLoader?.appClassLoader ?: Thread.currentThread().contextClassLoader

     /**
     * Will run the Liquibase migration on the actual database.
     */
     fun runMigration(existingCheckpoints: Boolean, schemas: Set<MappedSchema>) {
         migrateOlderDatabaseToUseLiquibase(existingCheckpoints, schemas)
         val resourcesAndSourceInfo = prepareResources(schemas)

         // current version of Liquibase appears to be non-threadsafe
         // this is apparent when multiple in-process nodes are all running migrations simultaneously
         mutex.withLock {
             dataSource.connection.use { connection ->
                 val (runner, _, shouldBlockOnCheckpoints) = prepareRunner(connection, resourcesAndSourceInfo)
                 if (shouldBlockOnCheckpoints && existingCheckpoints)
                     throw CheckpointsException()
                 try {
                     runner.update(Contexts().toString())
                 } catch (exp: LiquibaseException) {
                     throw DatabaseMigrationException(exp.message, exp)
                 }
             }
         }
     }

    /**
     * Ensures that the database is up to date with the latest migration changes.
     */
    fun checkState(schemas: Set<MappedSchema>) {
        val resourcesAndSourceInfo = prepareResources(schemas)

        // current version of Liquibase appears to be non-threadsafe
        // this is apparent when multiple in-process nodes are all running migrations simultaneously
        mutex.withLock {
            dataSource.connection.use { connection ->
                val (_, changeToRunCount, _) = prepareRunner(connection, resourcesAndSourceInfo)
                if (changeToRunCount > 0)
                    throw OutstandingDatabaseChangesException(changeToRunCount)
            }
        }
    }

    /**  Create a resource accessor that aggregates the changelogs included in the schemas into one dynamic stream. */
    protected class CustomResourceAccessor(val dynamicInclude: String, val changelogList: List<String?>, classLoader: ClassLoader) :
            ClassLoaderResourceAccessor(classLoader) {
        override fun getResourcesAsStream(path: String): Set<InputStream> {
            if (path == dynamicInclude) {
                // Create a map in Liquibase format including all migration files.
                val includeAllFiles = mapOf("databaseChangeLog"
                        to changelogList.filterNotNull().map { file -> mapOf("include" to mapOf("file" to file)) })

                // Transform it to json.
                val includeAllFilesJson = ObjectMapper().writeValueAsBytes(includeAllFiles)

                // Return the json as a stream.
                return setOf(ByteArrayInputStream(includeAllFilesJson))
            }
            return super.getResourcesAsStream(path)?.take(1)?.toSet() ?: emptySet()
        }
    }

    private fun logOrThrowMigrationError(mappedSchema: MappedSchema): String? =
            if (forceThrowOnMissingMigration) {
                throw MissingMigrationException(mappedSchema)
            } else {
                logger.warn(MissingMigrationException.errorMessageFor(mappedSchema))
                null
            }

    // Virtual file name of the changelog that includes all schemas.
    val dynamicInclude = "master.changelog.json"

    protected fun prepareResources(schemas: Set<MappedSchema>): List<Pair<CustomResourceAccessor, String>> {
        // Collect all changelog files referenced in the included schemas.
        val changelogList = schemas.mapNotNull { mappedSchema ->
            val resource = getMigrationResource(mappedSchema, classLoader)
            when {
                resource != null -> resource
                // Corda OS FinanceApp in v3 has no Liquibase script, so no error is raised
                (mappedSchema::class.qualifiedName == "net.corda.finance.schemas.CashSchemaV1" || mappedSchema::class.qualifiedName == "net.corda.finance.schemas.CommercialPaperSchemaV1") && mappedSchema.migrationResource == null -> null
                else -> logOrThrowMigrationError(mappedSchema)
            }
        }

        val path = currentDirectory?.toString()
        if (path != null) {
            System.setProperty(NODE_BASE_DIR_KEY, path) // base dir for any custom change set which may need to load a file (currently AttachmentVersionNumberMigration)
        }
        if (ourName != null) {
            System.setProperty(NODE_X500_NAME, ourName.toString())
        }
        val customResourceAccessor = CustomResourceAccessor(dynamicInclude, changelogList, classLoader)
        checkResourcesInClassPath(changelogList)
        return listOf(Pair(customResourceAccessor, ""))
    }

    protected fun prepareRunner(connection: Connection,
                                resourcesAndSourceInfo: List<Pair<CustomResourceAccessor, String>>): Triple<Liquibase, Int, Boolean> {
        require(resourcesAndSourceInfo.size == 1)
        val liquibase = Liquibase(dynamicInclude, resourcesAndSourceInfo.single().first, databaseFactory.getLiquibaseDatabase(JdbcConnection(connection)))

        val unRunChanges = liquibase.listUnrunChangeSets(Contexts(), LabelExpression())
        return Triple(liquibase, unRunChanges.size, !unRunChanges.isEmpty())
    }

    /** For existing database created before verions 4.0 add Liquibase support - creates DATABASECHANGELOG and DATABASECHANGELOGLOCK tables and marks changesets as executed. */
    private fun migrateOlderDatabaseToUseLiquibase(existingCheckpoints: Boolean, schemas: Set<MappedSchema>): Boolean {
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
                    // Lower case names for PostgreSQL
                    || it.metaData.getTables(null, null, "node%", null).next()

            val hasLiquibase = it.metaData.getTables(null, null, "DATABASECHANGELOG%", null).next()
                    // Lower case names for PostgreSQL
                    || it.metaData.getTables(null, null, "databasechangelog%", null).next()

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
                val liquibase = Liquibase(dynamicInclude, customResourceAccessor, databaseFactory.getLiquibaseDatabase(JdbcConnection(connection)))
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

open class DatabaseMigrationException(message: String?, cause: Throwable? = null) : IllegalArgumentException(message, cause) {
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
        "Updating the database would make reverting to the previous version more difficult.")

class DatabaseIncompatibleException(@Suppress("MemberVisibilityCanBePrivate") private val reason: String) : DatabaseMigrationException(errorMessageFor(reason)) {
    internal companion object {
        fun errorMessageFor(reason: String): String = "Incompatible database schema version detected, please run schema migration scripts (node with sub-command run-migration-scripts). Reason: $reason"
    }
}