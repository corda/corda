package net.corda.gradle.jarfilter

import net.corda.gradle.unwanted.HasUnwantedVar
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.test.assertFailsWith

class StubVarPropertyTest {
    companion object {
        private const val GETTER_CLASS = "net.corda.gradle.HasUnwantedGetForStub"
        private const val SETTER_CLASS = "net.corda.gradle.HasUnwantedSetForStub"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "stub-var-property")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteGetter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVar>(GETTER_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.unwantedVar)
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVar>(GETTER_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertFailsWith<UnsupportedOperationException> { obj.unwantedVar }.also { ex ->
                        assertEquals("Method has been deleted", ex.message)
                    }
                }
            }
        }
    }

    @Test
    fun deleteSetter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVar>(SETTER_CLASS).apply {
                getConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertEquals(DEFAULT_MESSAGE, obj.unwantedVar)
                    obj.unwantedVar = MESSAGE
                    assertEquals(MESSAGE, obj.unwantedVar)
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVar>(SETTER_CLASS).apply {
                getConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertEquals(DEFAULT_MESSAGE, obj.unwantedVar)
                    obj.unwantedVar = MESSAGE
                    assertEquals(DEFAULT_MESSAGE, obj.unwantedVar)
                }
            }
        }
    }
}