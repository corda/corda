package net.corda.djvm.serialization

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.djvm.serialization.SandboxType.*
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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowLocalTime::class.java).newInstance(),
                sandboxTime
            ) ?: fail("Result cannot be null")

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
