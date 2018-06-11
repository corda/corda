package net.corda.gradle.jarfilter

import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import java.lang.reflect.Modifier.*
import kotlin.test.assertFailsWith

class InterfaceFunctionTest {
    companion object {
        private const val FUNCTION_CLASS = "net.corda.gradle.InterfaceFunctions"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "interface-function")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteInterfaceFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("toDelete", Long::class.java).also { method ->
                    assertEquals(ABSTRACT, method.modifiers and ABSTRACT)
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod("toDelete", Long::class.java) }
            }
        }
    }

    @Test
    fun cannotStubInterfaceFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("toStubOut", Long::class.java).also { method ->
                    assertEquals(ABSTRACT, method.modifiers and ABSTRACT)
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("toStubOut", Long::class.java).also { method ->
                    assertEquals(ABSTRACT, method.modifiers and ABSTRACT)
                }
            }
        }
    }
}
