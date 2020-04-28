package net.corda.errorUtilities.docsTable

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.errorUtilities.ErrorToolCLIUtilities
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Error tool sub-command for generating the documentation for error codes.
 *
 * The command needs a location to output the documentation to and a directory containing the resource files. From this, it generates a
 * Markdown table with all defined error codes.
 *
 * In the event that the file already exists, the tool will report an error and exit.
 */
class DocsTableCLI : CordaCliWrapper("build-docs", "Builds the error table for the error codes page") {

    @CommandLine.Parameters(
            index = "0",
            paramLabel = "OUTPUT_LOCATION",
            arity = "1",
            description = ["The file system location to output the error codes documentation table"]
    )
    var outputDir: Path? = null

    @CommandLine.Parameters(
            index = "1",
            paramLabel = "RESOURCE_LOCATION",
            arity = "1",
            description = ["The file system location of the resource files to process"]
    )
    var resourceLocation: Path? = null

    @CommandLine.Option(
            names = ["--locale-tag"],
            description = ["The locale tag of the locale to use when localising the error codes table. For example, en-US"],
            arity = "1"
    )
    var localeTag: String? = null

    companion object {
        private val logger = LoggerFactory.getLogger(DocsTableCLI::class.java)
        private const val ERROR_CODES_FILE = "error-codes.md"
    }

    override fun runProgram(): Int {
        val locale = if (localeTag != null) Locale.forLanguageTag(localeTag) else Locale.getDefault()
        val (outputFile, resources) = try {
            val output = ErrorToolCLIUtilities.checkDirectory(outputDir, "output file")
            val outputPath = output.resolve(ERROR_CODES_FILE)
            require(Files.notExists(outputPath)) {
                "Output file $outputPath exists, please remove it and run again."
            }
            Pair(outputPath, ErrorToolCLIUtilities.checkDirectory(resourceLocation, "resource bundle files"))
        } catch (e: IllegalArgumentException) {
            logger.error(e.message, e)
            return ExitCodes.FAILURE
        }
        val tableGenerator = DocsTableGenerator(resources, locale)
        try {
            val table = tableGenerator.generateMarkdown()
            outputFile.toFile().writeText(table)
        } catch (e: IllegalArgumentException) {
            logger.error(e.message, e)
            return ExitCodes.FAILURE
        }
        return ExitCodes.SUCCESS
    }
}