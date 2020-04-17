package net.corda.errorPageBuilder

import net.corda.common.logging.errorReporting.ErrorCodes
import net.corda.common.logging.errorReporting.ResourceBundleProperties
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.*

/**
 * Generate a set of resource files from an enumeration of error codes.
 */
class ResourceGenerator(private val resourceLocation: Path,
                        private val locales: List<Locale>) {

    private fun createResourceFile(name: String) {
        val file = resourceLocation.resolve(name)
        val text = """
            |${ResourceBundleProperties.MESSAGE_TEMPLATE} = <Message template>
            |${ResourceBundleProperties.SHORT_DESCRIPTION} = <Short description>
            |${ResourceBundleProperties.ACTIONS_TO_FIX} = <Actions to fix>
            |${ResourceBundleProperties.ALIASES} = 
        """.trimMargin()
        file.toFile().writeText(text)
    }

    fun createResources(resources: List<String>) {
        for (resource in resources) {
            // Make the default resource file
            createResourceFile("$resource.properties")
            for (locale in locales) {
                // Create a resource file for each locale
                val suffix = locale.toLanguageTag().replace("-", "_")
                createResourceFile("${resource}_$suffix.properties")
            }
        }
    }

    fun readDefinedCodes(classFilesLocation: File) : List<String> {
        val urls = classFilesLocation.walkTopDown().map { it.toURI().toURL() }.asIterable().toList().toTypedArray()
        val loader = URLClassLoader(urls)
        val reflections = Reflections(ConfigurationBuilder()
                .addClassLoader(loader)
                .setScanners(SubTypesScanner())
                .addUrls(urls.toList())
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
}