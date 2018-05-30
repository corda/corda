package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isClass
import org.assertj.core.api.Assertions.*
import org.hamcrest.core.IsCollectionContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.test.assertFailsWith

class DeleteNestedClassTest {
    companion object {
        private const val HOST_CLASS = "net.corda.gradle.HasNestedClasses"
        private const val KEPT_CLASS = "$HOST_CLASS\$OneToKeep"
        private const val DELETED_CLASS = "$HOST_CLASS\$OneToThrowAway"

        private const val SEALED_CLASS = "net.corda.gradle.SealedClass"
        private const val WANTED_CLASS = "$SEALED_CLASS\$Wanted"
        private const val UNWANTED_CLASS = "$SEALED_CLASS\$Unwanted"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-nested-class")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteNestedClass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val deleted = cl.load<Any>(DELETED_CLASS)
            val kept = cl.load<Any>(KEPT_CLASS)
            cl.load<Any>(HOST_CLASS).apply {
                assertThat(classes).containsExactlyInAnyOrder(deleted, kept)
                assertThat("OneToThrowAway class is missing", kotlin.nestedClasses, hasItem(isClass(DELETED_CLASS)))
                assertThat("OneToKeep class is missing", kotlin.nestedClasses, hasItem(isClass(KEPT_CLASS)))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(DELETED_CLASS) }
            val kept = cl.load<Any>(KEPT_CLASS)
            cl.load<Any>(HOST_CLASS).apply {
                assertThat(classes).containsExactly(kept)
                assertThat("OneToThrowAway class still exists", kotlin.nestedClasses, not(hasItem(isClass(DELETED_CLASS))))
                assertThat("OneToKeep class is missing", kotlin.nestedClasses, hasItem(isClass(KEPT_CLASS)))
            }
        }
    }

    @Test
    fun deleteFromSealedClass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val unwanted = cl.load<Any>(UNWANTED_CLASS)
            val wanted = cl.load<Any>(WANTED_CLASS)
            cl.load<Any>(SEALED_CLASS).apply {
                assertTrue(kotlin.isSealed)
                assertThat(classes).containsExactlyInAnyOrder(wanted, unwanted)
                assertThat("Wanted class is missing", kotlin.nestedClasses, hasItem(isClass(WANTED_CLASS)))
                assertThat("Unwanted class is missing", kotlin.nestedClasses, hasItem(isClass(UNWANTED_CLASS)))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(UNWANTED_CLASS) }
            val wanted = cl.load<Any>(WANTED_CLASS)
            cl.load<Any>(SEALED_CLASS).apply {
                assertTrue(kotlin.isSealed)
                assertThat(classes).containsExactly(wanted)
                assertThat("Unwanted class still exists", kotlin.nestedClasses, not(hasItem(isClass(UNWANTED_CLASS))))
                assertThat("Wanted class is missing", kotlin.nestedClasses, hasItem(isClass(WANTED_CLASS)))
            }
        }
    }
}
