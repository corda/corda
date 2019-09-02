package net.corda.djvm.serialization

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.djvm.serialization.SandboxType.*
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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowOffsetDateTime::class.java).newInstance(),
                sandboxDateTime
            ) ?: fail("Result cannot be null")

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
