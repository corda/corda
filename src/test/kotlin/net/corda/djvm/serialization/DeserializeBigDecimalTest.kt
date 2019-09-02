package net.corda.djvm.serialization

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.djvm.serialization.SandboxType.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.math.BigDecimal
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeBigDecimalTest : TestBase(KOTLIN) {
    companion object {
        const val VERY_BIG_DECIMAL = 994349993939.32737232
    }

    @Test
    fun `test deserializing big decimal`() {
        val bigDecimal = BigDecimalData(BigDecimal.valueOf(VERY_BIG_DECIMAL))
        val data = bigDecimal.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxBigInteger = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                    classLoader.loadClassForSandbox(ShowBigDecimal::class.java).newInstance(),
                    sandboxBigInteger
            ) ?: fail("Result cannot be null")

            assertEquals(ShowBigDecimal().apply(bigDecimal), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowBigDecimal : Function<BigDecimalData, String> {
        override fun apply(data: BigDecimalData): String {
            return with(data) {
                "BigDecimal: $number"
            }
        }
    }
}

@CordaSerializable
data class BigDecimalData(val number: BigDecimal)
