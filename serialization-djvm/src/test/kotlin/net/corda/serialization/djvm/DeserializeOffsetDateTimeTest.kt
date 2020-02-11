package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.time.OffsetDateTime
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeOffsetDateTimeTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing offset date-time`() {
        val dateTime = OffsetDateTime.now()
        val data = dateTime.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxDateTime = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showOffsetDateTime = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowOffsetDateTime::class.java)
            val result = showOffsetDateTime.apply(sandboxDateTime) ?: fail("Result cannot be null")

            assertEquals(dateTime.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowOffsetDateTime : Function<OffsetDateTime, String> {
        override fun apply(dateTime: OffsetDateTime): String {
            return dateTime.toString()
        }
    }
}
