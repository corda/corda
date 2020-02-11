package net.corda.serialization.djvm

import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import java.util.function.Function

class DeserializeWithObjectCustomSerializerTest: TestBase(KOTLIN) {
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
    val serialization = LocalSerialization(setOf(ObjectCustomSerializer), emptySet())

    @Test
	fun `test deserializing custom object with object serializer`() {
        val custom = CustomData(MESSAGE)
        val data = custom.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(
                classLoader = classLoader,
                customSerializerClassNames = setOf(ObjectCustomSerializer::class.java.name),
                serializationWhitelistNames = emptySet()
            ))

            val sandboxCustom = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showCustom = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowCustomData::class.java)
            val result = showCustom.apply(sandboxCustom) ?: fail("Result cannot be null")

            assertEquals(custom.value, result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowCustomData : Function<CustomData, String> {
        override fun apply(custom: CustomData): String {
            return custom.value
        }
    }

    /**
     * This class REQUIRES a custom serializer because its
     * constructor parameter cannot be mapped to a property
     * automatically. THIS IS DELIBERATE!
     */
    class CustomData(initialValue: String) {
        // DO NOT MOVE THIS PROPERTY INTO THE CONSTRUCTOR!
        val value: String = initialValue
    }

    object ObjectCustomSerializer : SerializationCustomSerializer<CustomData, ObjectCustomSerializer.Proxy> {
        data class Proxy(val value: String)

        override fun fromProxy(proxy: Proxy): CustomData = CustomData(proxy.value)
        override fun toProxy(obj: CustomData): Proxy = Proxy(obj.value)
    }
}
