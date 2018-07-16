package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isConstructor
import net.corda.gradle.unwanted.HasInt
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.core.IsCollectionContaining.*
import org.hamcrest.core.IsNot.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.test.assertFailsWith

class DeleteInnerLambdaTest {
    companion object {
        private const val LAMBDA_CLASS = "net.corda.gradle.HasInnerLambda"
        private const val SIZE = 64

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-inner-lambda")
        private val constructInt = isConstructor(LAMBDA_CLASS, Int::class)
        private val constructBytes = isConstructor(LAMBDA_CLASS, ByteArray::class)

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
            sourceClasses = testProject.sourceJar.getClassNames(LAMBDA_CLASS)
            filteredClasses = testProject.filteredJar.getClassNames(LAMBDA_CLASS)
        }
    }

    @Test
    fun `test lambda class is deleted`() {
        assertThat(sourceClasses)
            .contains(LAMBDA_CLASS)
            .hasSize(2)
        assertThat(filteredClasses).containsExactly(LAMBDA_CLASS)
    }

    @Test
    fun `test host class`() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasInt>(LAMBDA_CLASS).apply {
                getConstructor(Int::class.java).newInstance(SIZE).also { obj ->
                    assertEquals(SIZE, obj.intData())
                }
                kotlin.constructors.also { ctors ->
                    assertThat("<init>(Int) not found", ctors, hasItem(constructInt))
                    assertThat("<init>(byte[]) not found", ctors, hasItem(constructBytes))
                }

                getConstructor(ByteArray::class.java).newInstance(ByteArray(SIZE)).also { obj ->
                    assertEquals(SIZE, obj.intData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasInt>(LAMBDA_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getConstructor(Int::class.java) }
                kotlin.constructors.also { ctors ->
                    assertThat("<init>(Int) still exists", ctors, not(hasItem(constructInt)))
                    assertThat("<init>(byte[]) not found", ctors, hasItem(constructBytes))
                }

                getConstructor(ByteArray::class.java).newInstance(ByteArray(SIZE)).also { obj ->
                    assertEquals(SIZE, obj.intData())
                }
            }
        }
    }
}