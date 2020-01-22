package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.time.LocalDate
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeLocalDateTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing local date`() {
        val date = LocalDate.now()
        val data = date.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxDate = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showLocalDate = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowLocalDate::class.java)
            val result = showLocalDate.apply(sandboxDate) ?: fail("Result cannot be null")

            assertEquals(date.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowLocalDate : Function<LocalDate, String> {
        override fun apply(date: LocalDate): String {
            return date.toString()
        }
    }
}
