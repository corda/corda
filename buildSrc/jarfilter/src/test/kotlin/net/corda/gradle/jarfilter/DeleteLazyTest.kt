package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.*
import net.corda.gradle.unwanted.HasUnwantedVal
import org.assertj.core.api.Assertions.*
import org.hamcrest.core.IsCollectionContaining.*
import org.hamcrest.core.IsNot.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.reflect.full.declaredMemberProperties
import kotlin.test.assertFailsWith

class DeleteLazyTest {
    companion object {
        private const val LAZY_VAL_CLASS = "net.corda.gradle.HasLazyVal"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-lazy")
        private val unwantedVal = isProperty("unwantedVal", String::class)
        private val getUnwantedVal = isMethod("getUnwantedVal", String::class.java)
        private lateinit var sourceClasses: List<String>
        private lateinit var filteredClasses: List<String>

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)

        @BeforeClass
        @JvmStatic
        fun setup() {
            sourceClasses = testProject.sourceJar.getClassNames(LAZY_VAL_CLASS)
            filteredClasses = testProject.filteredJar.getClassNames(LAZY_VAL_CLASS)
        }
    }

    @Test
    fun deletedClasses() {
        assertThat(sourceClasses).contains(LAZY_VAL_CLASS)
        assertThat(filteredClasses).containsExactly(LAZY_VAL_CLASS)
    }

    @Test
    fun deleteLazyVal() {
        assertThat(sourceClasses).anyMatch { it.contains("\$unwantedVal\$") }

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVal>(LAZY_VAL_CLASS).apply {
                getConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.unwantedVal)
                }
                assertThat("getUnwantedVal not found", kotlin.javaDeclaredMethods, hasItem(getUnwantedVal))
                assertThat("unwantedVal not found", kotlin.declaredMemberProperties, hasItem(unwantedVal))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVal>(LAZY_VAL_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getConstructor(String::class.java) }
                assertThat("getUnwantedVal still exists", kotlin.javaDeclaredMethods, not(hasItem(getUnwantedVal)))
                assertThat("unwantedVal still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVal)))
            }
        }
    }
}