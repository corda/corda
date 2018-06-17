package net.corda.gradle.jarfilter

import net.corda.gradle.unwanted.HasUnwantedFun
import org.assertj.core.api.Assertions.*
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import javax.annotation.Resource
import kotlin.test.assertFailsWith

class StubFunctionOutTest {
    companion object {
        private const val FUNCTION_CLASS = "net.corda.gradle.HasFunctionToStub"
        private const val STUB_ME_OUT_ANNOTATION = "net.corda.gradle.jarfilter.StubMeOut"
        private const val PARAMETER_ANNOTATION = "net.corda.gradle.Parameter"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "stub-function")

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun stubFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val stubMeOut = cl.load<Annotation>(STUB_ME_OUT_ANNOTATION)
            val parameter = cl.load<Annotation>(PARAMETER_ANNOTATION)

            cl.load<HasUnwantedFun>(FUNCTION_CLASS).apply {
                newInstance().also { obj ->
                    assertEquals(MESSAGE, obj.unwantedFun(MESSAGE))
                }
                getMethod("unwantedFun", String::class.java).also { method ->
                    assertTrue("StubMeOut annotation missing", method.isAnnotationPresent (stubMeOut))
                    assertTrue("Resource annotation missing", method.isAnnotationPresent(Resource::class.java))
                    method.parameterAnnotations.also { paramAnns ->
                        assertEquals(1, paramAnns.size)
                        assertThat(paramAnns[0])
                            .hasOnlyOneElementSatisfying { a -> a.javaClass.isInstance(parameter) }
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            val stubMeOut = cl.load<Annotation>(STUB_ME_OUT_ANNOTATION)
            val parameter = cl.load<Annotation>(PARAMETER_ANNOTATION)

            cl.load<HasUnwantedFun>(FUNCTION_CLASS).apply {
                newInstance().also { obj ->
                    assertFailsWith<UnsupportedOperationException> { obj.unwantedFun(MESSAGE) }.also { ex ->
                        assertEquals("Method has been deleted", ex.message)
                    }
                }
                getMethod("unwantedFun", String::class.java).also { method ->
                    assertFalse("StubMeOut annotation present", method.isAnnotationPresent(stubMeOut))
                    assertTrue("Resource annotation missing", method.isAnnotationPresent(Resource::class.java))
                    method.parameterAnnotations.also { paramAnns ->
                        assertEquals(1, paramAnns.size)
                        assertThat(paramAnns[0])
                            .hasOnlyOneElementSatisfying { a -> a.javaClass.isInstance(parameter) }
                    }
                }
            }
        }
    }
}
