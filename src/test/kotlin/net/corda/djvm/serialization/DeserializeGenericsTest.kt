package net.corda.djvm.serialization

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.djvm.serialization.SandboxType.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeGenericsTest : TestBase(KOTLIN) {
    @Disabled
    @Test
    fun `test deserializing generic wrapper`() {
        val wrappedString = GenericWrapper(data = "Hello World!")
        val data = wrappedString.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxWrapper = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowGenericWrapper::class.java).newInstance(),
                sandboxWrapper
            ) ?: fail("Result cannot be null")

            assertEquals("Hello World!", result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowGenericWrapper : Function<GenericWrapper<String>, String> {
        override fun apply(input: GenericWrapper<String>): String {
            return input.data
        }
    }

    @Test
    fun `test deserializing concrete wrapper`() {
        val wrapped = ConcreteWrapper(
            first = GenericWrapper("Hello World"),
            second = GenericWrapper('!')
        )
        val data = wrapped.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxWrapped = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowConcreteWrapper::class.java).newInstance(),
                sandboxWrapped
            ) ?: fail("Result cannot be null")

            assertEquals("Concrete: first='Hello World', second='!'", result.toString())
        }
    }

    class ShowConcreteWrapper : Function<ConcreteWrapper, String> {
        override fun apply(input: ConcreteWrapper): String {
            return "Concrete: first='${input.first.data}', second='${input.second.data}'"
        }
    }
}

@CordaSerializable
data class GenericWrapper<T>(val data: T)

@CordaSerializable
data class ConcreteWrapper(
    val first: GenericWrapper<String>,
    val second: GenericWrapper<Char>
)
