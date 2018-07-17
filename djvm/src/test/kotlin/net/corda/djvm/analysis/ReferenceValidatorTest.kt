package net.corda.djvm.analysis

import com.sun.beans.WeakCache
import net.corda.djvm.TestBase
import net.corda.djvm.assertions.AssertionExtensions.withMessage
import net.corda.djvm.execution.SandboxedRunnable
import net.corda.djvm.validation.ReferenceValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.*

class ReferenceValidatorTest : TestBase() {

    private fun validator(whitelist: Whitelist = Whitelist.DETERMINISTIC_RUNTIME) =
            ReferenceValidator(AnalysisConfiguration(whitelist))

    @Test
    fun `can validate when there are no references`() = analyze { context ->
        analyze<EmptyRunnable>(context)
        val (_, messages) = validator(Whitelist.DETERMINISTIC_RUNTIME).validate(context, this)
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
        val (_, messages) = validator(Whitelist.DETERMINISTIC_RUNTIME).validate(context, this)
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
        val (_, messages) = validator(Whitelist.DETERMINISTIC_RUNTIME).validate(context, this)
        assertThat(messages.sorted())
                .withMessage("Invalid reference to class java.util.WeakHashMap, entity is not whitelisted")
    }

    private class RunnableWithTransientReferences : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            return ReferencedClass().test()
        }
    }

    private class ReferencedClass {
        fun test(): Int {
            val cache = WeakCache<String, Int>()
            return cache.hashCode()
        }
    }

}
