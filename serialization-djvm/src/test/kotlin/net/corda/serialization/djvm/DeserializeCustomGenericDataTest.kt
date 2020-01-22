package net.corda.serialization.djvm

import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.internal.MissingSerializerException
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import java.util.function.Function

class DeserializeCustomGenericDataTest: TestBase(KOTLIN) {
    companion object {
        const val MESSAGE = "Hello Sandbox!"
        const val BIG_NUMBER = 23823L

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun checkData() {
            assertNotCordaSerializable<GenericData<*>>()
            assertNotCordaSerializable<ComplexGenericData>()
        }
    }

    @RegisterExtension
    @JvmField
    val serialization = LocalSerialization(setOf(CustomSerializer()), emptySet())

    @Test
	fun `test deserializing custom generic object`() {
        val complex = ComplexGenericData(MESSAGE, BIG_NUMBER)
        val data = complex.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(
                classLoader = classLoader,
                customSerializerClassNames = setOf(CustomSerializer::class.java.name),
                serializationWhitelistNames = emptySet()
            ))

            val sandboxComplex = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showComplex = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowComplexData::class.java)
            val result = showComplex.apply(sandboxComplex) ?: fail("Result cannot be null")
            assertEquals(SANDBOX_STRING, result::class.java.name)
            assertEquals(ShowComplexData().apply(complex), result.toString())
        }
    }

    @Test
	fun `test deserialization needs custom serializer`() {
        val complex = ComplexGenericData(MESSAGE, BIG_NUMBER)
        val data = complex.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
            assertThrows<MissingSerializerException> { data.deserializeFor(classLoader) }
        }
    }

    class ShowComplexData : Function<ComplexGenericData, String> {
        private fun show(generic: GenericData<*>): String = generic.toString()

        override fun apply(complex: ComplexGenericData): String {
            return "Complex: message=${show(complex.message)} and value=${show(complex.value)}"
        }
    }

    /**
     * This class REQUIRES a custom serializer because its
     * constructor parameters cannot be mapped to properties
     * automatically. THIS IS DELIBERATE!
     */
    class ComplexGenericData(msg: String, initialValue: Long?) {
        val message = GenericData(msg)
        val value = GenericData(initialValue)
    }

    class GenericData<T>(val data: T) {
        override fun toString(): String {
            return "[$data]"
        }
    }

    class CustomSerializer : SerializationCustomSerializer<ComplexGenericData, CustomSerializer.Proxy> {
        data class Proxy(val message: String, val value: Long?)

        override fun fromProxy(proxy: Proxy): ComplexGenericData = ComplexGenericData(proxy.message, proxy.value)
        override fun toProxy(obj: ComplexGenericData): Proxy = Proxy(obj.message.data, obj.value.data)
    }
}
