package net.corda.serialization.djvm

import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import java.io.NotSerializableException
import java.util.function.Function

class DeserializeWithSerializationWhitelistTest: TestBase(KOTLIN) {
    companion object {
        const val MESSAGE = "Hello Sandbox!"

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun checkData() {
            assertNotCordaSerializable<CustomData>()
        }
    }

    @RegisterExtension
    @JvmField
    val serialization = LocalSerialization(emptySet(), setOf(CustomWhitelist))

    @Test
	fun `test deserializing custom object`() {
        val custom = CustomData(MESSAGE)
        val data = custom.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(
                classLoader = classLoader,
                customSerializerClassNames = emptySet(),
                serializationWhitelistNames = setOf(CustomWhitelist::class.java.name)
            ))

            val sandboxCustom = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showCustom = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowCustomData::class.java)
            val result = showCustom.apply(sandboxCustom) ?: fail("Result cannot be null")

            assertEquals(custom.value, result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    @Test
	fun `test deserialization needs whitelisting`() {
        val custom = CustomData(MESSAGE)
        val data = custom.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
            val ex = assertThrows<NotSerializableException> { data.deserializeFor(classLoader) }
            assertThat(ex).hasMessageContaining(
                "Class \"class sandbox.${CustomData::class.java.name}\" is not on the whitelist or annotated with @CordaSerializable."
            )
        }
    }

    class ShowCustomData : Function<CustomData, String> {
        override fun apply(custom: CustomData): String {
            return custom.value
        }
    }

    data class CustomData(val value: String)

    object CustomWhitelist : SerializationWhitelist {
        override val whitelist: List<Class<*>> = listOf(CustomData::class.java)
    }
}
