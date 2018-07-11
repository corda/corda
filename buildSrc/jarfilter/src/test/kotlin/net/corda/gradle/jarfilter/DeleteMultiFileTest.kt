package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.test.assertFailsWith

class DeleteMultiFileTest {
    companion object {
        private const val MULTIFILE_CLASS = "net.corda.gradle.HasMultiData"
        private const val STRING_METHOD = "stringToDelete"
        private const val LONG_METHOD = "longToDelete"
        private const val INT_METHOD = "intToDelete"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-multifile")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteStringFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(MULTIFILE_CLASS).apply {
                getMethod(STRING_METHOD, String::class.java).also { method ->
                    method.invoke(null, MESSAGE).also { result ->
                        assertThat(result)
                            .isInstanceOf(String::class.java)
                            .isEqualTo(MESSAGE)
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(MULTIFILE_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod(STRING_METHOD, String::class.java) }
            }
        }
    }

    @Test
    fun deleteLongFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(MULTIFILE_CLASS).apply {
                getMethod(LONG_METHOD, Long::class.java).also { method ->
                    method.invoke(null, BIG_NUMBER).also { result ->
                        assertThat(result)
                            .isInstanceOf(Long::class.javaObjectType)
                            .isEqualTo(BIG_NUMBER)
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(MULTIFILE_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod(LONG_METHOD, Long::class.java) }
            }
        }
    }

    @Test
    fun deleteIntFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(MULTIFILE_CLASS).apply {
                getMethod(INT_METHOD, Int::class.java).also { method ->
                    method.invoke(null, NUMBER).also { result ->
                        assertThat(result)
                            .isInstanceOf(Int::class.javaObjectType)
                            .isEqualTo(NUMBER)
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(MULTIFILE_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod(INT_METHOD, Int::class.java) }
            }
        }
    }
}