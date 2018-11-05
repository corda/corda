package com.r3.corda.dbmigration

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
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

    private fun getMigrationOutput(baseDirectory: Path): Writer {
        return when (outputFile) {
            "" -> FileWriter(File(baseDirectory.toFile(), "migration${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.sql"))
            CONSOLE -> PrintWriter(System.out)
            else -> FileWriter(File(baseDirectory.toFile(), outputFile))
        }
    }

    override fun runProgram(): Int {
        val db = cmdLineOptions.toConfig()
        val writer = getMigrationOutput(db.baseDirectory)
        migrationLogger.info("Exporting the current database migrations ...")
        db.runMigrationCommand(db.schemas) { migration, _ ->
            migration.generateMigrationScript(writer)
        }
        return ExitCodes.SUCCESS
    }
}