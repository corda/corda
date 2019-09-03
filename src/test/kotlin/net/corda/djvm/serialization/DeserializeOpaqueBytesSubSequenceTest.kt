package net.corda.djvm.serialization

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.utilities.OpaqueBytesSubSequence
import net.corda.djvm.serialization.SandboxType.*
import org.junit.jupiter.api.Assertions.*
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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowOpaqueBytesSubSequence::class.java).newInstance(),
                sandboxBytes
            ) ?: fail("Result cannot be null")

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
