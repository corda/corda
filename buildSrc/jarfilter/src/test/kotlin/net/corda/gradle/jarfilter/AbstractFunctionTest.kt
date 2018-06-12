package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.*
import org.hamcrest.core.IsCollectionContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import java.lang.reflect.Modifier.*
import kotlin.reflect.full.declaredFunctions
import kotlin.test.assertFailsWith

class AbstractFunctionTest {
    companion object {
        private const val FUNCTION_CLASS = "net.corda.gradle.AbstractFunctions"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "abstract-function")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteAbstractFunction() {
        val longFunction = isFunction("toDelete", Long::class, Long::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("toDelete", Long::class.java).also { method ->
                    assertEquals(ABSTRACT, method.modifiers and ABSTRACT)
                }
                assertThat("toDelete(J) not found", kotlin.declaredFunctions, hasItem(longFunction))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod("toDelete", Long::class.java) }
                assertThat("toDelete(J) still exists", kotlin.declaredFunctions, not(hasItem(longFunction)))
            }
        }
    }

    @Test
    fun cannotStubAbstractFunction() {
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
