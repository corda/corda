package net.corda.djvm.serialization

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.djvm.serialization.SandboxType.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.time.OffsetTime
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeOffsetTimeTest : TestBase(KOTLIN) {
    @Test
    fun `test deserializing instant`() {
        val time = OffsetTime.now()
        val data = time.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxTime = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowOffsetTime::class.java).newInstance(),
                sandboxTime
            ) ?: fail("Result cannot be null")

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
