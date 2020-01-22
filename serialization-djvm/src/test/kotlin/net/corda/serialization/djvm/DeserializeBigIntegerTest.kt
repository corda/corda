package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.math.BigInteger
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeBigIntegerTest : TestBase(KOTLIN) {
    companion object {
        const val VERY_BIG_NUMBER = 1234567890123456789
    }

    @Test
	fun `test deserializing big integer`() {
        val bigInteger = BigIntegerData(BigInteger.valueOf(VERY_BIG_NUMBER))
        val data = bigInteger.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxBigInteger = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showBigInteger = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowBigInteger::class.java)
            val result = showBigInteger.apply(sandboxBigInteger) ?: fail("Result cannot be null")

            assertEquals(ShowBigInteger().apply(bigInteger), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowBigInteger : Function<BigIntegerData, String> {
        override fun apply(data: BigIntegerData): String {
            return with(data) {
                "BigInteger: $number"
            }
        }
    }
}

@CordaSerializable
data class BigIntegerData(val number: BigInteger)
