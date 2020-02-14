package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.time.OffsetTime
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeOffsetTimeTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing offset time`() {
        val time = OffsetTime.now()
        val data = time.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxTime = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showOffsetTime = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowOffsetTime::class.java)
            val result = showOffsetTime.apply(sandboxTime) ?: fail("Result cannot be null")

            assertEquals(time.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowOffsetTime : Function<OffsetTime, String> {
        override fun apply(time: OffsetTime): String {
            return time.toString()
        }
    }
}
