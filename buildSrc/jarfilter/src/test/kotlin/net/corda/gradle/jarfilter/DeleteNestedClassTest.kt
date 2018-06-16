package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.classMetadata
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
        private const val WANTED_SUBCLASS = "$SEALED_CLASS\$Wanted"
        private const val UNWANTED_SUBCLASS = "$SEALED_CLASS\$Unwanted"

        private val keptClass = isClass(KEPT_CLASS)
        private val deletedClass = isClass(DELETED_CLASS)
        private val wantedSubclass = isClass(WANTED_SUBCLASS)
        private val unwantedSubclass = isClass(UNWANTED_SUBCLASS)

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
                assertThat(declaredClasses).containsExactlyInAnyOrder(deleted, kept)
                assertThat("OneToThrowAway class is missing", kotlin.nestedClasses, hasItem(deletedClass))
                assertThat("OneToKeep class is missing", kotlin.nestedClasses, hasItem(keptClass))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(DELETED_CLASS) }
            val kept = cl.load<Any>(KEPT_CLASS)
            cl.load<Any>(HOST_CLASS).apply {
                assertThat(declaredClasses).containsExactly(kept)
                assertThat("OneToThrowAway class still exists", kotlin.nestedClasses, not(hasItem(deletedClass)))
                assertThat("OneToKeep class is missing", kotlin.nestedClasses, hasItem(keptClass))
            }
        }
    }

    @Test
    fun deleteFromSealedClass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val unwanted = cl.load<Any>(UNWANTED_SUBCLASS)
            val wanted = cl.load<Any>(WANTED_SUBCLASS)
            cl.load<Any>(SEALED_CLASS).apply {
                assertTrue(kotlin.isSealed)
                assertThat(declaredClasses).containsExactlyInAnyOrder(wanted, unwanted)
                assertThat("Wanted class is missing", kotlin.nestedClasses, hasItem(wantedSubclass))
                assertThat("Unwanted class is missing", kotlin.nestedClasses, hasItem(unwantedSubclass))
                assertThat(classMetadata.sealedSubclasses).containsExactlyInAnyOrder(WANTED_SUBCLASS, UNWANTED_SUBCLASS)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(UNWANTED_SUBCLASS) }
            val wanted = cl.load<Any>(WANTED_SUBCLASS)
            cl.load<Any>(SEALED_CLASS).apply {
                assertTrue(kotlin.isSealed)
                assertThat(declaredClasses).containsExactly(wanted)
                assertThat("Unwanted class still exists", kotlin.nestedClasses, not(hasItem(unwantedSubclass)))
                assertThat("Wanted class is missing", kotlin.nestedClasses, hasItem(wantedSubclass))
                assertThat(classMetadata.sealedSubclasses).containsExactly(WANTED_SUBCLASS)
            }
        }
    }
}
