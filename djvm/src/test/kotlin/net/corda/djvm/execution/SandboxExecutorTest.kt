package net.corda.djvm.execution

import foo.bar.sandbox.MyObject
import foo.bar.sandbox.testClock
import foo.bar.sandbox.toNumber
import net.corda.djvm.TestBase
import net.corda.djvm.analysis.Whitelist
import net.corda.djvm.Utilities
import net.corda.djvm.Utilities.throwRuleViolationError
import net.corda.djvm.Utilities.throwThresholdViolationError
import net.corda.djvm.assertions.AssertionExtensions.withProblem
import net.corda.djvm.rewiring.SandboxClassLoadingException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import sandbox.net.corda.djvm.costing.ThresholdViolationError
import sandbox.net.corda.djvm.rules.RuleViolationError
import java.nio.file.Files
import java.util.function.Function
import java.util.stream.Collectors.*

class SandboxExecutorTest : TestBase() {

    @Test
    fun `can load and execute runnable`() = sandbox(Whitelist.MINIMAL) {
        val contractExecutor = DeterministicSandboxExecutor<Int, String>(configuration)
        val summary = contractExecutor.run<TestSandboxedRunnable>(1)
        val result = summary.result
        assertThat(result).isEqualTo("sandbox")
    }

    class TestSandboxedRunnable : Function<Int, String> {
        override fun apply(input: Int): String {
            return "sandbox"
        }
    }

    @Test
    fun `can load and execute contract`() = sandbox(
            pinnedClasses = setOf(Transaction::class.java, Utilities::class.java)
    ) {
        val contractExecutor = DeterministicSandboxExecutor<Transaction, Unit>(configuration)
        val tx = Transaction(1)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<Contract>(tx) }
                .withCauseInstanceOf(IllegalArgumentException::class.java)
                .withMessageContaining("Contract constraint violated")
    }

    class Contract : Function<Transaction, Unit> {
        override fun apply(input: Transaction) {
            throw IllegalArgumentException("Contract constraint violated")
        }
    }

    data class Transaction(val id: Int)

    @Test
    fun `can load and execute code that overrides object hash code`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        val summary = contractExecutor.run<TestObjectHashCode>(0)
        val result = summary.result
        assertThat(result).isEqualTo(0xfed_c0de + 2)
    }

    class TestObjectHashCode : Function<Int, Int> {
        override fun apply(input: Int): Int {
            val obj = Object()
            val hash1 = obj.hashCode()
            val hash2 = obj.hashCode()
            require(hash1 == hash2)
            return Object().hashCode()
        }
    }

    @Test
    fun `can load and execute code that overrides object hash code when derived`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        val summary = contractExecutor.run<TestObjectHashCodeWithHierarchy>(0)
        val result = summary.result
        assertThat(result).isEqualTo(0xfed_c0de + 1)
    }

    class TestObjectHashCodeWithHierarchy : Function<Int, Int> {
        override fun apply(input: Int): Int {
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

    class TestThresholdBreach : Function<Int, Int> {
        private var x = 0
        override fun apply(input: Int): Int {
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
                .withCauseInstanceOf(StackOverflowError::class.java)
    }

    class TestStackOverflow : Function<Int, Int> {
        override fun apply(input: Int): Int {
            return a()
        }

        private fun a(): Int = b()
        private fun b(): Int = a()
    }


    @Test
    fun `can detect illegal references in Kotlin meta-classes`() = sandbox(DEFAULT, ExecutionProfile.DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Long>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestKotlinMetaClasses>(0) }
                .withCauseInstanceOf(NoSuchMethodError::class.java)
                .withProblem("sandbox.java.lang.System.nanoTime()J")
    }

    class TestKotlinMetaClasses : Function<Int, Long> {
        override fun apply(input: Int): Long {
            val someNumber = testClock()
            return "12345".toNumber() * someNumber
        }
    }

    @Test
    fun `cannot execute runnable that references non-deterministic code`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Long>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestNonDeterministicCode>(0) }
                .withCauseInstanceOf(NoSuchMethodError::class.java)
                .withProblem("sandbox.java.lang.System.currentTimeMillis()J")
    }

    class TestNonDeterministicCode : Function<Int, Long> {
        override fun apply(input: Int): Long {
            return System.currentTimeMillis()
        }
    }

    @Test
    fun `cannot execute runnable that catches ThreadDeath`() = sandbox(DEFAULT, pinnedClasses = setOf(Utilities::class.java)) {
        TestCatchThreadDeath().apply {
            assertThat(apply(0)).isEqualTo(1)
        }

        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestCatchThreadDeath>(0) }
                .withCauseExactlyInstanceOf(ThreadDeath::class.java)
    }

    class TestCatchThreadDeath : Function<Int, Int> {
        override fun apply(input: Int): Int {
            return try {
                throw ThreadDeath()
            } catch (exception: ThreadDeath) {
                1
            }
        }
    }

    @Test
    fun `cannot execute runnable that catches ThresholdViolationError`() = sandbox(DEFAULT, pinnedClasses = setOf(Utilities::class.java)) {
        TestCatchThresholdViolationError().apply {
            assertThat(apply(0)).isEqualTo(1)
        }

        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestCatchThresholdViolationError>(0) }
                .withCauseExactlyInstanceOf(ThresholdViolationError::class.java)
                .withMessageContaining("Can't catch this!")
    }

    class TestCatchThresholdViolationError : Function<Int, Int> {
        override fun apply(input: Int): Int {
            return try {
                throwThresholdViolationError()
            } catch (exception: ThresholdViolationError) {
                1
            }
        }
    }

    @Test
    fun `cannot execute runnable that catches RuleViolationError`() = sandbox(DEFAULT, pinnedClasses = setOf(Utilities::class.java)) {
        TestCatchRuleViolationError().apply {
            assertThat(apply(0)).isEqualTo(1)
        }

        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestCatchRuleViolationError>(0) }
                .withCauseExactlyInstanceOf(RuleViolationError::class.java)
                .withMessageContaining("Can't catch this!")
    }

    class TestCatchRuleViolationError : Function<Int, Int> {
        override fun apply(input: Int): Int {
            return try {
                throwRuleViolationError()
            } catch (exception: RuleViolationError) {
                1
            }
        }
    }

    @Test
    fun `can catch Throwable`() = sandbox(DEFAULT, pinnedClasses = setOf(Utilities::class.java)) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        contractExecutor.run<TestCatchThrowableAndError>(1).apply {
            assertThat(result).isEqualTo(1)
        }
    }

    @Test
    fun `can catch Error`() = sandbox(DEFAULT, pinnedClasses = setOf(Utilities::class.java)) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        contractExecutor.run<TestCatchThrowableAndError>(2).apply {
            assertThat(result).isEqualTo(2)
        }
    }

    @Test
    fun `cannot catch ThreadDeath`() = sandbox(DEFAULT, pinnedClasses = setOf(Utilities::class.java)) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestCatchThrowableErrorsAndThreadDeath>(3) }
                .withCauseInstanceOf(ThreadDeath::class.java)
    }

    class TestCatchThrowableAndError : Function<Int, Int> {
        override fun apply(input: Int): Int {
            return try {
                when (input) {
                    1 -> throw Throwable()
                    2 -> throw Error()
                    else -> 0
                }
            } catch (exception: Error) {
                2
            } catch (exception: Throwable) {
                1
            }
        }
    }

    class TestCatchThrowableErrorsAndThreadDeath : Function<Int, Int> {
        override fun apply(input: Int): Int {
            return try {
                when (input) {
                    1 -> throw Throwable()
                    2 -> throw Error()
                    3 -> try {
                        throw ThreadDeath()
                    } catch (ex: ThreadDeath) {
                        3
                    }
                    4 -> try {
                        throw StackOverflowError("FAKE OVERFLOW!")
                    } catch (ex: StackOverflowError) {
                        4
                    }
                    5 -> try {
                        throw OutOfMemoryError("FAKE OOM!")
                    } catch (ex: OutOfMemoryError) {
                        5
                    }
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
    fun `cannot catch stack-overflow error`() = sandbox(DEFAULT, pinnedClasses = setOf(Utilities::class.java)) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestCatchThrowableErrorsAndThreadDeath>(4) }
                .withCauseInstanceOf(StackOverflowError::class.java)
                .withMessageContaining("FAKE OVERFLOW!")
    }

    @Test
    fun `cannot catch out-of-memory error`() = sandbox(DEFAULT, pinnedClasses = setOf(Utilities::class.java)) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestCatchThrowableErrorsAndThreadDeath>(5) }
                .withCauseInstanceOf(OutOfMemoryError::class.java)
                .withMessageContaining("FAKE OOM!")
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

    class TestStatePersistence : Function<Int, Int> {
        override fun apply(input: Int): Int {
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
                .withMessageContaining("Class file not found; java/nio/file/Files.class")
    }

    class TestIO : Function<Int, Int> {
        override fun apply(input: Int): Int {
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
                .withCauseInstanceOf(RuleViolationError::class.java)
                .withMessageContaining("Disallowed reference to API;")
                .withMessageContaining("java.lang.Class.newInstance()")
    }

    class TestReflection : Function<Int, Int> {
        override fun apply(input: Int): Int {
            val clazz = Object::class.java
            val obj = clazz.newInstance()
            val result = clazz.methods.first().invoke(obj)
            return obj.hashCode() + result.hashCode()
        }
    }

    @Test
    fun `can load and execute code that uses notify()`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, String?>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestMonitors>(1) }
                .withCauseInstanceOf(RuleViolationError::class.java)
                .withMessageContaining("Disallowed reference to API;")
                .withMessageContaining("java.lang.Object.notify()")
    }

    @Test
    fun `can load and execute code that uses notifyAll()`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, String?>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestMonitors>(2) }
                .withCauseInstanceOf(RuleViolationError::class.java)
                .withMessageContaining("Disallowed reference to API;")
                .withMessageContaining("java.lang.Object.notifyAll()")
    }

    @Test
    fun `can load and execute code that uses wait()`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, String?>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestMonitors>(3) }
                .withCauseInstanceOf(RuleViolationError::class.java)
                .withMessageContaining("Disallowed reference to API;")
                .withMessageContaining("java.lang.Object.wait()")
    }

    @Test
    fun `can load and execute code that uses wait(long)`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, String?>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestMonitors>(4) }
                .withCauseInstanceOf(RuleViolationError::class.java)
                .withMessageContaining("Disallowed reference to API;")
                .withMessageContaining("java.lang.Object.wait(Long)")
    }

    @Test
    fun `can load and execute code that uses wait(long,int)`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, String?>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestMonitors>(5) }
                .withCauseInstanceOf(RuleViolationError::class.java)
                .withMessageContaining("Disallowed reference to API;")
                .withMessageContaining("java.lang.Object.wait(Long, Integer)")
    }

    @Test
    fun `code after forbidden APIs is intact`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, String?>(configuration)
        assertThat(contractExecutor.run<TestMonitors>(0).result)
                .isEqualTo("unknown")
    }

    class TestMonitors : Function<Int, String?> {
        override fun apply(input: Int): String? {
            return synchronized(this) {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                val javaObject = this as java.lang.Object
                when(input) {
                    1 -> {
                        javaObject.notify()
                        "notify"
                    }
                    2 -> {
                        javaObject.notifyAll()
                        "notifyAll"
                    }
                    3 -> {
                        javaObject.wait()
                        "wait"
                    }
                    4 -> {
                        javaObject.wait(100)
                        "wait(100)"
                    }
                    5 -> {
                        javaObject.wait(100, 10)
                        "wait(100, 10)"
                    }
                    else -> "unknown"
                }
            }
        }
    }

    @Test
    fun `can load and execute code that has a native method`() = sandbox(DEFAULT) {
        assertThatExceptionOfType(UnsatisfiedLinkError::class.java)
            .isThrownBy { TestNativeMethod().apply(0) }
            .withMessageContaining("TestNativeMethod.evilDeeds()I")

        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
            .isThrownBy { contractExecutor.run<TestNativeMethod>(0) }
            .withCauseInstanceOf(RuleViolationError::class.java)
            .withMessageContaining("Native method has been deleted")
    }

    class TestNativeMethod : Function<Int, Int> {
        override fun apply(input: Int): Int {
            return evilDeeds()
        }

        private external fun evilDeeds(): Int
    }

    @Test
    fun `check arrays still work`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, Array<Int>>(configuration)
        contractExecutor.run<TestArray>(100).apply {
            assertThat(result).isEqualTo(arrayOf(100))
        }
    }

    class TestArray : Function<Int, Array<Int>> {
        override fun apply(input: Int): Array<Int> {
            return listOf(input).toTypedArray()
        }
    }

    @Test
    fun `check building a string`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<String?, String?>(configuration)
        contractExecutor.run<TestStringBuilding>("Hello Sandbox!").apply {
            assertThat(result)
                .isEqualTo("SANDBOX: Boolean=true, Char='X', Integer=1234, Long=99999, Short=3200, Byte=101, String='Hello Sandbox!', Float=123.456, Double=987.6543")
        }
    }

    class TestStringBuilding : Function<String?, String?> {
        override fun apply(input: String?): String? {
             return StringBuilder("SANDBOX")
                    .append(": Boolean=").append(true)
                    .append(", Char='").append('X')
                    .append("', Integer=").append(1234)
                    .append(", Long=").append(99999L)
                    .append(", Short=").append(3200.toShort())
                    .append(", Byte=").append(101.toByte())
                    .append(", String='").append(input)
                    .append("', Float=").append(123.456f)
                    .append(", Double=").append(987.6543)
                    .toString()
        }
    }

    @Test
    fun `check System-arraycopy still works with Objects`() = sandbox(DEFAULT) {
        val source = arrayOf("one", "two", "three")
        assertThat(TestArrayCopy().apply(source))
            .isEqualTo(source)
            .isNotSameAs(source)

        val contractExecutor = DeterministicSandboxExecutor<Array<String>, Array<String>>(configuration)
        contractExecutor.run<TestArrayCopy>(source).apply {
            assertThat(result)
                .isEqualTo(source)
                .isNotSameAs(source)
        }
    }

    class TestArrayCopy : Function<Array<String>, Array<String>> {
        override fun apply(input: Array<String>): Array<String> {
            val newArray = Array(input.size) { "" }
            System.arraycopy(input, 0, newArray, 0, newArray.size)
            return newArray
        }
    }

    @Test
    fun `test System-arraycopy still works with CharArray`() = sandbox(DEFAULT) {
        val source = CharArray(10) { '?' }
        val contractExecutor = DeterministicSandboxExecutor<CharArray, CharArray>(configuration)
        contractExecutor.run<TestCharArrayCopy>(source).apply {
            assertThat(result)
                .isEqualTo(source)
                .isNotSameAs(source)
        }
    }

    class TestCharArrayCopy : Function<CharArray, CharArray> {
        override fun apply(input: CharArray): CharArray {
            val newArray = CharArray(input.size) { 'X' }
            System.arraycopy(input, 0, newArray, 0, newArray.size)
            return newArray
        }
    }

    @Test
    fun `can load and execute class that has finalize`() = sandbox(DEFAULT) {
        assertThatExceptionOfType(UnsupportedOperationException::class.java)
            .isThrownBy { TestFinalizeMethod().apply(100) }
            .withMessageContaining("Very Bad Thing")

        val contractExecutor = DeterministicSandboxExecutor<Int, Int>(configuration)
        contractExecutor.run<TestFinalizeMethod>(100).apply {
            assertThat(result).isEqualTo(100)
        }
    }

    class TestFinalizeMethod : Function<Int, Int> {
        override fun apply(input: Int): Int {
            finalize()
            return input
        }

        private fun finalize() {
            throw UnsupportedOperationException("Very Bad Thing")
        }
    }

    @Test
    fun `can execute parallel stream`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<String, String>(configuration)
        contractExecutor.run<TestParallelStream>("Pebble").apply {
            assertThat(result).isEqualTo("Five,Four,One,Pebble,Three,Two")
        }
    }

    class TestParallelStream : Function<String, String> {
        override fun apply(input: String): String {
            return listOf(input, "One", input, "Two", input, "Three", input, "Four", input, "Five")
                    .stream()
                    .distinct()
                    .sorted()
                    .collect(joining(","))
        }
    }

    @Test
    fun `users cannot load our sandboxed classes`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<String, Class<*>>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<TestClassForName>("java.lang.DJVM") }
                .withCauseInstanceOf(ClassNotFoundException::class.java)
                .withMessageContaining("java.lang.DJVM")
    }

    @Test
    fun `users can load sandboxed classes`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<String, Class<*>>(configuration)
        contractExecutor.run<TestClassForName>("java.util.List").apply {
            assertThat(result?.name).isEqualTo("sandbox.java.util.List")
        }
    }

    class TestClassForName : Function<String, Class<*>> {
        override fun apply(input: String): Class<*> {
            return Class.forName(input)
        }
    }

    @Test
    fun `test case-insensitive string sorting`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Array<String>, Array<String>>(configuration)
        contractExecutor.run<CaseInsensitiveSort>(arrayOf("Zelda", "angela", "BOB", "betsy", "ALBERT")).apply {
            assertThat(result).isEqualTo(arrayOf("ALBERT", "angela", "betsy", "BOB", "Zelda"))
        }
    }

    class CaseInsensitiveSort : Function<Array<String>, Array<String>> {
        override fun apply(input: Array<String>): Array<String> {
            return listOf(*input).sortedWith(String.CASE_INSENSITIVE_ORDER).toTypedArray()
        }
    }

    @Test
    fun `test unicode characters`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<Int, String>(configuration)
        contractExecutor.run<ExamineUnicodeBlock>(0x01f600).apply {
            assertThat(result).isEqualTo("EMOTICONS")
        }
    }

    class ExamineUnicodeBlock : Function<Int, String> {
        override fun apply(codePoint: Int): String {
            return Character.UnicodeBlock.of(codePoint).toString()
        }
    }

    @Test
    fun `test unicode scripts`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<String, Character.UnicodeScript?>(configuration)
        contractExecutor.run<ExamineUnicodeScript>("COMMON").apply {
            assertThat(result).isEqualTo(Character.UnicodeScript.COMMON)
        }
    }

    class ExamineUnicodeScript : Function<String, Character.UnicodeScript?> {
        override fun apply(scriptName: String): Character.UnicodeScript? {
            val script = Character.UnicodeScript.valueOf(scriptName)
            return if (script::class.java.isEnum) script else null
        }
    }

    @Test
    fun `test users cannot define new classes`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<String, Class<*>>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<DefineNewClass>("sandbox.java.lang.DJVM") }
                .withCauseInstanceOf(RuleViolationError::class.java)
                .withMessageContaining("Disallowed reference to API;")
                .withMessageContaining("java.lang.ClassLoader.defineClass")
    }

    class DefineNewClass : Function<String, Class<*>> {
        override fun apply(input: String): Class<*> {
            val data = ByteArray(0)
            val cl = object : ClassLoader(this::class.java.classLoader) {
                fun define(): Class<*> {
                    return super.defineClass(input, data, 0, data.size)
                }
            }
            return cl.define()
        }
    }

    @Test
    fun `test users cannot load new classes`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<String, Class<*>>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<LoadNewClass>("sandbox.java.lang.DJVM") }
                .withCauseInstanceOf(RuleViolationError::class.java)
                .withMessageContaining("Disallowed reference to API;")
                .withMessageContaining("java.lang.ClassLoader.loadClass")
    }

    class LoadNewClass : Function<String, Class<*>> {
        override fun apply(input: String): Class<*> {
            val cl = object : ClassLoader(this::class.java.classLoader) {
                fun load(): Class<*> {
                    return super.loadClass(input)
                }
            }
            return cl.load()
        }
    }

    @Test
    fun `test users cannot lookup classes`() = sandbox(DEFAULT) {
        val contractExecutor = DeterministicSandboxExecutor<String, Class<*>>(configuration)
        assertThatExceptionOfType(SandboxException::class.java)
                .isThrownBy { contractExecutor.run<FindClass>("sandbox.java.lang.DJVM") }
                .withCauseInstanceOf(RuleViolationError::class.java)
                .withMessageContaining("Disallowed reference to API;")
                .withMessageContaining("java.lang.ClassLoader.findClass")
    }

    class FindClass : Function<String, Class<*>> {
        override fun apply(input: String): Class<*> {
            val cl = object : ClassLoader(this::class.java.classLoader) {
                fun find(): Class<*> {
                    return super.findClass(input)
                }
            }
            return cl.find()
        }
    }
}
