package net.corda.djvm.rewiring

import foo.bar.sandbox.A
import foo.bar.sandbox.B
import foo.bar.sandbox.Empty
import foo.bar.sandbox.StrictFloat
import net.corda.djvm.TestBase
import net.corda.djvm.assertions.AssertionExtensions.assertThat
import net.corda.djvm.execution.ExecutionProfile
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import sandbox.net.corda.djvm.costing.ThresholdViolationError
import java.nio.file.Paths

class ClassRewriterTest : TestBase() {

    @Test
    fun `empty transformer does nothing`() = sandbox(BLANK) {
        val callable = newCallable<Empty>()
        assertThat(callable).hasNotBeenModified()
        callable.createAndInvoke()
        assertThat(runtimeCosts).areZero()
    }

    @Test
    fun `can transform class`() = sandbox(DEFAULT) {
        val callable = newCallable<A>()
        assertThat(callable).hasBeenModified()
        callable.createAndInvoke()
        assertThat(runtimeCosts).hasInvocationCost(1)
    }

    @Test
    fun `can transform another class`() = sandbox(DEFAULT) {
        val callable = newCallable<B>()
        assertThat(callable).hasBeenModified()
        assertThat(callable).isSandboxed()
        callable.createAndInvoke()
        assertThat(runtimeCosts)
                .hasInvocationCostGreaterThanOrEqualTo(1) // Includes static constructor calls for java.lang.Math, etc.
                .hasJumpCostGreaterThanOrEqualTo(30 * 2 + 1)
    }

    @Test
    fun `cannot breach threshold`() = sandbox(ExecutionProfile.DISABLE_BRANCHING, DEFAULT) {
        val callable = newCallable<B>()
        assertThat(callable).hasBeenModified()
        assertThat(callable).isSandboxed()
        assertThatExceptionOfType(ThresholdViolationError::class.java).isThrownBy {
            callable.createAndInvoke()
        }.withMessageContaining("terminated due to excessive use of looping")
        assertThat(runtimeCosts)
                .hasAllocationCost(0)
                .hasInvocationCost(1)
                .hasJumpCost(1)
    }

    @Test
    fun `can transform class into using strictfp`() = sandbox(DEFAULT) {
        val callable = newCallable<StrictFloat>()
        assertThat(callable).hasBeenModified()
        callable.createAndInvoke()
    }

    @Test
    fun `can load a Java API that still exists in Java runtime`() = sandbox(DEFAULT) {
        assertThat(loadClass<MutableList<*>>())
                .hasClassName("sandbox.java.util.List")
                .hasBeenModified()
    }

    @Test
    fun `cannot load a Java API that was deleted from Java runtime`() = sandbox(DEFAULT) {
        assertThatExceptionOfType(SandboxClassLoadingException::class.java)
                .isThrownBy { loadClass<Paths>() }
                .withMessageContaining("Class file not found; java/nio/file/Paths.class")
    }

    @Test
    fun `load internal Sun class that still exists in Java runtime`() = sandbox(DEFAULT) {
        assertThat(loadClass<sun.misc.Unsafe>())
                .hasClassName("sandbox.sun.misc.Unsafe")
                .hasBeenModified()
    }

    @Test
    fun `cannot load internal Sun class that was deleted from Java runtime`() = sandbox(DEFAULT) {
        assertThatExceptionOfType(SandboxClassLoadingException::class.java)
                .isThrownBy { loadClass<sun.misc.Timer>() }
                .withMessageContaining("Class file not found; sun/misc/Timer.class")
    }

    @Test
    fun `can load local class`() = sandbox(DEFAULT) {
        assertThat(loadClass<Example>())
                .hasClassName("sandbox.net.corda.djvm.rewiring.ClassRewriterTest\$Example")
                .hasBeenModified()
    }

    class Example : java.util.function.Function<Int, Int> {
        override fun apply(input: Int): Int {
            return input
        }
    }
}
