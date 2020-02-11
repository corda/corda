package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.BitSet
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeBitSetTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing bitset`() {
        val bitSet = BitSet.valueOf(byteArrayOf(0x00, 0x70, 0x55, 0x3A, 0x48, 0x12))
        val data = bitSet.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxBitSet = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showBitSet = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowBitSet::class.java)
            val result = showBitSet.apply(sandboxBitSet) ?: fail("Result cannot be null")

            assertEquals(bitSet.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowBitSet : Function<BitSet, String> {
        override fun apply(bitSet: BitSet): String {
            return bitSet.toString()
        }
    }
}
