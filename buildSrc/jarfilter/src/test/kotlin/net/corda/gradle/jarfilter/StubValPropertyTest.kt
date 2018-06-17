package net.corda.gradle.jarfilter

import net.corda.gradle.unwanted.HasUnwantedVal
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.test.assertFailsWith

class StubValPropertyTest {
    companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.HasValPropertyForStub"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "stub-val-property")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteGetter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVal>(PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.unwantedVal)
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVal>(PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertFailsWith<UnsupportedOperationException> { obj.unwantedVal }.also { ex ->
                        assertEquals("Method has been deleted", ex.message)
                    }
                }
            }
        }
    }
}
