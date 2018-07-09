package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.*
import net.corda.gradle.unwanted.HasInt
import net.corda.gradle.unwanted.HasLong
import net.corda.gradle.unwanted.HasString
import org.assertj.core.api.Assertions.*
import org.hamcrest.core.IsCollectionContaining.hasItem
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.jvm.kotlin
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertFailsWith

class SanitiseDeleteConstructorTest {
    companion object {
        private const val COUNT_INITIAL_OVERLOADED = 1
        private const val COUNT_INITIAL_MULTIPLE = 2
        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "sanitise-delete-constructor")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteOverloadedLongConstructor() = checkClassWithLongParameter(
        "net.corda.gradle.HasOverloadedLongConstructorToDelete",
        COUNT_INITIAL_OVERLOADED
    )

    @Test
    fun deleteMultipleLongConstructor() = checkClassWithLongParameter(
        "net.corda.gradle.HasMultipleLongConstructorsToDelete",
        COUNT_INITIAL_MULTIPLE
    )

    private fun checkClassWithLongParameter(longClass: String, initialCount: Int) {
        val longConstructor = isConstructor(longClass, Long::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasLong>(longClass).apply {
                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also {
                    assertEquals(BIG_NUMBER, it.longData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(J) not found", this, hasItem(longConstructor))
                    assertEquals(initialCount, this.size)
                }
                val primary = kotlin.primaryConstructor ?: throw AssertionError("primary constructor missing")
                assertThat(primary.call(BIG_NUMBER).longData()).isEqualTo(BIG_NUMBER)

                newInstance().also {
                    assertEquals(0, it.longData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasLong>(longClass).apply {
                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also {
                    assertEquals(BIG_NUMBER, it.longData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(J) not found", this, hasItem(longConstructor))
                    assertEquals(1, this.size)
                }
                val primary = kotlin.primaryConstructor ?: throw AssertionError("primary constructor missing")
                assertThat(primary.call(BIG_NUMBER).longData()).isEqualTo(BIG_NUMBER)

                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor() }
            }
        }
    }

    @Test
    fun deleteOverloadedIntConstructor() = checkClassWithIntParameter(
        "net.corda.gradle.HasOverloadedIntConstructorToDelete",
        COUNT_INITIAL_OVERLOADED
    )

    @Test
    fun deleteMultipleIntConstructor() = checkClassWithIntParameter(
        "net.corda.gradle.HasMultipleIntConstructorsToDelete",
        COUNT_INITIAL_MULTIPLE
    )

    private fun checkClassWithIntParameter(intClass: String, initialCount: Int) {
        val intConstructor = isConstructor(intClass, Int::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasInt>(intClass).apply {
                getDeclaredConstructor(Int::class.java).newInstance(NUMBER).also {
                    assertEquals(NUMBER, it.intData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(I) not found", this, hasItem(intConstructor))
                    assertEquals(initialCount, this.size)
                }
                val primary = kotlin.primaryConstructor ?: throw AssertionError("primary constructor missing")
                assertThat(primary.call(NUMBER).intData()).isEqualTo(NUMBER)

                newInstance().also {
                    assertEquals(0, it.intData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasInt>(intClass).apply {
                getDeclaredConstructor(Int::class.java).newInstance(NUMBER).also {
                    assertEquals(NUMBER, it.intData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(I) not found", this, hasItem(intConstructor))
                    assertEquals(1, this.size)
                }
                val primary = kotlin.primaryConstructor ?: throw AssertionError("primary constructor missing")
                assertThat(primary.call(NUMBER).intData()).isEqualTo(NUMBER)

                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor() }
            }
        }
    }

    @Test
    fun deleteOverloadedStringConstructor() = checkClassWithStringParameter(
        "net.corda.gradle.HasOverloadedStringConstructorToDelete",
        COUNT_INITIAL_OVERLOADED
    )

    @Test
    fun deleteMultipleStringConstructor() = checkClassWithStringParameter(
        "net.corda.gradle.HasMultipleStringConstructorsToDelete",
        COUNT_INITIAL_MULTIPLE
    )

    private fun checkClassWithStringParameter(stringClass: String, initialCount: Int) {
        val stringConstructor = isConstructor(stringClass, String::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasString>(stringClass).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also {
                    assertEquals(MESSAGE, it.stringData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(String) not found", this, hasItem(stringConstructor))
                    assertEquals(initialCount, this.size)
                }
                val primary = kotlin.primaryConstructor ?: throw AssertionError("primary constructor missing")
                assertThat(primary.call(MESSAGE).stringData()).isEqualTo(MESSAGE)

                newInstance().also {
                    assertEquals(DEFAULT_MESSAGE, it.stringData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasString>(stringClass).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also {
                    assertEquals(MESSAGE, it.stringData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(String) not found", this, hasItem(stringConstructor))
                    assertEquals(1, this.size)
                }
                val primary = kotlin.primaryConstructor ?: throw AssertionError("primary constructor missing")
                assertThat(primary.call(MESSAGE).stringData()).isEqualTo(MESSAGE)

                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor() }
            }
        }
    }
}
