package net.corda.djvm.execution

import net.corda.djvm.TestBase
import org.assertj.core.api.Assertions.*
import org.junit.Test
import java.util.function.Function

class SandboxThrowableTest : TestBase() {

    @Test
    fun `test user exception handling`() = parentedSandbox {
        val contractExecutor = DeterministicSandboxExecutor<String, Array<String>>(configuration)
        contractExecutor.run<ThrowAndCatchExample>("Hello World").apply {
            assertThat(result)
                .isEqualTo(arrayOf("FIRST FINALLY", "BASE EXCEPTION", "Hello World", "SECOND FINALLY"))
        }
    }

    @Test
    fun `test rethrowing an exception`() = parentedSandbox {
        val contractExecutor = DeterministicSandboxExecutor<String, Array<String>>(configuration)
        contractExecutor.run<ThrowAndRethrowExample>("Hello World").apply {
            assertThat(result)
                .isEqualTo(arrayOf("FIRST CATCH", "FIRST FINALLY", "SECOND CATCH", "Hello World", "SECOND FINALLY"))
        }
    }

    @Test
    fun `test JVM exceptions still propagate`() = parentedSandbox {
        val contractExecutor = DeterministicSandboxExecutor<Int, String>(configuration)
        contractExecutor.run<TriggerJVMException>(-1).apply {
            assertThat(result)
                .isEqualTo("sandbox.java.lang.ArrayIndexOutOfBoundsException:-1")
        }
    }
}

class ThrowAndRethrowExample : Function<String, Array<String>> {
    override fun apply(input: String): Array<String> {
        val data = mutableListOf<String>()
        try {
            try {
                throw MyExampleException(input)
            } catch (e: Exception) {
                data += "FIRST CATCH"
                throw e
            } finally {
                data += "FIRST FINALLY"
            }
        } catch (e: MyExampleException) {
            data += "SECOND CATCH"
            e.message?.apply { data += this }
        } finally {
            data += "SECOND FINALLY"
        }

        return data.toTypedArray()
    }
}

class ThrowAndCatchExample : Function<String, Array<String>> {
    override fun apply(input: String): Array<String> {
        val data = mutableListOf<String>()
        try {
            try {
                throw MyExampleException(input)
            } finally {
                data += "FIRST FINALLY"
            }
        } catch (e: MyBaseException) {
            data += "BASE EXCEPTION"
            e.message?.apply { data += this }
        } catch (e: Exception) {
            data += "NOT THIS ONE!"
        } finally {
            data += "SECOND FINALLY"
        }

        return data.toTypedArray()
    }
}

class TriggerJVMException : Function<Int, String> {
    override fun apply(input: Int): String {
        return try {
            arrayOf(0, 1, 2)[input]
            "No Error"
        } catch (e: Exception) {
            e.javaClass.name + ':' + (e.message ?: "<MESSAGE MISSING>")
        }
    }
}

open class MyBaseException(message: String) : Exception(message)
class MyExampleException(message: String) : MyBaseException(message)