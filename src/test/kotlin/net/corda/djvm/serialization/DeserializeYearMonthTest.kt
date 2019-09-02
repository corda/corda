package net.corda.djvm.serialization

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.djvm.serialization.SandboxType.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.time.YearMonth
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeYearMonthTest : TestBase(KOTLIN) {
    @Test
    fun `test deserializing year-month`() {
        val yearMonth = YearMonth.now()
        val data = yearMonth.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxYearMonth = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowYearMonth::class.java).newInstance(),
                sandboxYearMonth
            ) ?: fail("Result cannot be null")

            assertEquals(yearMonth.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowYearMonth : Function<YearMonth, String> {
        override fun apply(yearMonth: YearMonth): String {
            return yearMonth.toString()
        }
    }
}
