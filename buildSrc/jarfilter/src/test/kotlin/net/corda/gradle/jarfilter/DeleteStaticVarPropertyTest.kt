package net.corda.gradle.jarfilter

import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.test.assertFailsWith

class DeleteStaticVarPropertyTest {
    companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.StaticVarToDelete"
        private const val DEFAULT_BIG_NUMBER: Long = 123456789L
        private const val DEFAULT_NUMBER: Int = 123456

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-static-var")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteStringVar() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredMethod("getStringVar")
                val setter = getDeclaredMethod("setStringVar", String::class.java)
                assertEquals(DEFAULT_MESSAGE, getter.invoke(null))
                setter.invoke(null, MESSAGE)
                assertEquals(MESSAGE, getter.invoke(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("getStringVar") }
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("setStringVar", String::class.java) }
            }
        }
    }

    @Test
    fun deleteLongVar() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredMethod("getLongVar")
                val setter = getDeclaredMethod("setLongVar", Long::class.java)
                assertEquals(DEFAULT_BIG_NUMBER, getter.invoke(null))
                setter.invoke(null, BIG_NUMBER)
                assertEquals(BIG_NUMBER, getter.invoke(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("getLongVar") }
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("setLongVar", Long::class.java) }
            }
        }
    }

    @Test
    fun deleteIntVar() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredMethod("getIntVar")
                val setter = getDeclaredMethod("setIntVar", Int::class.java)
                assertEquals(DEFAULT_NUMBER, getter.invoke(null))
                setter.invoke(null, NUMBER)
                assertEquals(NUMBER, getter.invoke(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("getIntVar") }
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("setIntVar", Int::class.java) }
            }
        }
    }
}
