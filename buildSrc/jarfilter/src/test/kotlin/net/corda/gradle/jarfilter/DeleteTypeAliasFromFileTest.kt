package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.fileMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule

class DeleteTypeAliasFromFileTest {
    companion object {
        private const val TYPEALIAS_CLASS = "net.corda.gradle.FileWithTypeAlias"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-file-typealias")
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
            sourceClasses = testProject.sourceJar.getClassNames(TYPEALIAS_CLASS)
            filteredClasses = testProject.filteredJar.getClassNames(TYPEALIAS_CLASS)
        }
    }

    @Test
    fun deleteTypeAlias() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val metadata = cl.load<Any>(TYPEALIAS_CLASS).fileMetadata
            assertThat(metadata.typeAliasNames)
                .containsExactlyInAnyOrder("FileWantedType", "FileUnwantedType")
        }
        classLoaderFor(testProject.filteredJar).use { cl ->
            val metadata = cl.load<Any>(TYPEALIAS_CLASS).fileMetadata
            assertThat(metadata.typeAliasNames)
                .containsExactly("FileWantedType")
        }
    }
}