/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("DBMigration")

package com.r3.corda.dbmigration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import joptsimple.OptionException
import joptsimple.OptionParser
import joptsimple.OptionSet
import joptsimple.util.EnumConverter
import net.corda.nodeapi.internal.MigrationHelpers
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.schemas.MappedSchema
import net.corda.node.internal.DataSourceFactory.createDatasourceFromDriverJarFolders
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.configOf
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.persistence.MigrationExporter
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.persistence.CheckpointsException
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.SchemaMigration
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.Writer
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import javax.sql.DataSource

//command line arguments
const val HELP = "help"
const val MODE = "mode"
const val BASE_DIRECTORY = "base-directory"
const val CONFIG = "config-file"
const val DOORMAN_JAR_PATH = "doorman-jar-path"
const val RUN_MIGRATION = "execute-migration"
const val DRY_RUN = "dry-run"
const val CREATE_MIGRATION_CORDAPP = "create-migration-sql-for-cordapp"
const val RELEASE_LOCK = "release-lock"

// output type
const val CONSOLE = "CONSOLE"

private val migrationLogger = LoggerFactory.getLogger("migration.tool")
private val errorLogger = LoggerFactory.getLogger("errors")

private enum class Mode {
    NODE, DOORMAN
}

private fun initOptionParser(): OptionParser = OptionParser().apply {
    accepts(MODE, "Either 'NODE' or 'DOORMAN'. By default 'NODE'")
            .withOptionalArg()
            .withValuesConvertedBy(object : EnumConverter<Mode>(Mode::class.java) {})
            .defaultsTo(Mode.NODE)

    accepts(BASE_DIRECTORY, "The node or doorman directory")
            .withRequiredArg().required()

    accepts(CONFIG, "The name of the config file. By default 'node.conf' for a simple node and 'network-management.conf' for a doorman.")
            .withOptionalArg()

    accepts(DOORMAN_JAR_PATH, "The path to the doorman JAR")
            .withOptionalArg()

    val runMig = accepts(RUN_MIGRATION,
            "This option will run the db migration on the configured database. This is the only command that will actually write to the database.")

    val dryRun = accepts(DRY_RUN, """Output the database migration to the specified output file.
        |The output directory is the base-directory.
        |You can specify a file name or 'CONSOLE' if you want to send the output to the console.""".trimMargin())

    dryRun.withOptionalArg()
    dryRun.availableUnless(runMig)

    accepts(CREATE_MIGRATION_CORDAPP, """Create migration files for a CorDapp.
        |You can specify the fully qualified name of the `MappedSchema` class. If not specified it will generate the migration for all schemas that don't have migrations.
        |The output directory is the base-directory, where a `migration` folder is created.""".trimMargin())
            .withOptionalArg()

    accepts(RELEASE_LOCK, "Releases whatever locks are on the database change log table, in case shutdown failed.")

    accepts(HELP).forHelp()
}

fun main(args: Array<String>) {
    val parser = initOptionParser()
    try {
        val options = parser.parse(*args)
        runCommand(options, parser)
    } catch (e: OptionException) {
        errorAndExit(e.message)
    }
}

data class Configuration(val dataSourceProperties: Properties, val database: DatabaseConfig, val jarDirs: List<String> = emptyList())

private fun runCommand(options: OptionSet, parser: OptionParser) {

    fun baseDirectory() = Paths.get(options.valueOf(BASE_DIRECTORY) as String).toAbsolutePath().normalize()
    val mode = options.valueOf(MODE) as Mode
    fun configFile(defaultCfgName: String) = baseDirectory() / ((options.valueOf(CONFIG) as String?) ?: defaultCfgName)

    when {
        options.has(HELP) -> parser.printHelpOn(System.out)
        mode == Mode.NODE -> {
            val baseDirectory = baseDirectory()
            if (!baseDirectory.exists()) {
                errorAndExit("Could not find base-directory: '$baseDirectory'.")
            }
            val config = configFile("node.conf")
            if (!config.exists()) {
                errorAndExit("Not a valid node folder. Could not find the config file: '$config'.")
            }
            val nodeConfig = ConfigHelper.loadConfig(baseDirectory, config).parseAsNodeConfiguration()
            val cordappLoader = JarScanningCordappLoader.fromDirectories(setOf(baseDirectory, baseDirectory / "cordapps"))

            val schemaService = NodeSchemaService(extraSchemas = cordappLoader.cordappSchemas, includeNotarySchemas = nodeConfig.notary != null)

            handleCommand(options, baseDirectory, config, mode, cordappLoader.appClassLoader, schemaService.schemaOptions.keys)
        }
        mode == Mode.DOORMAN -> {
            if (!options.has(DOORMAN_JAR_PATH)) {
                errorAndExit("The $DOORMAN_JAR_PATH argument is required when running in doorman mode.")
            }
            val fatJarPath = Paths.get(options.valueOf(DOORMAN_JAR_PATH) as String)
            if (!fatJarPath.exists()) {
                errorAndExit("Could not find the doorman jar in location: '$fatJarPath'.")
            }
            val doormanClassloader = classLoaderFromJar(fatJarPath)
            val doormanSchema = "com.r3.corda.networkmanage.common.persistence.NetworkManagementSchemaServices\$SchemaV1"
            val schema = loadMappedSchema(doormanSchema, doormanClassloader)
            handleCommand(options, baseDirectory(), configFile("network-management.conf"), mode, doormanClassloader, setOf(schema))
        }
    }
    migrationLogger.info("Done")
}

private fun handleCommand(options: OptionSet, baseDirectory: Path, configFile: Path, mode: Mode, classLoader: ClassLoader, schemas: Set<MappedSchema>) {
    val parsedConfig = ConfigFactory.parseFile(configFile.toFile()).resolve().let {
        if (mode == Mode.NODE) {
            it.withFallback(configOf("baseDirectory" to baseDirectory.toString()))
                    .withFallback(ConfigFactory.parseResources("reference.conf", ConfigParseOptions.defaults().setAllowMissing(true)))
                    .resolve()
        } else {
            it
        }
    }
    val config = parsedConfig.parseAs(Configuration::class, UnknownConfigKeysPolicy.IGNORE::handle)

    fun runMigrationCommand(withMigration: (SchemaMigration, DataSource) -> Unit): Unit = runWithDataSource(config, baseDirectory, classLoader) { dataSource ->
        withMigration(SchemaMigration(schemas, dataSource, true, config.database, classLoader), dataSource)
    }

    when {
        options.has(RELEASE_LOCK) -> runWithDataSource(ConfigFactory.parseFile(configFile.toFile()).resolve().parseAs(Configuration::class), baseDirectory, classLoader) {
            SchemaMigration(emptySet(), it, true, config.database, Thread.currentThread().contextClassLoader).forceReleaseMigrationLock()
        }
        options.has(DRY_RUN) -> {
            val writer = getMigrationOutput(baseDirectory, options)
            migrationLogger.info("Exporting the current db migrations ...")
            runMigrationCommand { migration, _ ->
                migration.generateMigrationScript(writer)
            }
        }
        options.has(RUN_MIGRATION) -> {
            migrationLogger.info("Running the database migration on  $baseDirectory")
            runMigrationCommand { migration, dataSource -> migration.runMigration(dataSource.connection.use { DBCheckpointStorage().getCheckpointCount(it) != 0L }) }
        }
        options.has(CREATE_MIGRATION_CORDAPP) && (mode == Mode.NODE) -> {

            fun generateMigrationFileForSchema(schemaClass: String) {
                migrationLogger.info("Creating database migration files for schema: $schemaClass into ${baseDirectory / "migration"}")
                try {
                    runWithDataSource(config, baseDirectory, classLoader) {
                        MigrationExporter(baseDirectory, config.dataSourceProperties, classLoader, it).generateMigrationForCorDapp(schemaClass)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorAndExit("Could not generate migration for $schemaClass: ${e.message}")
                }
            }

            if (options.hasArgument(CREATE_MIGRATION_CORDAPP)) {
                val schemaClass = options.valueOf(CREATE_MIGRATION_CORDAPP) as String
                generateMigrationFileForSchema(schemaClass)
            } else {
                schemas.filter { MigrationHelpers.getMigrationResource(it, classLoader) == null }.forEach {
                    generateMigrationFileForSchema(it.javaClass.name)
                }
            }
        }
        else -> errorAndExit("Please specify a correct command")
    }
}

private fun classLoaderFromJar(jarPath: Path): ClassLoader = URLClassLoader(listOf(jarPath.toUri().toURL()).toTypedArray())

private fun loadMappedSchema(schemaName: String, classLoader: ClassLoader) = classLoader.loadClass(schemaName).kotlin.objectInstance as MappedSchema

private fun getMigrationOutput(baseDirectory: Path, options: OptionSet): Writer {
    val option = options.valueOf(DRY_RUN) as String?
    return when (option) {
        null -> FileWriter(File(baseDirectory.toFile(), "migration${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.sql"))
        CONSOLE -> PrintWriter(System.out)
        else -> FileWriter(File(baseDirectory.toFile(), option))
    }
}

private fun runWithDataSource(config: Configuration, baseDirectory: Path, classLoader: ClassLoader, withDatasource: (DataSource) -> Unit) {
    val driversFolder = (baseDirectory / "drivers").let { if (it.exists()) listOf(it) else emptyList() }
    val jarDirs = config.jarDirs.map { Paths.get(it) }
    for (jarDir in jarDirs) {
        if (!jarDir.exists()) {
            errorAndExit("Could not find the configured JDBC driver directory: '$jarDir'.")
        }
    }

    return try {
        withDatasource(createDatasourceFromDriverJarFolders(config.dataSourceProperties, classLoader, driversFolder + jarDirs))
    } catch (e: CheckpointsException) {
        errorAndExit(e.message)
    } catch (e: Exception) {
        errorAndExit("""Failed to create datasource.
            |Please check that the correct JDBC driver is installed in one of the following folders:
            |${(driversFolder + jarDirs).joinToString("\n\t - ", "\t - ")}
            |Caused By $e""".trimMargin(), e)
    }
}

private fun errorAndExit(message: String?, exception: Exception? = null) {
    errorLogger.error(message, exception)
    System.err.println(message)
    System.exit(1)
}
