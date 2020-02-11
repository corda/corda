package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.utilities.OpaqueBytesSubSequence
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeOpaqueBytesSubSequenceTest : TestBase(KOTLIN) {
    companion object {
        const val MESSAGE = "The rain in spain falls mainly on the plain."
        const val OFFSET = MESSAGE.length / 2
    }

    @Test
	fun `test deserializing opaquebytes subsequence`() {
        val subSequence = OpaqueBytesSubSequence(
            bytes = MESSAGE.toByteArray(),
            offset = OFFSET,
            size = MESSAGE.length - OFFSET
        )
        val data = subSequence.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxBytes = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val sandboxFunction = classLoader.createSandboxFunction()
            val showOpaqueBytesSubSequence = taskFactory.compose(sandboxFunction).apply(ShowOpaqueBytesSubSequence::class.java)
            val result = showOpaqueBytesSubSequence.apply(sandboxBytes) ?: fail("Result cannot be null")

            assertEquals(MESSAGE.substring(OFFSET), String(result as ByteArray))
            assertEquals(String(subSequence.copyBytes()), String(result))
        }
    }

    class ShowOpaqueBytesSubSequence : Function<OpaqueBytesSubSequence, ByteArray> {
        override fun apply(sequence: OpaqueBytesSubSequence): ByteArray {
            return sequence.copyBytes()
        }
    }
}
