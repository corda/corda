package net.corda.errorPageBuilder

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

fun main(args: Array<String>) {
    val builder = ErrorPageBuilder()
    builder.start(args)
}

class ErrorPageBuilder : CordaCliWrapper("error-page-builder", "Builds the error table for the error codes page") {

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
        private val logger = LoggerFactory.getLogger(ErrorPageBuilder::class.java)
        private const val ERROR_CODES_FILE = "error-codes.md"
    }

    private fun getOutputFile() : File {
        return outputDir?.let {
            require(Files.exists(it)) {
                "Directory $it does not exist. Please specify a valid directory to write output to."
            }
//            val outputPath = it.resolve(ERROR_CODES_FILE)
//            require(Files.notExists(outputPath)) {
//                "Output file $outputPath exists, please remove it and run again."
//            }
            val outputPath = it
            outputPath.toFile()
        } ?: throw IllegalArgumentException("Directory not specified. Please specify a valid directory to write output to.")
    }

    private fun getResourceDir() : Path {
        return resourceLocation?.also {
            require(Files.exists(it)) {
                "Resource location $it does not exist. Please specify a valid location for error code resources"
            }
        } ?: throw IllegalArgumentException("Resource location not specified. Please specify a resource location.")
    }

    override fun runProgram(): Int {
        val locale = if (localeTag != null) Locale.forLanguageTag(localeTag) else Locale.getDefault()
        val (outputFile, resources) = try {
            Pair(getOutputFile(), getResourceDir())
        } catch (e: IllegalArgumentException) {
            logger.error(e.message, e)
            return ExitCodes.FAILURE
        }
//        val tableGenerator = ErrorTableGenerator(resources.toFile(), locale)
        val resourceGenerator = ResourceGenerator(resources.toFile(), listOf(Locale.forLanguageTag("en-US")))
        val utils = ErrorResourceUtilities(resources.toFile())
        try {
            val resourceFiles = utils.listResources().asSequence().toSet()
            val enumResources = resourceGenerator.readDefinedCodes(outputFile).toSet()
            val missingResources = enumResources - resourceFiles
            resourceGenerator.createResources(missingResources.toList())

//            val table = tableGenerator.generateMarkdown()
//            outputFile.writeText(table)
        } catch (e: IllegalArgumentException) {
            logger.error(e.message, e)
            return ExitCodes.FAILURE
        }
        return ExitCodes.SUCCESS
    }
}