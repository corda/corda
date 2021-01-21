package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.CustomSerializationContext
import net.corda.core.serialization.CustomSerializationMagic
import net.corda.core.serialization.CustomSerializationScheme
import net.corda.core.serialization.SerializedBytes
import net.corda.nodeapi.internal.serialization.testutils.serializationContext
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.NotSerializableException
import kotlin.test.assertFailsWith

class CustomSerializationSchemeAdapterTests {

    companion object {
        const val DEFAULT_MAGIC = 7
    }

    class DummyInputClass
    class DummyOutputClass

    class SingleInputAndOutputScheme(val magic: CustomSerializationMagic = CustomSerializationMagic(DEFAULT_MAGIC)): CustomSerializationScheme {

        override fun getSerializationMagic(): CustomSerializationMagic {
            return magic
        }

        override fun deserialize(bytes: SerializedBytes<*>, clazz: Class<*>, context: CustomSerializationContext): Any {
            return DummyOutputClass()
        }

        override fun serialize(obj: Any, context: CustomSerializationContext): SerializedBytes<*> {
            assertTrue(obj is DummyInputClass)
            return SerializedBytes<Any>(ByteArray(2) { 0x2 })
        }
    }

    class SameBytesInputAndOutputsAndScheme: CustomSerializationScheme {

        private val expectedBytes = "123456789".toByteArray()

        override fun getSerializationMagic(): CustomSerializationMagic {
            return CustomSerializationMagic(DEFAULT_MAGIC)
        }

        override fun deserialize(bytes: SerializedBytes<*>, clazz: Class<*>, context: CustomSerializationContext): Any {
            assertTrue(bytes.bytes.contentEquals(expectedBytes))
            return DummyOutputClass()
        }

        override fun serialize(obj: Any, context: CustomSerializationContext): SerializedBytes<*> {
            return SerializedBytes<Any>(expectedBytes)
        }
    }

    @Test(timeout=300_000)
    fun `CustomSerializationSchemeAdapter calls the correct methods in CustomSerializationScheme`() {
        val scheme = CustomSerializationSchemeAdapter(SingleInputAndOutputScheme())
        val serializedData = scheme.serialize(DummyInputClass(), serializationContext)
        val roundTripped = scheme.deserialize(serializedData, Any::class.java, serializationContext)
        assertTrue(roundTripped is DummyOutputClass)
    }

    @Test(timeout=300_000)
    fun `CustomSerializationSchemeAdapter validates the magic`() {
        val inScheme = CustomSerializationSchemeAdapter(SingleInputAndOutputScheme())
        val serializedData = inScheme.serialize(DummyInputClass(), serializationContext)
        val outScheme = CustomSerializationSchemeAdapter(SingleInputAndOutputScheme(CustomSerializationMagic(8)))
        assertFailsWith<NotSerializableException> {
            outScheme.deserialize(serializedData, DummyOutputClass::class.java, serializationContext)
        }
    }

    @Test(timeout=300_000)
    fun `CustomSerializationSchemeAdapter preserves the serialized bytes between deserialize and serialize`() {
        val scheme = CustomSerializationSchemeAdapter(SameBytesInputAndOutputsAndScheme())
        val serializedData = scheme.serialize(Any(), serializationContext)
        scheme.deserialize(serializedData, Any::class.java, serializationContext)
    }
}