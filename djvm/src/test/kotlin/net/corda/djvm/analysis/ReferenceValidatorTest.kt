package net.corda.djvm.analysis

import net.corda.djvm.TestBase
import net.corda.djvm.execution.SandboxedRunnable
import net.corda.djvm.validation.ReferenceValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ReferenceValidatorTest : TestBase() {

    private fun validator(whitelist: Whitelist = Whitelist.MINIMAL) =
            ReferenceValidator(AnalysisConfiguration(whitelist))

    @Test
    fun `can validate when there are no references`() = analyze { context ->
        analyze<EmptyRunnable>(context)
        val (_, messages) = validator().validate(context, this)
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
        analyze<TestRandom>(context)
        val (_, messages) = validator().validate(context, this)
        assertThat(messages.count).isEqualTo(0)
    }

    private class RunnableWithReferences : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            return TestRandom().nextInt()
        }
    }

    private class TestRandom {
        external fun nextInt(): Int
    }

    @Test
    fun `can validate when there are transient references`() = analyze { context ->
        analyze<RunnableWithTransientReferences>(context)
        analyze<ReferencedClass>(context)
        analyze<TestRandom>(context)
        val (_, messages) = validator().validate(context, this)
        assertThat(messages.count).isEqualTo(0)
    }

    private class RunnableWithTransientReferences : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            return ReferencedClass().test()
        }
    }

    private class ReferencedClass {
        fun test(): Int {
            return TestRandom().nextInt()
        }
    }

}
