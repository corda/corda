package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail

import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeJavaWithMultipleConstructorsTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing existing class`() {
        val multiData = MultiConstructorData("Hello World", Long.MAX_VALUE, '!')
        val data = multiData.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxData = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showMultiData = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowMultiData::class.java)
            val result = showMultiData.apply(sandboxData) ?: fail("Result cannot be null")

            assertThat(result.toString())
                .isEqualTo("MultiConstructor[message='Hello World', bigNumber=9223372036854775807, tag=!]")
        }
    }

    class ShowMultiData : Function<MultiConstructorData, String> {
        override fun apply(data: MultiConstructorData): String {
            return data.toString()
        }
    }
}
