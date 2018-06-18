package net.corda.sandbox.execution

import foo.bar.sandbox.MyObject
import net.corda.sandbox.TestBase
import net.corda.sandbox.analysis.Whitelist
import net.corda.sandbox.assertions.AssertionExtensions.withProblem
import net.corda.sandbox.costing.ThresholdViolationException
import net.corda.sandbox.rewiring.SandboxClassLoadingException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import java.nio.file.Files
import java.util.*

class SandboxExecutorTest : TestBase() {

    @Test
    fun `can load and execute runnable`() = sandbox(Whitelist.MINIMAL) {
        val contractExecutor = DeterministicSandboxExecutor<Int, String>(configuration)
        val summary = contractExecutor.run<TestSandboxedRunnable>(1)
        val result = summary.result
        assertThat(result).isEqualTo("sandbox")
    }

    class TestSandboxedRunnable : SandboxedRunnable<Int, String> {
        override fun run(input: Int): String? {
            return "sandbox"
        }
    }

    @Test
    fun `can load and execute contract`() = sandbox(
            pinnedClasses = setOf(Transaction::class.java)
    ) {
        val contractExecutor = DeterministicSandboxExecutor<Transaction, Unit>(configuration)
        val tx = Transaction(1)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<Contract>(tx) }
                .withCauseInstanceOf(IllegalArgumentException::class.java)
                .withMessageContaining("Contract constraint violated")
    }

    class Contract : SandboxedRunnable<Transaction, Unit> {
        override fun run(input: Transaction) {
            throw IllegalArgumentException("Contract constraint violated")
        }
    }

    data class Transaction(val id: Int)

    @Test
    fun `can load and execute code that overrides object hash code`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        val summary = contractExecutor.run<TestObjectHashCode>(0)
        val result = summary.result
        assertThat(result).isEqualTo(0xfed_c0de)
    }

    class TestObjectHashCode : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            val obj = Object()
            return obj.hashCode()
        }
    }

    @Test
    fun `can load and execute code that overrides object hash code when derived`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        val summary = contractExecutor.run<TestObjectHashCodeWithHierarchy>(0)
        val result = summary.result
        assertThat(result).isEqualTo(0xfed_c0de)
    }

    class TestObjectHashCodeWithHierarchy : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            val obj = MyObject()
            return obj.hashCode()
        }
    }

    @Test
    fun `can detect breached threshold`() = sandbox(DEFAULT, ExecutionProfile.DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestThresholdBreach>(0) }
                .withMessageContaining("terminated due to excessive use of looping")
    }

    class TestThresholdBreach : SandboxedRunnable<Int, Int> {
        private var x = 0
        override fun run(input: Int): Int? {
            for (i in 0..1_000_000) {
                x += 1
            }
            return x
        }
    }

    @Test
    fun `can detect stack overflow`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestStackOverflow>(0) }
                .withMessageContaining("Stack overflow")
    }

    class TestStackOverflow : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            return a()
        }

        private fun a(): Int = b()
        private fun b(): Int = a()
    }

    @Test
    fun `cannot execute runnable that references non-deterministic code`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestNonDeterministicCode>(0) }
                .withCauseInstanceOf(SandboxClassLoadingException::class.java)
                .withMessageContaining(TestNonDeterministicCode::class.java.simpleName)
                .withProblem("Invalid reference to class java.util.Random, entity is not whitelisted")
                .withProblem("Invalid reference to constructor java.util.Random(), entity is not whitelisted")
                .withProblem("Invalid reference to method java.util.Random.nextInt(), entity is not whitelisted")
    }

    class TestNonDeterministicCode : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            return Random().nextInt()
        }
    }

    @Test
    fun `cannot execute runnable that catches ThreadDeath`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestCatchThreadDeath>(0) }
                .withCauseInstanceOf(SandboxClassLoadingException::class.java)
                .withMessageContaining("Disallowed catch of ThreadDeath exception")
                .withMessageContaining(TestCatchThreadDeath::class.java.simpleName)
    }

    class TestCatchThreadDeath : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            return try {
                0
            } catch (exception: ThreadDeath) {
                1
            }
        }
    }

    @Test
    fun `cannot execute runnable that catches ThresholdViolationException`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestCatchThresholdViolationException>(0) }
                .withCauseInstanceOf(SandboxClassLoadingException::class.java)
                .withMessageContaining("Disallowed catch of threshold violation exception")
                .withMessageContaining(TestCatchThresholdViolationException::class.java.simpleName)
    }

    class TestCatchThresholdViolationException : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            return try {
                0
            } catch (exception: ThresholdViolationException) {
                1
            }
        }
    }

    @Test
    fun `can catch Throwable`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        contractExecutor.run<TestCatchThrowableAndError>(1).apply {
            assertThat(result).isEqualTo(1)
        }
    }

    @Test
    fun `can catch Error`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        contractExecutor.run<TestCatchThrowableAndError>(2).apply {
            assertThat(result).isEqualTo(2)
        }
    }

    @Test
    fun `cannot catch ThreadDeath`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestCatchThrowableAndError>(3) }
                .withCauseInstanceOf(ThreadDeath::class.java)
    }

    class TestCatchThrowableAndError : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            return try {
                when (input) {
                    1 -> throw Throwable()
                    2 -> throw Error()
                    3 -> throw ThreadDeath()
                    else -> 0
                }
            } catch (exception: Error) {
                2
            } catch (exception: Throwable) {
                1
            }
        }
    }

    @Test
    fun `cannot persist state across sessions`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        val result1 = contractExecutor.run<TestStatePersistence>(0)
        val result2 = contractExecutor.run<TestStatePersistence>(0)
        assertThat(result1.result)
                .isEqualTo(result2.result)
                .isEqualTo(1)
    }

    class TestStatePersistence : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            ReferencedClass.value += 1
            return ReferencedClass.value
        }
    }

    object ReferencedClass {
        @JvmField
        var value = 0
    }

    @Test
    fun `can load and execute code that uses IO`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestIO>(0) }
                .withCauseInstanceOf(SandboxClassLoadingException::class.java)
                .withMessageContaining("Invalid reference")
                .withMessageContaining("Files.createTempFile")
                .withMessageContaining("Files.newBufferedWriter")
                .withMessageContaining("entity is not whitelisted")
    }

    class TestIO : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            val file = Files.createTempFile("test", ".dat")
            Files.newBufferedWriter(file).use {
                it.write("Hello world!")
            }
            return 0
        }
    }

    @Test
    fun `can load and execute code that uses reflection`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestReflection>(0) }
                .withCauseInstanceOf(SandboxClassLoadingException::class.java)
                .withMessageContaining("Disallowed reference to reflection API")
                .withMessageContaining("java.lang.Class.newInstance()")
                .withMessageContaining("java.lang.reflect.Method.invoke(Object, Object[])")
    }

    class TestReflection : SandboxedRunnable<Int, Int> {
        override fun run(input: Int): Int? {
            val clazz = Object::class.java
            val obj = clazz.newInstance()
            val result = clazz.methods.first().invoke(obj)
            return obj.hashCode() + result.hashCode()
        }
    }

}
