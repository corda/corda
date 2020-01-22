package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.time.Year
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeYearTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing year`() {
        val year = Year.now()
        val data = year.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxYear = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showYear = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowYear::class.java)
            val result = showYear.apply(sandboxYear) ?: fail("Result cannot be null")

            assertEquals(year.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowYear : Function<Year, String> {
        override fun apply(year: Year): String {
            return year.toString()
        }
    }
}
