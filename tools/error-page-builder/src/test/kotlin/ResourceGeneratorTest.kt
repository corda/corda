import junit.framework.TestCase.assertEquals
import net.corda.common.logging.errorReporting.ErrorCodes
import net.corda.errorPageBuilder.ResourceGenerator
import org.junit.Test
import java.net.URLClassLoader

class ResourceGeneratorTest {

    private enum class TestNamespaces {
        TN1,
        TN2
    }

    private enum class TestCodes1 : ErrorCodes {
        CASE1,
        CASE2;

        override val namespace = TestNamespaces.TN1.toString()
    }

    private enum class TestCodes2 : ErrorCodes {
        CASE1,
        CASE3;

        override val namespace = TestNamespaces.TN2.toString()
    }

    private val urls = (TestCodes1::class.java.classLoader as URLClassLoader).urLs.toList()

    private fun expectedCodes() : List<String> {
        val codes1 = TestCodes1.values().map { "${it.namespace}-${it.name.replace("_", "-")}" }
        val codes2 = TestCodes2.values().map { "${it.namespace}-${it.name.replace("_", "-")}" }
        return codes1 + codes2
    }

    @Test(timeout = 300_000)
    fun `no codes marked as missing if all resources are present`() {
        val resourceGenerator = ResourceGenerator(listOf())
        val currentFiles = expectedCodes().map { "$it.properties" }
        val missing = resourceGenerator.calculateMissingResources(urls, currentFiles)
        assertEquals(listOf<String>(), missing)
    }
}