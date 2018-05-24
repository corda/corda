package net.corda.sandbox.analysis

import net.corda.sandbox.TestBase
import net.corda.sandbox.assertions.AssertionExtensions.withMessage
import net.corda.sandbox.execution.SandboxedRunnable
import net.corda.sandbox.validation.ReferenceValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.URLClassLoader
import java.util.*

class ReferenceValidatorTest : TestBase() {

    private fun validator(whitelist: Whitelist = Whitelist.DEFAULT) =
            ReferenceValidator(AnalysisConfiguration(whitelist))

    @Test
    fun `can validate when there are no references`() = analyze { context ->
        analyze<EmptyRunnable>(context)
        val (_, messages) = validator(Whitelist.DEFAULT).validate(context, this)
        assertThat(messages.count).isEqualTo(0)
    }

    private class EmptyRunnable : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            return null
        }
    }

    @Test
    fun `can validate when there are references`() = analyze { context ->
        analyze<RunnableWithReferences>(context)
        analyze<Random>(context)
        val (_, messages) = validator(Whitelist.DEFAULT).validate(context, this)
        assertThat(messages.sorted())
                .hasSize(3)
                .withMessage("Invalid reference to class java.util.Random, entity is not whitelisted")
                .withMessage("Invalid reference to constructor java.util.Random(), entity is not whitelisted")
                .withMessage("Invalid reference to method java.util.Random.nextInt(), entity is not whitelisted")
    }

    private class RunnableWithReferences : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            return Random().nextInt()
        }
    }


    @Test
    fun `can validate when there are transient references`() = analyze { context ->
        analyze<RunnableWithTransientReferences>(context)
        analyze<Random>(context)
        val (_, messages) = validator(Whitelist.DEFAULT).validate(context, this)
        assertThat(messages.sorted())
                .hasSize(4)
                .withMessage("Invalid reference to class java.net.URLClassLoader, entity is not whitelisted")
                .withMessage("Invalid reference to constructor java.net.URLClassLoader(URL[]), entity is not whitelisted")
                .withMessage("Invalid reference to method java.net.URLClassLoader.getURLs(), entity is not whitelisted")
                .withMessage("Invalid reference to class java.net.URL, entity is not whitelisted")
    }

    private class RunnableWithTransientReferences : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            return ReferencedClass().test()
        }
    }

    private class ReferencedClass {
        fun test(): Int {
            val classLoader = URLClassLoader(emptyArray())
            return classLoader.urLs.size
        }
    }

}
