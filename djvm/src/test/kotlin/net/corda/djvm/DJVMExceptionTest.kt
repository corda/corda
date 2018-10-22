package net.corda.djvm

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import sandbox.SandboxFunction
import sandbox.Task
import sandbox.java.lang.sandbox

class DJVMExceptionTest {
    @Test
    fun testSingleException() {
        val result = Task(SingleExceptionTask()).apply("Hello World")
        assertThat(result).isInstanceOf(Throwable::class.java)
        result as Throwable

        assertThat(result.message).isEqualTo("Hello World")
        assertThat(result.cause).isNull()
        assertThat(result.stackTrace)
            .hasSize(2)
            .allSatisfy { it is StackTraceElement && it.className == result.javaClass.name }
    }

    @Test
    fun testMultipleExceptions() {
        val result = Task(MultipleExceptionsTask()).apply("Hello World")
        assertThat(result).isInstanceOf(Throwable::class.java)
        result as Throwable

        assertThat(result.message).isEqualTo("Hello World(1)(2)")
        assertThat(result.cause).isInstanceOf(Throwable::class.java)
        assertThat(result.stackTrace)
            .hasSize(2)
            .allSatisfy { it is StackTraceElement && it.className == result.javaClass.name }
        val resultLineNumbers = result.stackTrace.toLineNumbers()

        val firstCause = result.cause as Throwable
        assertThat(firstCause.message).isEqualTo("Hello World(1)")
        assertThat(firstCause.cause).isInstanceOf(Throwable::class.java)
        assertThat(firstCause.stackTrace)
            .hasSize(2)
            .allSatisfy { it is StackTraceElement && it.className == result.javaClass.name }
        val firstCauseLineNumbers = firstCause.stackTrace.toLineNumbers()

        val rootCause = firstCause.cause as Throwable
        assertThat(rootCause.message).isEqualTo("Hello World")
        assertThat(rootCause.cause).isNull()
        assertThat(rootCause.stackTrace)
            .hasSize(2)
            .allSatisfy { it is StackTraceElement && it.className == result.javaClass.name }
        val rootCauseLineNumbers = rootCause.stackTrace.toLineNumbers()

        // These stack traces should share one line number and have one distinct line number each.
        assertThat(resultLineNumbers.toSet() + firstCauseLineNumbers.toSet() + rootCauseLineNumbers.toSet())
            .hasSize(4)
    }

    @Test
    fun testJavaThrowableToSandbox() {
        val result = Throwable("Hello World").sandbox()
        assertThat(result).isInstanceOf(sandbox.java.lang.Throwable::class.java)
        result as sandbox.java.lang.Throwable

        assertThat(result.message).isEqualTo("Hello World".toDJVM())
        assertThat(result.stackTrace).isNotEmpty()
        assertThat(result.cause).isNull()
    }

    @Test
    fun testWeTryToCreateCorrectSandboxExceptionsAtRuntime() {
        assertThatExceptionOfType(ClassNotFoundException::class.java)
            .isThrownBy { Exception("Hello World").sandbox() }
            .withMessage("sandbox.java.lang.Exception")
        assertThatExceptionOfType(ClassNotFoundException::class.java)
            .isThrownBy { RuntimeException("Hello World").sandbox() }
            .withMessage("sandbox.java.lang.RuntimeException")
    }
}

class SingleExceptionTask : SandboxFunction<Any?, sandbox.java.lang.Throwable> {
    override fun apply(input: Any?): sandbox.java.lang.Throwable? {
        return sandbox.java.lang.Throwable(input as? sandbox.java.lang.String)
    }
}

class MultipleExceptionsTask : SandboxFunction<Any?, sandbox.java.lang.Throwable> {
    override fun apply(input: Any?): sandbox.java.lang.Throwable? {
        val root = sandbox.java.lang.Throwable(input as? sandbox.java.lang.String)
        val nested = sandbox.java.lang.Throwable(root.message + "(1)", root)
        return sandbox.java.lang.Throwable(nested.message + "(2)", nested)
    }
}

private infix operator fun sandbox.java.lang.String.plus(s: String): sandbox.java.lang.String {
    return (toString() + s).toDJVM()
}

private fun Array<StackTraceElement>.toLineNumbers(): IntArray {
    return map(StackTraceElement::getLineNumber).toIntArray()
}