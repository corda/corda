@file:JvmName("DBMigration")

package com.r3.corda.dbmigration

import com.typesafe.config.Config
import liquibase.exception.MigrationFailedException
import net.corda.cliutils.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.Emoji
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.schemas.MappedSchema
import net.corda.node.internal.DataSourceFactory.createDatasourceFromDriverJarFolders
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.nodeapi.internal.persistence.CheckpointsException
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.SchemaMigration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine.Mixin
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.sql.DataSource

// output type
const val CONSOLE = "CONSOLE"

// initialise loggers lazily as some configuration is changed on startup and if loggers are already initialised it will be ignored
val migrationLogger: Logger by lazy { LoggerFactory.getLogger("migration.tool") }
val errorLogger: Logger by lazy { LoggerFactory.getLogger("errors") }

fun main(args: Array<String>) {
    disableQuasarWarning()
    DbManagementTool().start(args)
}

private class DbManagementTool : CordaCliWrapper("database-manager", "The Corda database management tool.") {
    @Mixin
    var cmdLineOptions = LegacyDbManagerOptions()

    private fun checkOnlyOneCommandSelected() {
        val selectedOptions = mutableListOf<String>()
        if (cmdLineOptions.dryRun != null) selectedOptions.add(DRY_RUN)
        if (cmdLineOptions.executeMigration) selectedOptions.add(EXECUTE_MIGRATION)
        if (cmdLineOptions.createMigrationSqlForCordapp) selectedOptions.add(CREATE_MIGRATION_CORDAPP)
        if (cmdLineOptions.releaseLock) selectedOptions.add(RELEASE_LOCK)

        require(selectedOptions.count() != 0) { "You must call database-manager with a command option. See --help for further info." }
        require(selectedOptions.count() == 1) { "You cannot call more than one of: ${selectedOptions.joinToString(", ")}. See --help for further info." }
    }

    private val dryRunCli by lazy { DryRunCli() }
    private val executeMigrationsCli by lazy { ExecuteMigrationsCli() }
    private val createMigrationSqlForCordappsCli by lazy { CreateMigrationSqlForCordappsCli() }
    private val releaseLockCli by lazy { ReleaseLockCli() }

    override fun additionalSubCommands() = setOf(dryRunCli, executeMigrationsCli, createMigrationSqlForCordappsCli, releaseLockCli)

    override fun runProgram(): Int {
        // The database manager should be invoked using one of the vaild subcommands. If the old --flag based options are used,
        // this is the entry point that will be used. It does some additional validation and then delegates to the relevant
        // subcommand. It should be removed in a future version.
        return runLegacyCommands()
    }

    fun runLegacyCommands(): Int {
        checkOnlyOneCommandSelected()
        require(cmdLineOptions.baseDirectory != null) { "You must specify a base directory" }
        when {
            cmdLineOptions.releaseLock -> {
                printWarning("The --$RELEASE_LOCK flag has been deprecated and will be removed in a future version. Use the $RELEASE_LOCK command instead.")
                releaseLockCli.cmdLineOptions.copyFrom(cmdLineOptions)
                return releaseLockCli.runProgram()
            }
            cmdLineOptions.dryRun != null -> {
                printWarning("The --$DRY_RUN option has been deprecated and will be removed in a future version. Use the $DRY_RUN command instead.")
                dryRunCli.cmdLineOptions.copyFrom(cmdLineOptions)
                dryRunCli.outputFile = cmdLineOptions.dryRun!!
                return dryRunCli.runProgram()
            }
            cmdLineOptions.executeMigration -> {
                printWarning("The --$EXECUTE_MIGRATION option has been deprecated and will be removed in a future version. Use the $EXECUTE_MIGRATION command instead.")
                executeMigrationsCli.cmdLineOptions.copyFrom(cmdLineOptions)
                return executeMigrationsCli.runProgram()
            }
            cmdLineOptions.createMigrationSqlForCordapp -> {
                printWarning("The --$CREATE_MIGRATION_CORDAPP option has been deprecated and will be removed in a future version. Use the $CREATE_MIGRATION_CORDAPP command instead.")
                createMigrationSqlForCordappsCli.cmdLineOptions.copyFrom(cmdLineOptions)
                createMigrationSqlForCordappsCli.schemaClass = cmdLineOptions.createMigrationSqlForCordappPath!!
                return createMigrationSqlForCordappsCli.runProgram()
            }
        }
        migrationLogger.info("No command specified.")
        return ExitCodes.FAILURE
    }
}

data class Configuration(val dataSourceProperties: Properties, val database: DatabaseConfig, val jarDirs: List<String> = emptyList(), val myLegalName: CordaX500Name)

abstract class DbManagerConfiguration(private val cmdLineOptions: SharedDbManagerOptions) {
    protected abstract val defaultConfigFileName: String
    abstract val schemas: Set<MappedSchema>
    abstract val classLoader: ClassLoader
    abstract val cordappLoader: CordappLoader?
    abstract val parsedConfig: Config
    val config by lazy { parsedConfig.parseAs(Configuration::class, UnknownConfigKeysPolicy.IGNORE::handle) }
    val baseDirectory: Path by lazy {
        val dir = cmdLineOptions.baseDirectory?.toAbsolutePath()?.normalize()
                ?: throw error("You must specify a base-directory")
        if (!dir.exists()) throw error("Could not find base-directory: '${cmdLineOptions.baseDirectory}'.")
        dir
    }
    val configFile by lazy { baseDirectory / (cmdLineOptions.configFile ?: (defaultConfigFileName)) }
    fun runWithDataSource(withDatasource: (DataSource) -> Unit) {
        val driversFolder = (baseDirectory / "drivers").let { if (it.exists()) listOf(it) else emptyList() }
        val jarDirs = config.jarDirs.map { Paths.get(it) }
        for (jarDir in jarDirs) {
            if (!jarDir.exists()) {
                error("Could not find the configured JDBC driver directory: '$jarDir'.")
            }
        }

        return try {
            withDatasource(createDatasourceFromDriverJarFolders(config.dataSourceProperties, classLoader, driversFolder + jarDirs))
        } catch (e: Exception) {
            when (e) {
                is CheckpointsException, is MigrationFailedException -> error(e)
                else -> wrappedError("""Failed to create datasource.
                |Please check that the correct JDBC driver is installed in one of the following folders:
                |${(driversFolder + jarDirs).joinToString("\n\t - ", "\t - ")}
                |Caused By $e""".trimMargin(), e)
            }
        }
    }

    fun runMigrationCommand(schemas: Set<MappedSchema>, withMigration: (SchemaMigration, DataSource) -> Unit): Unit = this.runWithDataSource { dataSource ->
        withMigration(SchemaMigration(schemas, dataSource, config.database, cordappLoader, baseDirectory, ourName = config.myLegalName), dataSource)
    }
}

class ConfigurationException(message: String) : Exception(message)
class WrappedConfigurationException(message: String, val innerException: Exception) : Exception(message)

fun wrappedError(message: String, innerException: Exception) {
    errorLogger.error(message, innerException)
    throw WrappedConfigurationException(message, innerException)
}

fun error(exception: Exception) {
    errorLogger.error(exception.message, exception)
    throw exception
}

private fun error(message: String): Throwable {
    errorLogger.error(message)
    return ConfigurationException(message)
}

fun printInRed(message: String) {
    println("${ShellConstants.RED}$message${ShellConstants.RESET}")
}

fun printWarning(message: String) {
    Emoji.renderIfSupported {
        printInRed("${Emoji.warningSign} ATTENTION: $message")
    }
    errorLogger.warn(message)
}

/**
 * Quasar gets pulled in via Corda dependencies and prints warnings that the agent is not running.
 * Since the agent isn't needed for the purposes of this tool, we disable all Quasar warnings.
 */
private fun disableQuasarWarning() {
    System.setProperty("co.paralleluniverse.fibers.disableAgentWarning", "true")
}
