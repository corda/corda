package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.time.MonthDay
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeMonthDayTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing month-day`() {
        val monthDay = MonthDay.now()
        val data = monthDay.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxMonthDay = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showMonthDay = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowMonthDay::class.java)
            val result = showMonthDay.apply(sandboxMonthDay) ?: fail("Result cannot be null")

            assertEquals(monthDay.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowMonthDay : Function<MonthDay, String> {
        override fun apply(monthDay: MonthDay): String {
            return monthDay.toString()
        }
    }
}
