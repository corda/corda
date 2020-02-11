package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.time.Duration
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeDurationTest : TestBase(KOTLIN) {
    @Test
    fun `test deserializing duration`() {
        val duration = Duration.ofSeconds(12345, 6789)
        val data = duration.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxDuration = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showDuration = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowDuration::class.java)
            val result = showDuration.apply(sandboxDuration) ?: fail("Result cannot be null")

            assertEquals(duration.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowDuration : Function<Duration, String> {
        override fun apply(duration: Duration): String {
            return duration.toString()
        }
    }
}
