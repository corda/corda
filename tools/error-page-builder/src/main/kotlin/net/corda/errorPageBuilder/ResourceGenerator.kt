package net.corda.errorPageBuilder

import net.corda.common.logging.errorReporting.ErrorCodes
import net.corda.common.logging.errorReporting.ResourceBundleProperties
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import java.net.URL
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
     */
    fun createResources(resources: List<String>, resourceLocation: Path) {
        for (resource in resources) {
            createResourceFile(resource, resourceLocation)
        }
    }

    private fun readDefinedCodes(urls: List<URL>) : List<String> {
        val reflections = Reflections(ConfigurationBuilder()
                .setScanners(SubTypesScanner(false))
                .addUrls(urls)
        )
        return reflections.getSubTypesOf(ErrorCodes::class.java).flatMap {
            if (it.isEnum) {
                val values = (it as Class<Enum<*>>).enumConstants
                values.map { constant ->
                    val namespace = (constant as ErrorCodes).namespace.toLowerCase()
                    "$namespace-${constant.name.toLowerCase().replace("_", "-")}"
                }
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
     * @param urls A list of URLs for each of the error code class files
     * @param resourceFiles The list of resource files
     */
    fun calculateMissingResources(urls: List<URL>, resourceFiles: List<String>) : List<String> {
        val codes = readDefinedCodes(urls)
        val expected = getExpectedResources(codes)
        val missing = expected - resourceFiles.toSet()
        return missing.toList()
    }
}