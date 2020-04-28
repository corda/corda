package net.corda.errorUtilities.resourceGenerator

import net.corda.common.logging.errorReporting.ErrorCodes
import net.corda.common.logging.errorReporting.ResourceBundleProperties
import net.corda.errorUtilities.ClassDoesNotExistException
import java.nio.file.Path
import java.util.*

/**
 * Generate a set of resource files from an enumeration of error codes.
 */
class ResourceGenerator(private val locales: List<Locale>) {

    companion object {
        internal const val MESSAGE_TEMPLATE_DEFAULT = "<Message template>"
        internal const val SHORT_DESCRIPTION_DEFAULT = "<Short description>"
        internal const val ACTIONS_TO_FIX_DEFAULT = "<Actions to fix>"
        internal const val ALIASES_DEFAULT = ""
    }

    private fun createResourceFile(name: String, location: Path) {
        val file = location.resolve(name)
        val text = """
            |${ResourceBundleProperties.MESSAGE_TEMPLATE} = $MESSAGE_TEMPLATE_DEFAULT
            |${ResourceBundleProperties.SHORT_DESCRIPTION} = $SHORT_DESCRIPTION_DEFAULT
            |${ResourceBundleProperties.ACTIONS_TO_FIX} = $ACTIONS_TO_FIX_DEFAULT
            |${ResourceBundleProperties.ALIASES} = $ALIASES_DEFAULT
        """.trimMargin()
        file.toFile().writeText(text)
    }

    /**
     * Create a set of resource files in the given location.
     *
     * @param resources The resource file names to create
     * @param resourceLocation The location to create the resource files
     */
    fun createResources(resources: List<String>, resourceLocation: Path) {
        for (resource in resources) {
            createResourceFile(resource, resourceLocation)
        }
    }

    private fun definedCodes(classes: List<String>, loader: ClassLoader) : List<String> {
        return classes.flatMap {
            val clazz = try {
                loader.loadClass(it)
            } catch (e: ClassNotFoundException) {
                throw ClassDoesNotExistException(it)
            }
            if (ErrorCodes::class.java.isAssignableFrom(clazz) && clazz != ErrorCodes::class.java) {
                val namespace = (clazz.enumConstants.first() as ErrorCodes).namespace.toLowerCase()
                clazz.enumConstants.map { code -> "${namespace}-${code.toString().toLowerCase().replace("_", "-")}"}
            } else {
                listOf()
            }
        }
    }

    private fun getExpectedResources(codes: List<String>) : List<String> {
        return codes.flatMap {
            val localeResources = locales.map { locale -> "${it}_${locale.toLanguageTag().replace("-", "_")}.properties"}
            localeResources + "$it.properties"
        }
    }

    /**
     * Calculate what resource files are missing from a set of resource files, given a set of error codes.
     *
     * @param classes The classes to generate resource files for
     * @param resourceFiles The list of resource files
     */
    fun calculateMissingResources(classes: List<String>, resourceFiles: List<String>, loader: ClassLoader) : List<String> {
        val codes = definedCodes(classes, loader)
        val expected = getExpectedResources(codes)
        val missing = expected - resourceFiles.toSet()
        return missing.toList()
    }
}