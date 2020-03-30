package net.corda.nodeapi.internal.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.pool.HikariPool
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.changelog.visitor.AbstractChangeExecListener
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.ObjectQuotingStrategy
import liquibase.database.core.MSSQLDatabase
import liquibase.database.core.PostgresDatabase
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ChangeLogParseException
import liquibase.exception.LiquibaseException
import liquibase.exception.MigrationFailedException
import liquibase.exception.SetupException
import liquibase.lockservice.LockServiceFactory
import liquibase.logging.LogService
import liquibase.logging.LoggerContext
import liquibase.logging.core.NoOpLoggerContext
import liquibase.logging.core.Slf4JLoggerFactory
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.structure.DatabaseObject
import liquibase.structure.core.Schema
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.toPath
import net.corda.core.schemas.MappedSchema
import net.corda.nodeapi.internal.MigrationHelpers.getMigrationResource
import net.corda.nodeapi.internal.MigrationHelpers.migrationResourceNameForSchema
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.nodeapi.internal.logging.KeyValueFormatter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.Writer
import java.net.URL
import java.nio.file.Path
import java.sql.Statement
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import kotlin.concurrent.withLock

// Migrate the database to the current version, using liquibase.
// Suppressing these rules here because fixing them would make the merge from OS to ENT a nightmare
@Suppress("MaxLineLength", "TooManyFunctions")
class SchemaMigration(
        val schemas: Set<MappedSchema>,
        val dataSource: DataSource,
        private val databaseConfig: DatabaseConfig,
        cordappLoader: CordappLoader? = null,
        private val currentDirectory: Path?,
        // This parameter is used by the vault state migration to establish what the node's legal identity is when setting up
        // its copy of the identity service. It is passed through using a system property. When multiple identity support is added, this will need
        // reworking so that multiple identities can be passed to the migration.
        private val ourName: CordaX500Name? = null,
        // This parameter forces an error to be thrown if there are missing migrations. When using H2, Hibernate will automatically create schemas where they are
        // missing, so no need to throw unless you're specifically testing whether all the migrations are present.
        private val forceThrowOnMissingMigration: Boolean = false) {

    private val jarToMappedSchemas: Map<URL?, Set<MappedSchema>> =
            cordappLoader?.cordapps?.associate { it.jarPath as URL? to it.customSchemas } ?: emptyMap()

    companion object {
        val logger: Logger = LoggerFactory.getLogger("databaseInitialisation")
        const val NODE_BASE_DIR_KEY = "liquibase.nodeDaseDir"
        const val NODE_X500_NAME = "liquibase.nodeName"
        const val DRY_RUN = "liquibase_dryRun"
        val loader = ThreadLocal<CordappLoader>()
        private val mutex = ReentrantLock()
        val formatter = KeyValueFormatter(logger.name.capitalize())

        /** Log a INFO level message produced by evaluating the given lambda, but only if INFO logging is enabled. */
        private inline fun Logger.info(msg: () -> String) {
            if (isInfoEnabled) info(msg())
        }

        private const val NODE_SOURCE = "node"

        private val errorStatus = Pair("status", "error")
        private val startStatus = Pair("status", "start")
        private val successStatus = Pair("status", "successful")
        private val toBeRunStatus = Pair("status", "to be run")

        private fun String?.sourceMsg() = Pair("source", this ?: NODE_SOURCE)
        private fun SchemaMigrationError.errorCodeMsg() = Pair("error_code", this.code.toString())
        private fun Exception.errorMsg() = Pair("message", this.message ?: "")
        private fun String.changesetMsg() = Pair("changeset", this)

        private fun logDatabaseMigrationFailure(t: Exception) {
            val errorCode = SchemaMigrationError.fromThrowable(t)
            logger.error(formatter.format(errorStatus,
                    errorCode.errorCodeMsg(),
                    t.errorMsg()))
        }

        private fun logDatabaseMigrationStart() {
            logger.info { formatter.format(startStatus) }
        }

        private fun logDatabaseMigrationSuccess() {
            logger.info { formatter.format(successStatus) }
        }

        private fun logDatabaseMigrationChangeSetStart(changeSet: String, cordapp: String?) {
            logger.info {
                formatter.format(changeSet.changesetMsg(),
                        cordapp.sourceMsg(),
                        startStatus)
            }
        }

        fun logDatabaseMigrationChangeSetEnd(changeSet: String, cordapp: String?) {
            logger.info {
                formatter.format(changeSet.changesetMsg(),
                        cordapp.sourceMsg(),
                        successStatus)
            }
        }

        fun logDatabaseMigrationChangeSetError(t: Exception, changeSet: String, cordapp: String?) {
            val errorCode = SchemaMigrationError.fromThrowable(t)
            logger.error(formatter.format(changeSet.changesetMsg(),
                    cordapp.sourceMsg(),
                    errorStatus,
                    errorCode.errorCodeMsg(),
                    t.errorMsg()))
        }

        fun logDatabaseMigrationScriptMissing(t: Exception, script: String, cordapp: String?) {
            val errorCode = SchemaMigrationError.fromThrowable(t)
            logger.error(formatter.format(Pair("file", script),
                    cordapp.sourceMsg(),
                    errorStatus,
                    errorCode.errorCodeMsg(),
                    t.errorMsg()))
        }

        fun logDatabaseMigrationScriptParsingError(t: Exception, resources: List<String?>, cordapp: String?) {
            val errorCode = SchemaMigrationError.fromThrowable(t)
            val file = if (resources.size == 1) resources.single() else resources.toString()
            logger.error(formatter.format(Pair("file", file ?: "null"),
                    cordapp.sourceMsg(),
                    errorStatus,
                    errorCode.errorCodeMsg(),
                    t.errorMsg()))
        }

        fun logDatabaseMigrationChangeSetToBeRun(changeSet: String, cordapp: String?) {
            logger.info {
                formatter.format(changeSet.changesetMsg(),
                        cordapp.sourceMsg(),
                        toBeRunStatus)
            }
        }

        fun logDatabaseMigrationCount(count: Int) {
            logger.info { formatter.format("changeset_count", "$count") }
        }

        @Suppress("TooGenericExceptionCaught")
        fun logDatabaseInitialisationStartAndFinish(block: () -> Unit) {
            logDatabaseMigrationStart()
            try {
                block()
            } catch (exp: Exception) {
                val exceptionWithContext = if (exp is HibernateSchemaChangeException) DatabaseIncompatibleException(exp) else exp
                logDatabaseMigrationFailure(exceptionWithContext)
                throw exceptionWithContext
            }
            logDatabaseMigrationSuccess()
        }
    }

    init {
        loader.set(cordappLoader)
        //Disable Liquibase's MDC logging as it adds unneeded cruft and breaks formatting
        LogService.setLoggerFactory(object : Slf4JLoggerFactory() {
            override fun pushContext(key: String, `object`: Any): LoggerContext = NoOpLoggerContext()
        })
    }

    private val classLoader = cordappLoader?.appClassLoader ?: Thread.currentThread().contextClassLoader

    /**
     * Main entry point to the schema migration.
     * Called during node startup.
     */
    fun nodeStartup(existingCheckpoints: Boolean, isH2Database: Boolean) {
        when {
            //Enterprise - OS diff: the initialiseSchema flag is checked against a H2 database only
            isH2Database && databaseConfig.initialiseSchema -> {
                migrateOlderDatabaseToUseLiquibase(existingCheckpoints)
                runMigration(existingCheckpoints)
            }
            //Enterprise only runMigration flag
            !isH2Database && databaseConfig.runMigration -> runMigration(existingCheckpoints)
            else -> checkState()
        }
    }

    /**
     * Will run the Liquibase migration on the actual database.
     */
    fun runMigration(existingCheckpoints: Boolean, statusLogger: Logger? = null) = doRunMigration(run = true, outputWriter = null, check = false, existingCheckpoints = existingCheckpoints, statusLogger = statusLogger)

    /**
     * Will write the migration to a [Writer].
     */
    fun generateMigrationScript(writer: Writer) = doRunMigration(run = false, outputWriter = writer, check = false)

    /**
     * Ensures that the database is up to date with the latest migration changes.
     */
    fun checkState() = doRunMigration(run = false, outputWriter = null, check = true)

    /**
     * Can be used from an external tool to release the lock in case something went terribly wrong.
     */
    fun forceReleaseMigrationLock() {
        dataSource.connection.use { connection ->
            LockServiceFactory.getInstance().getLockService(getLiquibaseDatabase(JdbcConnection(connection))).forceReleaseLock()
        }
    }

    /**  Create a resource accessor that aggregate the changelogs included in the schemas into one dynamic stream. */
    private class CustomResourceAccessor(val dynamicInclude: String, val changelogList: List<String?>, classLoader: ClassLoader) :
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

    // For backward compatibility reasons, when failOnMigrationMissing=false,
    // we don't manage CorDapps via Liquibase but use the hibernate hbm2ddl=update.
    private fun logOrThrowMigrationError(mappedSchema: MappedSchema, jar: String) {
        if (forceThrowOnMissingMigration) {
            val migrationError = MissingMigrationException(mappedSchema)
            logDatabaseMigrationScriptMissing(migrationError, migrationResourceNameForSchema(mappedSchema), jar)
            throw migrationError
        } else {
            logger.warn(MissingMigrationException(mappedSchema).message)
        }
    }

    private fun doRunMigration(
            run: Boolean,
            outputWriter: Writer?,
            check: Boolean,
            existingCheckpoints: Boolean? = null,
            statusLogger: Logger? = null
    ) {
        // Virtual file name of the changelog that includes all schemas.
        val dynamicInclude = "master.changelog.json"

        dataSource.connection.use { connection ->

            // Associate each MappedSchema with a Jar from within its source, so the JAR name is known when encountering an error
            val schemasAndSourceJar: List<Pair<MappedSchema, String>> = schemas.map { mappedSchema ->
                val jar = jarToMappedSchemas.filter { entry -> entry.value.contains(mappedSchema) }.keys.firstOrNull()
                val name = jar?.let { it.toPath().fileName.toString() } ?: NODE_SOURCE
                Pair(mappedSchema, name)
            }

            // Each MappedSchema is transformed into a Liquibase object holding a "main" script,
            // missing a "main" script will be reported alongside the JAR which contained the MappedSchema (else case)
            val resourcesAndSourceInfo: List<Pair<CustomResourceAccessor, String>> = schemasAndSourceJar.mapNotNull { (mappedSchema, jar) ->
                // Collect all changelog file referenced in the included schemas.
                val resource = getMigrationResource(mappedSchema, classLoader)
                when {
                    resource != null -> {
                        Pair(CustomResourceAccessor(dynamicInclude, listOf(resource), classLoader), jar)
                    }
                    // OS v3 Finance CordApps has no Liquibase script, so no error is raised
                    (mappedSchema::class.qualifiedName == "net.corda.finance.schemas.CashSchemaV1"
                            || mappedSchema::class.qualifiedName == "net.corda.finance.schemas.CommercialPaperSchemaV1")
                            && mappedSchema.migrationResource == null -> null
                    else -> {
                        logOrThrowMigrationError(mappedSchema, jar)
                        null
                    }
                }
            }

            val path = currentDirectory?.toString()
            if (path != null) {
                // base dir for any custom change set which may need to load a file (currently AttachmentVersionNumberMigration)
                System.setProperty(NODE_BASE_DIR_KEY, path)
            }
            if (ourName != null) {
                System.setProperty(NODE_X500_NAME, ourName.toString())
            }


            // current version of Liquibase appears to be non-threadsafe
            // this is apparent when multiple in-process nodes are all running migrations simultaneously
            mutex.withLock {

                // For each MappedSchema "main" script run connect to database and compute changesets which hasn't been applied
                // This is the occurrence where the scripts referenced from the "main" one are accessed
                // and they may be missing (files) or contains syntax errors, or then some issues when checking against database
                // "main" script of each MappedSchema is run separatelly to capture the context (JAR file, MappedSchema name in case the error occurs)
                val jarsToUnRunChanges: List<Pair<List<ChangeSet>, String>> = resourcesAndSourceInfo.map {
                    Liquibase(dynamicInclude, it.first, getLiquibaseDatabase(JdbcConnection(connection))).let { runner ->
                        runner.adhereSchemaNamespace(databaseConfig.schema)
                        try {
                            Pair(runner.listUnrunChangeSets(Contexts(), LabelExpression()), it.second)
                        } catch (exp: LiquibaseException) {
                            val missingNestedFileError = MissingNestedMigrationException.fromExceptionMessage(exp, it.first.changelogList)
                            if (missingNestedFileError != null) {
                                logDatabaseMigrationScriptMissing(missingNestedFileError, missingNestedFileError.migrationFile, it.second)
                                throw missingNestedFileError
                            } else {
                                val migrationExp = DatabaseMigrationException(exp.message, exp)
                                logDatabaseMigrationScriptParsingError(migrationExp, it.first.changelogList, it.second)
                                throw migrationExp
                            }
                        }
                    }
                }

                // All changesets to be run for all MappedSchemas are collected in one change log,
                // as they will be run in a single batch
                // The context of each Liquibase changeset is retained by a passing a class CordaChangeExecListener which extends a Liquibase change set hook
                // and has a changeset name context natively and correlates to a MappedSchema via changesToJars map
                val changeLog = DatabaseChangeLog()
                jarsToUnRunChanges.map { it.first }.flatten().forEach { changeLog.addChangeSet(it) }

                val changeToRunCount = changeLog.changeSets.size
                logDatabaseMigrationCount(changeToRunCount)
                statusLogger?.info(if (changeToRunCount > 0) "Changesets to run: $changeToRunCount" else "Database is up to date.")

                if (logger.isInfoEnabled) {
                    jarsToUnRunChanges.forEach { (unRunChanges, jar) ->
                        unRunChanges.forEach {
                            logDatabaseMigrationChangeSetToBeRun(it.toString(), jar)
                        }
                    }
                }

                val shouldBlockOnCheckpoints = changeLog.changeSets.any {
                    // When migrating between Corda versions, it's possible that changes may be made that invalidates the contents
                    // of the checkpoint table. If there are any checkpoint entries in this case, then prevent the migration occurring.
                    // Note however that this should not happen in all cases. Apps may also provide migrations,
                    // and blocking these may prevent a finalized transaction from being recovered from a checkpoint,
                    // if the migration is provided by a jar that also provides the contract for that transaction's states.
                    it.id == "modify checkpoint_value column type" || // Changes the checkpoint blob type in the database
                            it.id == "nullability" ||                     // Changes column constraint on checkpoint table
                            it.id == "column_host_name" ||                // Node was previously running version prior to ENT3.2
                            it.id == "create-external-id-to-state-party-view"  // Node was previously running a version prior to ENT4.0
                }

                val changesToJars: Map<ChangeSet, String> = jarsToUnRunChanges.flatMap { (changeSets, jar) ->
                    changeSets.map { it to jar }
                }.associate { it.first to it.second }
                val runner = Liquibase(changeLog, null, getLiquibaseDatabase(JdbcConnection(connection))).apply {
                    adhereSchemaNamespace(databaseConfig.schema)
                    setChangeExecListener(CordaChangeExecListener(changesToJars))
                }

                when {
                    (run && !check) && (shouldBlockOnCheckpoints && existingCheckpoints!!) -> {
                        // Do not allow database migration when there are checkpoints
                        throw CheckpointsException()
                    }
                    run && !check -> try {
                        runner.update(Contexts().toString())
                    } catch (exp: LiquibaseException) {
                        throw DatabaseMigrationException(exp.message, exp)
                    }
                    check && !run && changeToRunCount > 0 -> throw OutstandingDatabaseChangesException(changeToRunCount)
                    check && !run -> {
                    } // Do nothing will be interpreted as "check succeeded"
                    outputWriter != null && !check && !run -> {
                        // Enterprise only
                        runner.changeLogParameters.set(DRY_RUN, "true")
                        System.setProperty(DRY_RUN, "true") // Enterprise only: disable VaultSchemaMigration for dry-run
                        runner.update(Contexts(), outputWriter)
                    }
                    else -> throw IllegalStateException("Invalid usage.")
                }
            }
        }
    }

    private fun Liquibase.adhereSchemaNamespace(schemaName: String?) {
        if (!schemaName.isNullOrBlank()) {
            if (this.database.defaultSchemaName != schemaName) {
                logger.debug("defaultSchemaName=${this.database.defaultSchemaName} changed to $schemaName")
                this.database.defaultSchemaName = schemaName
            }
            if (this.database.liquibaseSchemaName != schemaName) {
                logger.debug("liquibaseSchemaName=${this.database.liquibaseSchemaName} changed to $schemaName")
                this.database.liquibaseSchemaName = schemaName
            }
        }
        logger.info("defaultSchemaName=${this.database.defaultSchemaName}, liquibaseSchemaName=${this.database.liquibaseSchemaName}, " +
                "outputDefaultSchema=${this.database.outputDefaultSchema}, objectQuotingStrategy=${this.database.objectQuotingStrategy}")
    }

    private fun getLiquibaseDatabase(conn: JdbcConnection): Database {

        // Enterprise only
        // The standard MSSQLDatabase in Liquibase does not support sequences for Ms Azure.
        // this class just overrides that behaviour
        class AzureDatabase(conn: JdbcConnection) : MSSQLDatabase() {
            init {
                this.connection = conn
            }

            override fun getShortName(): String = "azure"

            override fun supportsSequences(): Boolean = true
        }

        // Enterprise only
        // Postgres - Wrap schema name into double quotes to preserve case sensitivity
        class PostgresDatabaseFixed(conn: JdbcConnection) : PostgresDatabase() {
            init {
                this.connection = conn
            }

            override fun getPriority(): Int { return super.getPriority() + 1 }

            // Liquibase runners manages to clear quotingStrategy, despite being set before as default LEGACY,
            // Use LEGACY value instead of null.
            override fun setObjectQuotingStrategy(quotingStrategy: ObjectQuotingStrategy?) {
                super.setObjectQuotingStrategy(quotingStrategy ?: ObjectQuotingStrategy.LEGACY)
            }

            // For PostgreSQL if a schema name has uppercase or lowercase characters only then Liquibase would add it to generated query
            // without double quotes and effectively make them lowercase. This is inconsistent with Corda which wraps schema name for PostgreSQL in double quotes.
            // The overridden method ensures Liquibase wraps schema name into double quotes.
            override fun mustQuoteObjectName(objectName: String, objectType: Class<out DatabaseObject>?): Boolean {
                return if (objectType == Schema::class.java)
                    true
                else
                    super.mustQuoteObjectName(objectName, objectType)
            }

            override fun correctObjectName(objectName: String?, objectType: Class<out DatabaseObject>?): String? {
                return if (objectType == Schema::class.java)
                    objectName
                else
                    super.correctObjectName(objectName, objectType)
            }
        }

        val liquibaseDbImplementation = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(conn)

        return when (liquibaseDbImplementation) {
            is PostgresDatabase -> PostgresDatabaseFixed(conn) // Enterprise only
            is MSSQLDatabase -> AzureDatabase(conn) // Enterprise only
            else -> liquibaseDbImplementation
        }
    }

    class CordaChangeExecListener(private val changeLogToCordapp: Map<ChangeSet, String>) : AbstractChangeExecListener() {
        override fun willRun(changeSet: ChangeSet, databaseChangeLog: DatabaseChangeLog, database: Database,
                             runStatus: ChangeSet.RunStatus) {
            logDatabaseMigrationChangeSetStart(changeSet.toString(), changeLogToCordapp[changeSet])
        }

        override fun ran(changeSet: ChangeSet, databaseChangeLog: DatabaseChangeLog, database: Database, execType: ChangeSet.ExecType) {
            logDatabaseMigrationChangeSetEnd(changeSet.toString(), changeLogToCordapp[changeSet])
        }

        override fun runFailed(changeSet: ChangeSet, databaseChangeLog: DatabaseChangeLog, database: Database, exception: Exception) {
            logDatabaseMigrationChangeSetError(exception, changeSet.toString(), changeLogToCordapp[changeSet])
        }
    }

    /** For existing database created before verions 4.0 add Liquibase support
     * - creates DATABASECHANGELOG and DATABASECHANGELOGLOCK tables and marks changesets as executed. */
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

        val (isExistingDBWithoutLiquibase, isFinanceAppWithLiquibaseNotMigrated, migrationFromV3_2) = dataSource.connection.use {

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


            // Enterprise only: the patch v3.2 baseline differs from v3.0 release
            val migrationFromV3_2 = existingDatabase && !hasLiquibase && it.metaData.getColumns(null, null, "NODE_INFO_HOSTS", "HOST_NAME").next()

            Triple(existingDatabase && !hasLiquibase, isFinanceAppWithLiquibaseNotMigrated, migrationFromV3_2)
        }

        if (isExistingDBWithoutLiquibase && existingCheckpoints)
            throw CheckpointsException()

        // Schema migrations pre release 4.0
        val preV4Baseline = mutableListOf<String>()
        if (isExistingDBWithoutLiquibase) {
            preV4Baseline.addAll(listOf("migration/common.changelog-init.xml",
                    "migration/node-info.changelog-init.xml",
                    "migration/node-info.changelog-v1.xml",
                    "migration/node-info.changelog-v2.xml"))

            // Enterprise only: the migration was already run as part of v3.2
            if (migrationFromV3_2)
                preV4Baseline.addAll(listOf("migration/node-info.changelog-v3.xml"))

            preV4Baseline.addAll(listOf(
                    "migration/node-core.changelog-init.xml",
                    "migration/node-core.changelog-v3.xml",
                    "migration/node-core.changelog-v4.xml",
                    "migration/node-core.changelog-v5.xml",
                    "migration/node-core.changelog-pkey.xml"))

            // Enterprise only: the migration was already run as part of v3.2
            if (migrationFromV3_2)
                preV4Baseline.addAll(listOf("migration/node-core.changelog-postgres-blob.xml"))

            preV4Baseline.addAll(listOf(
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

    private fun checkResourcesInClassPath(resources: List<String?>, jar: String? = null) {
        for (resource in resources) {
            if (resource != null && classLoader.getResource(resource) == null) {
                    val error = DatabaseMigrationException("Could not find Liquibase database migration script $resource for $jar. " +
                            "Please ensure the jar file containing it is deployed in the cordapps directory.")
                    logDatabaseMigrationChangeSetError(error, resource, jar)
                }
            }
        }
    }

enum class SchemaMigrationError(val code: Int) {
    UNKNOWN_ERROR(1),
    MISSING_DRIVER(2),
    INVALID_DATA_SOURCE_PROPERTY(3),
    INITIALISATION_ERROR(4),
    MISSING_SCRIPT(5),
    SCRIPT_PARSING_ERROR(6),
    INVALID_SQL_STATEMENT(7),
    INVALID_SQL_TYPE(8),
    INCOMPATIBLE_CHANGE_SET(9),
    OUTSTANDING_CHANGE_SETS(10),
    MAPPED_SCHEMA_INCOMPATIBLE_WITH_DATABASE_MANAGEMENT_SCRIPT(11),
    UNCATEGORISED_DATABASE_MIGRATION_ERROR(999);

    companion object {
        fun fromThrowable(t: Throwable): SchemaMigrationError {
            return when {
                t.cause is ClassNotFoundException -> MISSING_DRIVER
                t.cause is HikariPool.PoolInitializationException -> INITIALISATION_ERROR
                t is MissingMigrationException || t.cause is MissingMigrationException
                        || t is MissingNestedMigrationException-> MISSING_SCRIPT
                t is ChangeLogParseException || t.cause is ChangeLogParseException -> SCRIPT_PARSING_ERROR
                t is CouldNotCreateDataSourceException &&
                        t.cause is RuntimeException && ".+ Property \\S+ does not exist .+".toRegex().matches(t.message ?: "") ->
                    INVALID_DATA_SOURCE_PROPERTY
                t is MigrationFailedException || t.cause is MigrationFailedException -> when {
                    (t.message?.contains("syntax error") ?: false ) /* PostgreSQL */
                            || (t.message?.contains("Incorrect syntax") ?: false) /* SQLServer */ -> INVALID_SQL_STATEMENT
                    (t.message?.contains("DatabaseException: ERROR: type") ?: false) /* PostgreSQL */
                            || (t.message?.contains("Cannot find data type") ?: false) /* SQLServer */ -> INVALID_SQL_TYPE
                    t.message?.contains("Failed SQL:") ?: false -> INCOMPATIBLE_CHANGE_SET
                    else -> UNCATEGORISED_DATABASE_MIGRATION_ERROR
                }
                t is OutstandingDatabaseChangesException -> OUTSTANDING_CHANGE_SETS
                t is DatabaseIncompatibleException || t is HibernateSchemaChangeException || t.cause is HibernateSchemaChangeException ->
                    MAPPED_SCHEMA_INCOMPATIBLE_WITH_DATABASE_MANAGEMENT_SCRIPT
                else -> UNKNOWN_ERROR
            }
        }
    }
}

open class DatabaseMigrationException(message: String?, cause: Throwable? = null) : IllegalArgumentException(message, cause) {
    override val message: String = super.message!!
}

class MissingMigrationException(@Suppress("MemberVisibilityCanBePrivate") val mappedSchema: MappedSchema) :
        DatabaseMigrationException("Missing migration script " +
                "${migrationResourceNameForSchema(mappedSchema)}.[xml/sql/yml/json] " +
                "required by mapped schema ${mappedSchema.name} v${mappedSchema.version}.")

class MissingNestedMigrationException private constructor(val migrationFile: String,
                                                          @Suppress("MemberVisibilityCanBePrivate") val outerMigrationFile: String,
                                                          val reason: String) :
        DatabaseMigrationException("Missing migration script $migrationFile required by $outerMigrationFile. Reason: $reason" ) {
    companion object {
        fun fromExceptionMessage(t: Throwable, changelogList : List<String?>) : MissingNestedMigrationException? {
            val msg = missingNestedFileErrorMessage(t)
            msg ?: return null
            val migrationFile = msg.substringAfter(": ").substringBefore(" does not exist")
            val outerMigrationFile = if (changelogList.size == 1) changelogList.single() ?: "" else changelogList.toString()
            return MissingNestedMigrationException(migrationFile, outerMigrationFile, msg)
        }
        private fun missingNestedFileErrorMessage(t: Throwable): String? {
            if (t is ChangeLogParseException && t.message == "Error parsing master.changelog.json") {
                val cause = t.cause
                if (cause is SetupException) {
                    val msg = cause.message
                    if (msg != null && msg.contains("does not exist"))
                        return msg
                }
            }
            return null
        }
    }
}


class OutstandingDatabaseChangesException(@Suppress("MemberVisibilityCanBePrivate") val count: Int) :
        DatabaseMigrationException("Incompatible database schema version detected. " +
              "Node is configured with option database.runMigration=false or the most recent Database Management Tool has not been run. " +
              "Reason: There are $count outstanding database changes that need to be run.")

class CheckpointsException : DatabaseMigrationException("Attempting to update the database while there are flows in flight. " +
        "This is dangerous because the node might not be able to restore the flows correctly and could consequently fail. " +
        "Updating the database would make reverting to the previous version more difficult. " +
        "Please drain your node first. See: https://docs.corda.net/upgrading-cordapps.html#flow-drains")

class DatabaseIncompatibleException(cause: HibernateSchemaChangeException) :
        DatabaseMigrationException(
                "Incompatible database schema version detected. " +
                "Reason: All database changes are up-to-date however JPA Entity is incompatible with database schema. " +
                "Reason: ${cause.cause?.message}", cause.cause)
