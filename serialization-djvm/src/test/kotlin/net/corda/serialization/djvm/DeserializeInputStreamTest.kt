package net.corda.serialization.djvm

import net.corda.core.internal.readFully
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.io.InputStream
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeInputStreamTest : TestBase(KOTLIN) {
    companion object {
        const val MESSAGE = "Round and round the rugged rocks..."
    }

    @Test
	fun `test deserializing input stream`() {
        val data = MESSAGE.byteInputStream().serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxStream = data.deserializeTypeFor<InputStream>(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showInputStream = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowInputStream::class.java)
            val result = showInputStream.apply(sandboxStream) ?: fail("Result cannot be null")

            assertEquals(String(MESSAGE.byteInputStream().readFully()), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowInputStream : Function<InputStream, String> {
        override fun apply(input: InputStream): String {
            return String(input.readFully())
        }
    }
}
