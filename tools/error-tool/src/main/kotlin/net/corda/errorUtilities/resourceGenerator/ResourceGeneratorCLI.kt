package net.corda.errorUtilities.resourceGenerator

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.errorUtilities.ErrorResourceUtilities
import net.corda.errorUtilities.ErrorToolCLIUtilities
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.lang.IllegalArgumentException
import java.nio.file.Path
import java.util.*

/**
 * Subcommand for generating resource bundles from error codes.
 *
 * This subcommand takes a directory containing built class files that define the enumerations of codes, and a directory containing any
 * existing resource bundles. From this, it generates any missing resource files with the properties specified. The data under these
 * properties should then be filled in by hand.
 */
class ResourceGeneratorCLI : CordaCliWrapper(
        "generate-resources",
        "Generate any missing resource files for a set of error codes"
) {

    @CommandLine.Parameters(
            index = "0",
            paramLabel = "JAR_FILE",
            arity = "1",
            description = ["JAR file containing class files of the error code definitions"]
    )
    var jarFile: Path? = null

    @CommandLine.Parameters(
            index = "1",
            paramLabel = "RESOURCE_DIR",
            arity = "1",
            description = ["Directory containing resource bundles for the error codes"]
    )
    var resourceDir: Path? = null

    @CommandLine.Parameters(
            index="2..*",
            paramLabel = "ERROR_CODE_CLASSES",
            description = ["Fully qualified class names of the error code classes to generate resources for"]
    )
    var classes: List<String> = mutableListOf()

    @CommandLine.Option(
            names = ["--locales"],
            description = ["The set of locales to generate resource files for. Specified as locale tags, for example en-US"],
            arity = "1"
    )
    var locales: List<String> = listOf("en-US")

    companion object {
        private val logger = LoggerFactory.getLogger(ResourceGeneratorCLI::class.java)
    }

    override fun runProgram(): Int {
        val jarFileLocation = ErrorToolCLIUtilities.checkDirectory(jarFile, "error code definition class files")
        val resourceLocation = ErrorToolCLIUtilities.checkDirectory(resourceDir, "resource bundle files")
        val resourceGenerator = ResourceGenerator(locales.map { Locale.forLanguageTag(it) })
        try {
            val resources = ErrorResourceUtilities.listResourceFiles(resourceLocation)
            val loader = ErrorResourceUtilities.loaderFromDirectory(jarFileLocation)
            val missingResources = resourceGenerator.calculateMissingResources(classes, resources, loader)
            resourceGenerator.createResources(missingResources, resourceLocation)
            loader.close()
        } catch (e: IllegalArgumentException) {
            logger.error(e.message, e)
            return ExitCodes.FAILURE
        }
        return ExitCodes.SUCCESS
    }
}