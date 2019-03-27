package com.r3.corda.dbmigration

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import picocli.CommandLine
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.Writer
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*

class DryRunCli : CliWrapperBase(DRY_RUN, description = "Output the database migration to the specified output file. The output directory is the base-directory.") {
    @CommandLine.Mixin
    var cmdLineOptions = DbManagerOptions()

    @CommandLine.Parameters(arity = "0..1", description = ["You can specify a file name or 'CONSOLE' if you want to send the output to the console."])
    var outputFile: String = ""

    override fun runProgram(): Int {
        val db = cmdLineOptions.toConfig()
        val writer = getMigrationOutput(db.baseDirectory)
        migrationLogger.info("Exporting the current database migrations ...")
        db.runMigrationCommand(db.schemas) { migration, _ ->
            migration.generateMigrationScript(writer)
        }
        migrationLogger.info("Successfully exported to $outputFile")
        return ExitCodes.SUCCESS
    }

    private fun getMigrationOutput(baseDirectory: Path): Writer {
        return when (outputFile) {
            "" -> FileWriter(File(baseDirectory.toFile(), "migration${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.sql"))
            CONSOLE -> {
                disableConsoleLogging()
                PrintWriter(System.out)
            }
            else -> FileWriter(File(baseDirectory.toFile(), outputFile))
        }
    }

    /** When migration output is set to CONSOLE we want to avoid any console log messages that aren't errors. */
    private fun disableConsoleLogging() {
        System.setProperty("consoleLogLevel", "ERROR")
        val loggerContext = LogManager.getContext(false) as LoggerContext
        loggerContext.apply {
            reconfigure()
            updateLoggers()
        }
    }
}