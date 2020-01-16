package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeEnumTest : TestBase(KOTLIN) {
    @ParameterizedTest
    @EnumSource(ExampleEnum::class)
    fun `test deserialize basic enum`(value: ExampleEnum) {
        val example = ExampleData(value)
        val data = example.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxExample = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showExampleData = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowExampleData::class.java)
            val result = showExampleData.apply(sandboxExample) ?: fail("Result cannot be null")

            assertEquals(ShowExampleData().apply(example), result.toString())
            assertEquals("Example: name='${value.name}', ordinal='${value.ordinal}'", result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowExampleData : Function<ExampleData, String> {
        override fun apply(input: ExampleData): String {
            return with(input) {
                "Example: name='${value.name}', ordinal='${value.ordinal}'"
            }
        }
    }
}

@Suppress("unused")
@CordaSerializable
enum class ExampleEnum {
    ONE,
    TWO,
    THREE
}

@CordaSerializable
data class ExampleData(val value: ExampleEnum)
