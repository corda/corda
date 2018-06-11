package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isMethod
import net.corda.gradle.jarfilter.matcher.isProperty
import net.corda.gradle.jarfilter.matcher.javaDeclaredMethods
import net.corda.gradle.unwanted.HasUnwantedVal
import org.hamcrest.core.IsCollectionContaining.*
import org.hamcrest.core.IsNot.*
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.reflect.full.declaredMemberExtensionProperties
import kotlin.reflect.full.declaredMemberProperties

class DeleteExtensionValPropertyTest {
    companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.HasValExtension"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-extension-val")
        private val unwantedVal = isProperty("unwantedVal", String::class)
        private val getUnwantedVal = isMethod("getUnwantedVal", String::class.java, List::class.java)

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteExtensionProperty() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVal>(PROPERTY_CLASS).apply {
                assertThat("unwantedVal not found", kotlin.declaredMemberProperties, hasItem(unwantedVal))
                assertThat("getUnwantedVal not found", kotlin.javaDeclaredMethods, hasItem(getUnwantedVal))
                assertThat("List.unwantedVal not found", kotlin.declaredMemberExtensionProperties, hasItem(unwantedVal))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVal>(PROPERTY_CLASS).apply {
                assertThat("unwantedVal not found", kotlin.declaredMemberProperties, hasItem(unwantedVal))
                assertThat("getUnwantedVal still exists", kotlin.javaDeclaredMethods, not(hasItem(getUnwantedVal)))
                assertThat("List.unwantedVal still exists", kotlin.declaredMemberExtensionProperties, not(hasItem(unwantedVal)))
            }
        }
    }
}
