package com.r3.corda.dbmigration

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.core.internal.copyTo
import net.corda.core.internal.div
import net.corda.node.services.persistence.MigrationExporter
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.MigrationHelpers
import picocli.CommandLine.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.*
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class CreateMigrationSqlForCordappOptions : SharedDbManagerOptions {
    override var mode: Mode = Mode.NODE // This can only be run on a node, don't expose as configurable via picocli
    override var doormanJarPath: Path? = null

    @Option(
            names = ["-b", "--base-directory"],
            description = ["The output directory, where a `migration` folder is created."],
            required = true
    )
    override var baseDirectory: Path? = null

    @Option(
            names = ["-f", "--config-file"],
            description = ["The name of the config file. By default 'node.conf' for a simple node and 'network-management.conf' for a doorman."]
    )
    override var configFile: String? = null

    @Option(
            names = ["--$JAR_OUTPUT"],
            description = ["Place generated migration scripts into a jar."]
    )
    var jarOutput: Boolean = false
}

class CreateMigrationSqlForCordappsCli : CliWrapperBase(CREATE_MIGRATION_CORDAPP, "Create migration files for a CorDapp.") {
    @Mixin
    var cmdLineOptions = CreateMigrationSqlForCordappOptions()

    @Parameters(arity = "0..1", description = ["You can specify the fully qualified name of the `MappedSchema` class. If not specified it will generate the migration for all schemas that don't have migrations."])
    var schemaClass: String = ""

    private val db by lazy { cmdLineOptions.toConfig() }

    override fun runProgram(): Int {
        val outputSchemaMigrations = mutableListOf<Pair<Class<*>, Path>>()

        if (schemaClass != "") {
            generateMigrationFileForSchema(schemaClass)?.let { outputSchemaMigrations.add(it) }
        } else {
            val cordappsSchemas = db.schemas.minus(NodeSchemaService().internalSchemas())
            val (withoutMigration, withMigration) = cordappsSchemas.partition {
                MigrationHelpers.getMigrationResource(it, db.classLoader) == null
            }
            withMigration.forEach {
                migrationLogger.info("Schema: ${it.javaClass.name} already contains the database migration script, the file creation skipped.")
            }
            outputSchemaMigrations.addAll(withoutMigration.mapNotNull { generateMigrationFileForSchema(it.javaClass.name) }.toList())
        }

        if (cmdLineOptions.jarOutput) {
            val groupedBySourceJar = outputSchemaMigrations.asSequence().map { (klazz, path) ->
                //get the source jar for this particular schema
                klazz.protectionDomain.codeSource to path
            }.filter {
                //filter all entries without a code source
                it.first != null
            }.map {
                //convert codesource to a File
                File(it.first.location.toURI()) to it.second
            }.groupBy {
                //group by codesource File
                it.first
            }.map {
                // convert File into a filename for the codesource
                // if run from within an IDE, there is possibility of some schemas to be
                // loaded from a build folder
                // so we must handle this case
                val fileName = if (it.key.name.endsWith("jar")) {
                    it.key.path
                } else {
                    "unknown-cordapp.jar"
                }
                fileName to it.value.map { it.second }
            }.toList().toMap()

            groupedBySourceJar.entries.forEach { (_, migrationFiles) ->
                migrationFiles.forEach {
                    //use System.out directly instead of logger as we MUST output to screen due to user input requirement
                    System.out.println("##### ${it.fileName} #####")
                    it.copyTo(System.out)
                    System.out.println("Is this output as expected? [y/N]")
                    validateUserResponse()
                }
            }

            System.out.println("""There is potential for data corruption.
                Please check the scripts are valid before running.
                If there are issues, rerun the command without the --jar parameter,
                edit the resulting file and manually create the jar.
                Are you sure that the migration scripts are acceptable?  [y/N]""")
            if (!validateUserResponse()) return ExitCodes.FAILURE

            groupedBySourceJar.entries.forEach { (jar, migrationFiles) ->
                val sourceJarFile = File(jar)
                val migrationJarFile = File(sourceJarFile.parent, "migration-${sourceJarFile.name}")
                JarOutputStream(FileOutputStream(migrationJarFile)).use { jos ->
                    migrationLogger.info("Creating migration CorDapp at: ${migrationJarFile.absolutePath}")
                    migrationFiles.map { ZipEntry("migration/${it.fileName}") }.forEach {
                        jos.putNextEntry(it)
                    }
                }
            }
        }
        return ExitCodes.SUCCESS
    }

    private fun generateMigrationFileForSchema(schemaClass: String): Pair<Class<*>, Path>? {
        migrationLogger.info("Creating database migration files for schema: $schemaClass into ${(db.baseDirectory / "migration").toString().trim()}")
        var liquiBaseOutput: Pair<Class<*>, Path>? = null
        try {
            db.runWithDataSource {
                liquiBaseOutput = MigrationExporter(db.baseDirectory, db.config.dataSourceProperties, db.classLoader, it).generateMigrationForCorDapp(schemaClass)
            }
        } catch (e: Exception) {
            throw wrappedError("Could not generate migration for $schemaClass: ${e.message}", e)
        }
        return liquiBaseOutput
    }

    private fun validateUserResponse(): Boolean {
        val userInput = Scanner(System.`in`).nextLine().toLowerCase()
        if (userInput != "y" && userInput != "yes") {
            migrationLogger.warn("Quitting due to user input")
            return false
        }
        return true
    }
}