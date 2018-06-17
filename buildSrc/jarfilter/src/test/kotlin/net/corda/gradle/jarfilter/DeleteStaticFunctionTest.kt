package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.test.assertFailsWith

class DeleteStaticFunctionTest {
    companion object {
        private const val FUNCTION_CLASS = "net.corda.gradle.StaticFunctionsToDelete"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-static-function")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteStringFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("unwantedStringToDelete", String::class.java).also { method ->
                    method.invoke(null, MESSAGE).also { result ->
                        assertThat(result)
                            .isInstanceOf(String::class.java)
                            .isEqualTo(MESSAGE)
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod("unwantedStringToDelete", String::class.java) }
            }
        }
    }

    @Test
    fun deleteLongFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("unwantedLongToDelete", Long::class.java).also { method ->
                    method.invoke(null, BIG_NUMBER).also { result ->
                        assertThat(result)
                            .isInstanceOf(Long::class.javaObjectType)
                            .isEqualTo(BIG_NUMBER)
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod("unwantedLongToDelete", Long::class.java) }
            }
        }
    }

    @Test
    fun deleteIntFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("unwantedIntToDelete", Int::class.java).also { method ->
                    method.invoke(null, NUMBER).also { result ->
                        assertThat(result)
                            .isInstanceOf(Int::class.javaObjectType)
                            .isEqualTo(NUMBER)
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod("unwantedIntToDelete", Int::class.java) }
            }
        }
    }
}
