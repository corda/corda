package net.corda.gradle.jarfilter

import net.corda.gradle.unwanted.HasAll
import net.corda.gradle.unwanted.HasInt
import net.corda.gradle.unwanted.HasLong
import net.corda.gradle.unwanted.HasString
import org.assertj.core.api.Assertions.*
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertFailsWith

class StubConstructorTest {
    companion object {
        private const val STRING_PRIMARY_CONSTRUCTOR_CLASS = "net.corda.gradle.PrimaryStringConstructorToStub"
        private const val LONG_PRIMARY_CONSTRUCTOR_CLASS = "net.corda.gradle.PrimaryLongConstructorToStub"
        private const val INT_PRIMARY_CONSTRUCTOR_CLASS = "net.corda.gradle.PrimaryIntConstructorToStub"
        private const val SECONDARY_CONSTRUCTOR_CLASS = "net.corda.gradle.HasConstructorToStub"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "stub-constructor")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun stubConstructorWithLongParameter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasLong>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also { obj ->
                    assertEquals(BIG_NUMBER, obj.longData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasLong>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Long::class.java).also {
                    assertFailsWith<InvocationTargetException> { it.newInstance(BIG_NUMBER) }.targetException.also { ex ->
                        assertThat(ex)
                            .isInstanceOf(UnsupportedOperationException::class.java)
                            .hasMessage("Method has been deleted")
                    }
                }
            }
        }
    }

    @Test
    fun stubConstructorWithStringParameter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasString>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.stringData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasString>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(String::class.java).also {
                    assertFailsWith<InvocationTargetException> { it.newInstance(MESSAGE) }.targetException.also { ex ->
                        assertThat(ex)
                            .isInstanceOf(UnsupportedOperationException::class.java)
                            .hasMessage("Method has been deleted")
                    }
                }
            }
        }
    }

    @Test
    fun showUnannotatedConstructorIsUnaffected() {
        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasAll>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Int::class.java).newInstance(NUMBER).also { obj ->
                    assertEquals(NUMBER, obj.intData())
                    assertEquals(NUMBER.toLong(), obj.longData())
                    assertEquals("<nothing>", obj.stringData())
                }
            }
        }
    }

    @Test
    fun stubPrimaryConstructorWithStringParameter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasString>(STRING_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.stringData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasString>(STRING_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(String::class.java).also {
                    assertFailsWith<InvocationTargetException> { it.newInstance(MESSAGE) }.targetException.also { ex ->
                        assertThat(ex)
                            .isInstanceOf(UnsupportedOperationException::class.java)
                            .hasMessage("Method has been deleted")
                    }
                }
            }
        }
    }

    @Test
    fun stubPrimaryConstructorWithLongParameter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasLong>(LONG_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also { obj ->
                    assertEquals(BIG_NUMBER, obj.longData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasLong>(LONG_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Long::class.java).also {
                    assertFailsWith<InvocationTargetException> { it.newInstance(BIG_NUMBER) }.targetException.also { ex ->
                        assertThat(ex)
                            .isInstanceOf(UnsupportedOperationException::class.java)
                            .hasMessage("Method has been deleted")
                    }
                }
            }
        }
    }

    @Test
    fun stubPrimaryConstructorWithIntParameter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasInt>(INT_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Int::class.java).newInstance(NUMBER).also { obj ->
                    assertEquals(NUMBER, obj.intData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasInt>(INT_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Int::class.java).apply {
                    val error = assertFailsWith<InvocationTargetException> { newInstance(NUMBER) }.targetException
                    assertThat(error)
                        .isInstanceOf(UnsupportedOperationException::class.java)
                        .hasMessage("Method has been deleted")
                }
            }
        }
    }
}
