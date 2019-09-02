package net.corda.djvm.serialization

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.djvm.serialization.SandboxType.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.*
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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowBitSet::class.java).newInstance(),
                sandboxBitSet
            ) ?: fail("Result cannot be null")

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
