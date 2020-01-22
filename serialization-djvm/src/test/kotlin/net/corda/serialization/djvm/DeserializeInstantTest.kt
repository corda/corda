package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.time.Instant
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeInstantTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing instant`() {
        val instant = Instant.now()
        val data = instant.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxInstant = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showInstant = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowInstant::class.java)
            val result = showInstant.apply(sandboxInstant) ?: fail("Result cannot be null")

            assertEquals(instant.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowInstant : Function<Instant, String> {
        override fun apply(instant: Instant): String {
            return instant.toString()
        }
    }
}
