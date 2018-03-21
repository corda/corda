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
import com.zaxxer.hikari.util.PropertyElf
import joptsimple.OptionException
import joptsimple.OptionParser
import joptsimple.OptionSet
import joptsimple.util.EnumConverter
import net.corda.core.internal.MigrationHelpers
import net.corda.core.internal.copyTo
import net.corda.core.internal.div
import net.corda.core.schemas.MappedSchema
import net.corda.node.internal.DataSourceFactory.createDatasourceFromDriverJars
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.node.services.persistence.MigrationExporter
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.SchemaMigration
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.Writer
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.jar.JarFile
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

private val logger = LoggerFactory.getLogger("migration.tool")

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

    accepts(DOORMAN_JAR_PATH, "The path to the doorman fat jar")
            .withOptionalArg()

    accepts(RUN_MIGRATION,
            "This option will run the db migration on the configured database")

    accepts(DRY_RUN, """Output the database migration to the specified output file.
        |The output directory is the base-directory.
        |You can specify a file name or 'CONSOLE' if you want to send the output to the console.""".trimMargin())
            .withOptionalArg()

    accepts(CREATE_MIGRATION_CORDAPP, """Create migration files for a CorDapp.
        |You can specify the fully qualified of the `MappedSchema` class. If not specified it will generate foll all schemas that don't have migrations.
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

data class Configuration(val dataSourceProperties: Properties, val database: DatabaseConfig)

private fun runCommand(options: OptionSet, parser: OptionParser) {

    fun baseDirectory() = Paths.get(options.valueOf(BASE_DIRECTORY) as String).normalize()
    val mode = options.valueOf(MODE) as Mode
    fun configFile(defaultCfgName: String) = baseDirectory() / ((options.valueOf(CONFIG) as String?) ?: defaultCfgName)

    when {
        options.has(HELP) -> parser.printHelpOn(System.out)
        mode == Mode.NODE -> {
            val baseDirectory = baseDirectory()
            val config = configFile("node.conf")
            val nodeConfig = ConfigHelper.loadConfig(baseDirectory, config).parseAsNodeConfiguration()
            val cordappLoader = CordappLoader.createDefault(baseDirectory)

            val schemaService = NodeSchemaService(extraSchemas = cordappLoader.cordappSchemas, includeNotarySchemas = nodeConfig.notary != null)

            handleCommand(options, baseDirectory, config, mode, cordappLoader.appClassLoader, schemaService.schemaOptions.keys)
        }
        mode == Mode.DOORMAN -> {
            val fatJarPath = Paths.get(options.valueOf(DOORMAN_JAR_PATH) as String)
            val doormanClassloader = classLoaderFromCapsuleFatJar(fatJarPath)
            val doormanSchema = "com.r3.corda.networkmanage.common.persistence.NetworkManagementSchemaServices\$SchemaV1"
            val schema = loadMappedSchema(doormanSchema, doormanClassloader)
            handleCommand(options, baseDirectory(), configFile("network-management.conf"), mode, doormanClassloader, setOf(schema))
        }
    }
    logger.info("Done")
}

private fun handleCommand(options: OptionSet, baseDirectory: Path, configFile: Path, mode: Mode, classLoader: ClassLoader, schemas: Set<MappedSchema>) {
    val config = ConfigFactory.parseFile(configFile.toFile()).resolve().parseAs(Configuration::class, false)

    fun runMigrationCommand(withMigration: (SchemaMigration) -> Unit): Unit = runWithDataSource(config, baseDirectory, classLoader) { dataSource ->
        withMigration(SchemaMigration(schemas, dataSource, true, config.database, classLoader))
    }

    when {
        options.has(RELEASE_LOCK) -> runWithDataSource(ConfigFactory.parseFile(configFile.toFile()).resolve().parseAs(Configuration::class), baseDirectory, classLoader) {
            SchemaMigration(emptySet(), it, true, config.database, Thread.currentThread().contextClassLoader).forceReleaseMigrationLock()
        }
        options.has(DRY_RUN) -> {
            val writer = getMigrationOutput(baseDirectory, options)
            logger.info("Exporting the current db migrations ...")
            runMigrationCommand {
                it.generateMigrationScript(writer)
            }
        }
        options.has(RUN_MIGRATION) -> {
            logger.info("Running the database migration on  $baseDirectory")
            runMigrationCommand { it.runMigration() }
        }
        options.has(CREATE_MIGRATION_CORDAPP) && (mode == Mode.NODE) -> {

            fun generateMigrationFileForSchema(schemaClass: String) {
                logger.info("Creating database migration files for schema: $schemaClass into ${baseDirectory / "migration"}")
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

//only used for capsule
private fun classLoaderFromCapsuleFatJar(fatJarPath: Path): ClassLoader {
    val dir = createTempDir()
    dir.deleteOnExit()
    val jarFile = JarFile(fatJarPath.toFile())
    val jars = jarFile.entries().toList().filter { !it.isDirectory && it.name.endsWith("jar", ignoreCase = true) }.map { entry ->
        val dest = File(dir, entry.name).toPath()
        jarFile.getInputStream(entry).copyTo(dest)
        dest
    }
    return URLClassLoader(jars.map { it.toUri().toURL() }.toTypedArray())
}

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
    val driversFolder = baseDirectory / "drivers"
    return withDatasource(createDatasourceFromDriverJars(config.dataSourceProperties, classLoader, driversFolder))
}

private fun errorAndExit(message: String?) {
    System.err.println(message)
    System.exit(1)
}
