package net.corda.serialization.djvm

import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import net.corda.serialization.internal.amqp.CompositeType
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.TypeNotation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class SafeDeserialisationTest : TestBase(KOTLIN) {
    companion object {
        const val MESSAGE = "Nothing to see here..."
        const val NUMBER = 123.toShort()
    }

    @Test
	fun `test deserialising an evil class`() {
        val context = (_contextSerializationEnv.get() ?: fail("No serialization environment!")).p2pContext

        val innocent = InnocentData(MESSAGE, NUMBER)
        val innocentData = innocent.serialize()
        val envelope = DeserializationInput.getEnvelope(innocentData, context.encodingWhitelist).apply {
            val innocentType = schema.types[0] as CompositeType
            (schema.types as MutableList<TypeNotation>)[0] = innocentType.copy(
                name = innocentType.name.replace("Innocent", "VeryEvil")
            )
        }
        val evilData = SerializedBytes<Any>(envelope.write())

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxData = evilData.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showInnocentData = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowInnocentData::class.java)
            val result = showInnocentData.apply(sandboxData) ?: fail("Result cannot be null")

            // Check that we have deserialised the data without instantiating the Evil class.
            assertThat(result.toString())
                .isEqualTo("sandbox.net.corda.serialization.djvm.VeryEvilData: $MESSAGE, $NUMBER")

            // Check that instantiating the Evil class does indeed cause an error.
            val ex = assertThrows<ExceptionInInitializerError>{ VeryEvilData("Naughty!", 0) }
            assertThat(ex.cause)
                .isExactlyInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Victory is mine!")
        }
    }

    class ShowInnocentData : Function<InnocentData, String> {
        override fun apply(data: InnocentData): String {
            return "${data::class.java.name}: ${data.message}, ${data.number}"
        }
    }
}

