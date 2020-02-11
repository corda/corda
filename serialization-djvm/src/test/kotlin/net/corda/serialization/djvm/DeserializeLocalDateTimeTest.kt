package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.time.LocalDateTime
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeLocalDateTimeTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing local date-time`() {
        val dateTime = LocalDateTime.now()
        val data = dateTime.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxDateTime = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showLocalDateTime = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowLocalDateTime::class.java)
            val result = showLocalDateTime.apply( sandboxDateTime) ?: fail("Result cannot be null")

            assertEquals(dateTime.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowLocalDateTime : Function<LocalDateTime, String> {
        override fun apply(dateTime: LocalDateTime): String {
            return dateTime.toString()
        }
    }
}
