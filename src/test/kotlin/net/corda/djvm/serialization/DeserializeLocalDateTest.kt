package net.corda.djvm.serialization

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.djvm.serialization.SandboxType.*
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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowLocalDate::class.java).newInstance(),
                sandboxDate
            ) ?: fail("Result cannot be null")

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
