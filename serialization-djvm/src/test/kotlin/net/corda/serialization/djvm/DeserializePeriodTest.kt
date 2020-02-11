package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.time.Period
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializePeriodTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing period`() {
        val period = Period.of(1, 2, 3)
        val data = period.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxPeriod = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showPeriod = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowPeriod::class.java)
            val result = showPeriod.apply(sandboxPeriod) ?: fail("Result cannot be null")

            assertEquals(period.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowPeriod : Function<Period, String> {
        override fun apply(period: Period): String {
            return period.toString()
        }
    }
}
