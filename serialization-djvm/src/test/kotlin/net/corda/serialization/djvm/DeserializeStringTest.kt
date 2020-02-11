package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeStringTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing string`() {
        val stringMessage = StringMessage("Hello World!")
        val data = stringMessage.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxString = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showStringMessage = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowStringMessage::class.java)
            val result = showStringMessage.apply(sandboxString) ?: fail("Result cannot be null")

            assertEquals(stringMessage.message, result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowStringMessage : Function<StringMessage, String> {
        override fun apply(data: StringMessage): String {
            return data.message
        }
    }

    @Test
	fun `test deserializing string list of arrays`() {
        val stringListArray = StringListOfArray(listOf(
            arrayOf("Hello"), arrayOf("World"), arrayOf("!"))
        )
        val data = stringListArray.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxListArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showStringListOfArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowStringListOfArray::class.java)
            val result = showStringListOfArray.apply(sandboxListArray) ?: fail("Result cannot be null")

            assertEquals(stringListArray.data.flatMap(Array<String>::toList).joinToString(), result.toString())
        }
    }

    class ShowStringListOfArray : Function<StringListOfArray, String> {
        override fun apply(obj: StringListOfArray): String {
            return obj.data.flatMap(Array<String>::toList).joinToString()
        }
    }
}

@CordaSerializable
data class StringMessage(val message: String)

@CordaSerializable
class StringListOfArray(val data: List<Array<String>>)
