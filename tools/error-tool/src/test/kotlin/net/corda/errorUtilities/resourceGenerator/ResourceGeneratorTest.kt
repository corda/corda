package net.corda.errorUtilities.resourceGenerator

import junit.framework.TestCase.assertEquals
import net.corda.common.logging.errorReporting.ResourceBundleProperties
import org.junit.Test
import java.util.*

class ResourceGeneratorTest {

    private val classes = listOf(TestCodes1::class.qualifiedName!!, TestCodes2::class.qualifiedName!!)

    private fun expectedCodes() : List<String> {
        val codes1 = TestCodes1.values().map { "${it.namespace.toLowerCase()}-${it.name.replace("_", "-").toLowerCase()}" }
        val codes2 = TestCodes2.values().map { "${it.namespace.toLowerCase()}-${it.name.replace("_", "-").toLowerCase()}" }
        return codes1 + codes2
    }

    @Test(timeout = 1000)
    fun `no codes marked as missing if all resources are present`() {
        val resourceGenerator = ResourceGenerator(listOf())
        val currentFiles = expectedCodes().map { "$it.properties" }
        val missing = resourceGenerator.calculateMissingResources(classes, currentFiles, TestCodes1::class.java.classLoader)
        assertEquals(setOf<String>(), missing.toSet())
    }

    @Test(timeout = 1000)
    fun `missing locales are marked as missing when other locales are present`() {
        val resourceGenerator = ResourceGenerator(listOf("en-US", "ga-IE").map { Locale.forLanguageTag(it) })
        val currentFiles = expectedCodes().flatMap { listOf("$it.properties", "${it}_en_US.properties") }
        val missing = resourceGenerator.calculateMissingResources(classes, currentFiles, TestCodes1::class.java.classLoader)
        assertEquals(expectedCodes().map { "${it}_ga_IE.properties" }.toSet(), missing.toSet())
    }

    @Test(timeout = 1000)
    fun `test writing out files works correctly`() {
        // First test that if all files are missing then the resource generator detects this
        val resourceGenerator = ResourceGenerator(listOf())
        val currentFiles = listOf<String>()
        val missing = resourceGenerator.calculateMissingResources(classes, currentFiles, TestCodes1::class.java.classLoader)
        assertEquals(expectedCodes().map { "$it.properties" }.toSet(), missing.toSet())

        // Now check that all resource files that should be created are
        val tempDir = createTempDir()
        resourceGenerator.createResources(missing, tempDir.toPath())
        val createdFiles = tempDir.walkTopDown().filter { it.isFile && it.extension == "properties" }.map { it.name }.toSet()
        assertEquals(missing.toSet(), createdFiles)

        // Now check that a created file has the expected properties and values
        val properties = Properties()
        properties.load(tempDir.walk().filter { it.isFile && it.extension == "properties"}.first().inputStream())
        assertEquals(ResourceGenerator.SHORT_DESCRIPTION_DEFAULT, properties.getProperty(ResourceBundleProperties.SHORT_DESCRIPTION))
        assertEquals(ResourceGenerator.ACTIONS_TO_FIX_DEFAULT, properties.getProperty(ResourceBundleProperties.ACTIONS_TO_FIX))
        assertEquals(ResourceGenerator.MESSAGE_TEMPLATE_DEFAULT, properties.getProperty(ResourceBundleProperties.MESSAGE_TEMPLATE))
        assertEquals(ResourceGenerator.ALIASES_DEFAULT, properties.getProperty(ResourceBundleProperties.ALIASES))
    }
}