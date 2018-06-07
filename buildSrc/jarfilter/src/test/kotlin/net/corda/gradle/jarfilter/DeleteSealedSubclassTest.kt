package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.classMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.test.assertFailsWith

/**
 * Sealed classes can have non-nested subclasses, so long as those subclasses
 * are declared in the same file as the sealed class. Check that the metadata
 * is still updated correctly in this case.
 */
class DeleteSealedSubclassTest {
    companion object {
        private const val SEALED_CLASS = "net.corda.gradle.SealedBaseClass"
        private const val WANTED_SUBCLASS = "net.corda.gradle.WantedSubclass"
        private const val UNWANTED_SUBCLASS = "net.corda.gradle.UnwantedSubclass"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-sealed-subclass")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteUnwantedSubclass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(WANTED_SUBCLASS)
            cl.load<Any>(UNWANTED_SUBCLASS)
            cl.load<Any>(SEALED_CLASS).apply {
                assertTrue(kotlin.isSealed)
                assertThat(classMetadata.sealedSubclasses)
                    .containsExactlyInAnyOrder(WANTED_SUBCLASS, UNWANTED_SUBCLASS)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(WANTED_SUBCLASS)
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(UNWANTED_SUBCLASS) }
            cl.load<Any>(SEALED_CLASS).apply {
                assertTrue(kotlin.isSealed)
                assertThat(classMetadata.sealedSubclasses)
                    .containsExactly(WANTED_SUBCLASS)
            }
        }
    }
}