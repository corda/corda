package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.*
import net.corda.gradle.unwanted.HasAll
import net.corda.gradle.unwanted.HasInt
import net.corda.gradle.unwanted.HasLong
import net.corda.gradle.unwanted.HasString
import org.hamcrest.core.IsCollectionContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.jvm.kotlin
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertFailsWith

class DeleteConstructorTest {
    companion object {
        private const val STRING_PRIMARY_CONSTRUCTOR_CLASS = "net.corda.gradle.PrimaryStringConstructorToDelete"
        private const val LONG_PRIMARY_CONSTRUCTOR_CLASS = "net.corda.gradle.PrimaryLongConstructorToDelete"
        private const val INT_PRIMARY_CONSTRUCTOR_CLASS = "net.corda.gradle.PrimaryIntConstructorToDelete"
        private const val SECONDARY_CONSTRUCTOR_CLASS = "net.corda.gradle.HasConstructorToDelete"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-constructor")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteConstructorWithLongParameter() {
        val longConstructor = isConstructor(SECONDARY_CONSTRUCTOR_CLASS, hasParam(Long::class))

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasLong>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also {
                    assertEquals(BIG_NUMBER, it.longData())
                }
                assertThat("<init>(J) not found", kotlin.constructors, hasItem(longConstructor))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasLong>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor(Long::class.java) }
                assertThat("<init>(J) still exists", kotlin.constructors, not(hasItem(longConstructor)))
                assertNotNull("primary constructor missing", kotlin.primaryConstructor)
            }
        }
    }

    @Test
    fun deleteConstructorWithStringParameter() {
        val stringConstructor = isConstructor(SECONDARY_CONSTRUCTOR_CLASS, hasParam(String::class))

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasString>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also {
                    assertEquals(MESSAGE, it.stringData())
                }
                assertThat("<init>(String) not found", kotlin.constructors, hasItem(stringConstructor))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasString>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor(String::class.java) }
                assertThat("<init>(String) still exists", kotlin.constructors, not(hasItem(stringConstructor)))
                assertNotNull("primary constructor missing", kotlin.primaryConstructor)
            }
        }
    }

    @Test
    fun showUnannotatedConstructorIsUnaffected() {
        val intConstructor = isConstructor(SECONDARY_CONSTRUCTOR_CLASS, hasParam(Int::class))
        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasAll>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Int::class.java).newInstance(NUMBER).also {
                    assertEquals(NUMBER, it.intData())
                    assertEquals(NUMBER.toLong(), it.longData())
                    assertEquals("<nothing>", it.stringData())
                }
                assertThat("<init>(Int) not found", kotlin.constructors, hasItem(intConstructor))
                assertNotNull("primary constructor missing", kotlin.primaryConstructor)
            }
        }
    }

    @Test
    fun deletePrimaryConstructorWithStringParameter() {
        val stringConstructor = isConstructor(STRING_PRIMARY_CONSTRUCTOR_CLASS, hasParam(String::class))

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasString>(STRING_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also {
                    assertEquals(MESSAGE, it.stringData())
                }
                assertThat("<init>(String) not found", kotlin.constructors, hasItem(stringConstructor))
                assertThat("primary constructor missing", kotlin.primaryConstructor!!, stringConstructor)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasString>(STRING_PRIMARY_CONSTRUCTOR_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor(String::class.java) }
                assertThat("<init>(String) still exists", kotlin.constructors, not(hasItem(stringConstructor)))
                assertNull("primary constructor still exists", kotlin.primaryConstructor)
            }
        }
    }

    @Test
    fun deletePrimaryConstructorWithLongParameter() {
        val longConstructor = isConstructor(LONG_PRIMARY_CONSTRUCTOR_CLASS, hasParam(Long::class))

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasLong>(LONG_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also {
                    assertEquals(BIG_NUMBER, it.longData())
                }
                assertThat("<init>(J) not found", kotlin.constructors, hasItem(longConstructor))
                assertThat("primary constructor missing", kotlin.primaryConstructor!!, longConstructor)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasLong>(LONG_PRIMARY_CONSTRUCTOR_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor(Long::class.java) }
                assertThat("<init>(J) still exists", kotlin.constructors, not(hasItem(longConstructor)))
                assertNull("primary constructor still exists", kotlin.primaryConstructor)
            }
        }
    }

    @Test
    fun deletePrimaryConstructorWithIntParameter() {
        val intConstructor = isConstructor(INT_PRIMARY_CONSTRUCTOR_CLASS, hasParam(Int::class))

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasInt>(INT_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Int::class.java).newInstance(NUMBER).also {
                    assertEquals(NUMBER, it.intData())
                }
                assertThat("<init>(I) not found", kotlin.constructors, hasItem(intConstructor))
                assertThat("primary constructor missing", kotlin.primaryConstructor!!, intConstructor)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasInt>(INT_PRIMARY_CONSTRUCTOR_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor(Int::class.java) }
                assertThat("<init>(I) still exists", kotlin.constructors, not(hasItem(intConstructor)))
                assertNull("primary constructor still exists", kotlin.primaryConstructor)
            }
        }
    }
}
