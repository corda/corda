package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.time.LocalTime
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeLocalTimeTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing local time`() {
        val time = LocalTime.now()
        val data = time.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxTime = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showLocalTime = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowLocalTime::class.java)
            val result = showLocalTime.apply(sandboxTime) ?: fail("Result cannot be null")

            assertEquals(time.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowLocalTime : Function<LocalTime, String> {
        override fun apply(time: LocalTime): String {
            return time.toString()
        }
    }
}
