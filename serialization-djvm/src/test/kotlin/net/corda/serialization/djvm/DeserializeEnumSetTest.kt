package net.corda.serialization.djvm

import greymalkin.ExternalEnum
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.EnumSet
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeEnumSetTest : TestBase(KOTLIN) {
    @ParameterizedTest
    @EnumSource(ExternalEnum::class)
    fun `test deserialize enum set`(value: ExternalEnum) {
        val enumSet = EnumSet.of(value)
        val data = enumSet.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxEnumSet = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showEnumSet = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowEnumSet::class.java)
            val result = showEnumSet.apply(sandboxEnumSet) ?: fail("Result cannot be null")

            assertEquals(ShowEnumSet().apply(enumSet), result.toString())
            assertEquals("EnumSet: [$value]'", result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowEnumSet : Function<EnumSet<*>, String> {
        override fun apply(input: EnumSet<*>): String {
            return "EnumSet: $input'"
        }
    }
}
