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
      * @param existingCheckpoints Whether checkpoints exist that would prohibit running a migration
      * @param schemas The set of MappedSchemas to check
      * @param forceThrowOnMissingMigration throws an exception if a mapped schema is missing the migration resource. Can be set to false
      *                                      when allowing hibernate to create missing schemas in dev or tests.
     */
     fun runMigration(existingCheckpoints: Boolean, schemas: Set<MappedSchema>, forceThrowOnMissingMigration: Boolean) {
         val resourcesAndSourceInfo = prepareResources(schemas, forceThrowOnMissingMigration)

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
     * @param schemas The set of MappedSchemas to check
     * @param forceThrowOnMissingMigration throws an exception if a mapped schema is missing the migration resource. Can be set to false
     *                                      when allowing hibernate to create missing schemas in dev or tests.
     */
    fun checkState(schemas: Set<MappedSchema>, forceThrowOnMissingMigration: Boolean) {
        val resourcesAndSourceInfo = prepareResources(schemas, forceThrowOnMissingMigration)

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

    /**
     * Returns the count of pending database migration changes
     * @param schemas The set of MappedSchemas to check
     * @param forceThrowOnMissingMigration throws an exception if a mapped schema is missing the migration resource. Can be set to false
     *                                      when allowing hibernate to create missing schemas in dev or tests.
     */
    fun getPendingChangesCount(schemas: Set<MappedSchema>, forceThrowOnMissingMigration: Boolean) : Int {
        val resourcesAndSourceInfo = prepareResources(schemas, forceThrowOnMissingMigration)

        // current version of Liquibase appears to be non-threadsafe
        // this is apparent when multiple in-process nodes are all running migrations simultaneously
        mutex.withLock {
            dataSource.connection.use { connection ->
                val (_, changeToRunCount, _) = prepareRunner(connection, resourcesAndSourceInfo)
                return changeToRunCount;
            }
        }
    }

    /**
     * Synchronises the changelog table with the schema descriptions passed in without applying any of the changes to the database.
     * This can be used when migrating a CorDapp that had its schema generated by hibernate to liquibase schema migration, or when
     * updating from a version of Corda that does not use liquibase for CorDapps
     * **Warning** - this will not check if the matching schema changes have been applied, it will just generate the changelog
     * It must not be run on a newly installed CorDapp.
     * @param schemas The set of schemas to add to the changelog
     * @param forceThrowOnMissingMigration throw an exception if a mapped schema is missing its migration resource
     */
    fun synchroniseSchemas(schemas: Set<MappedSchema>, forceThrowOnMissingMigration: Boolean) {
        val resourcesAndSourceInfo = prepareResources(schemas, forceThrowOnMissingMigration)

        // current version of Liquibase appears to be non-threadsafe
        // this is apparent when multiple in-process nodes are all running migrations simultaneously
        mutex.withLock {
            dataSource.connection.use { connection ->
                val (runner, _, _) = prepareRunner(connection, resourcesAndSourceInfo)
                try {
                    runner.changeLogSync(Contexts().toString())
                } catch (exp: LiquibaseException) {
                    throw DatabaseMigrationException(exp.message, exp)
                }
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

    private fun logOrThrowMigrationError(mappedSchema: MappedSchema, forceThrowOnMissingMigration: Boolean): String? =
            if (forceThrowOnMissingMigration) {
                throw MissingMigrationException(mappedSchema)
            } else {
                logger.warn(MissingMigrationException.errorMessageFor(mappedSchema))
                null
            }

    // Virtual file name of the changelog that includes all schemas.
    val dynamicInclude = "master.changelog.json"

    protected fun prepareResources(schemas: Set<MappedSchema>, forceThrowOnMissingMigration: Boolean): List<Pair<CustomResourceAccessor, String>> {
        // Collect all changelog files referenced in the included schemas.
        val changelogList = schemas.mapNotNull { mappedSchema ->
            val resource = getMigrationResource(mappedSchema, classLoader)
            when {
                resource != null -> resource
                else -> logOrThrowMigrationError(mappedSchema, forceThrowOnMissingMigration)
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