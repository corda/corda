package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeGenericsTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing generic wrapper with String`() {
        val wrappedString = GenericWrapper(data = "Hello World!")
        val data = wrappedString.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxWrapper = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val getGenericData = taskFactory.compose(classLoader.createSandboxFunction()).apply(GetGenericData::class.java)
            val result = getGenericData.apply(sandboxWrapper) ?: fail("Result cannot be null")

            assertEquals("Hello World!", result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    @Test
	fun `test deserializing generic wrapper with Integer`() {
        val wrappedInteger = GenericWrapper(data = 1000)
        val data = wrappedInteger.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxWrapper = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val getGenericData = taskFactory.compose(classLoader.createSandboxFunction()).apply(GetGenericData::class.java)
            val result = getGenericData.apply(sandboxWrapper) ?: fail("Result cannot be null")

            assertEquals("sandbox.java.lang.Integer", result::class.java.name)
            assertEquals(1000, classLoader.createBasicOutput().apply(result))
        }
    }

    @Test
	fun `test deserializing generic wrapper with array of Integer`() {
        val wrappedArrayOfInteger = GenericWrapper(arrayOf(1000, 2000, 3000))
        val data = wrappedArrayOfInteger.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxWrapper = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val getGenericData = taskFactory.compose(classLoader.createSandboxFunction()).apply(GetGenericData::class.java)
            val result = getGenericData.apply(sandboxWrapper) ?: fail("Result cannot be null")

            assertEquals("[Lsandbox.java.lang.Integer;", result::class.java.name)
            assertThat(classLoader.createBasicOutput().apply(result))
                .isEqualTo(arrayOf(1000, 2000, 3000))
        }
    }

    @Test
	fun `test deserializing generic wrapper with primitive int array`() {
        val wrappedArrayOfInteger = GenericWrapper(intArrayOf(1000, 2000, 3000))
        val data = wrappedArrayOfInteger.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxWrapper = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val getGenericData = taskFactory.compose(classLoader.createSandboxFunction()).apply(GetGenericData::class.java)
            val result = getGenericData.apply(sandboxWrapper) ?: fail("Result cannot be null")

            assertEquals("[I", result::class.java.name)
            assertThat(classLoader.createBasicOutput().apply(result))
                .isEqualTo(intArrayOf(1000, 2000, 3000))
        }
    }

    @Test
	fun `test deserializing generic list`() {
        val wrappedList = GenericWrapper(data = listOf("Hello World!"))
        val data = wrappedList.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxWrapper = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val sandboxFunction = classLoader.createSandboxFunction()
            val getGenericData = taskFactory.compose(sandboxFunction).apply(GetGenericData::class.java)
            val dataResult = getGenericData.apply(sandboxWrapper) ?: fail("Result cannot be null")

            assertEquals("[Hello World!]", dataResult.toString())
            assertEquals("sandbox.java.util.Collections\$UnmodifiableRandomAccessList", dataResult::class.java.name)

            val getGenericIterableData = taskFactory.compose(sandboxFunction).apply(GetGenericIterableData::class.java)
            val dataItemResult = getGenericIterableData.apply(sandboxWrapper) ?: fail("Result cannot be null")
            assertEquals(SANDBOX_STRING, dataItemResult::class.java.name)
        }
    }

    class GetGenericData : Function<GenericWrapper<out Any>, Any?> {
        override fun apply(input: GenericWrapper<out Any>): Any? {
            return input.data
        }
    }

    class GetGenericIterableData : Function<GenericWrapper<out Iterable<*>>, Any?> {
        override fun apply(input: GenericWrapper<out Iterable<*>>): Any? {
            return input.data.iterator().let {
                if (it.hasNext()) {
                    it.next()
                } else {
                    null
                }
            }
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

            val taskFactory = classLoader.createRawTaskFactory()
            val showConcreteWrapper = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowConcreteWrapper::class.java)
            val result = showConcreteWrapper.apply(sandboxWrapped) ?: fail("Result cannot be null")

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
