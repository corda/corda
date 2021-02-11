package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.SerializationSchemeContext
import net.corda.core.serialization.CustomSerializationScheme
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.testutils.serializationContext
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.NotSerializableException
import kotlin.test.assertFailsWith

class CustomSerializationSchemeAdapterTests {

    companion object {
        const val DEFAULT_SCHEME_ID = 7
    }

    class DummyInputClass
    class DummyOutputClass

    class SingleInputAndOutputScheme(private val schemeId: Int = DEFAULT_SCHEME_ID): CustomSerializationScheme {

        override fun getSchemeId(): Int {
            return schemeId
        }

        override fun <T: Any> deserialize(bytes: ByteSequence, clazz: Class<T>, context: SerializationSchemeContext): T {
            @Suppress("UNCHECKED_CAST")
            return DummyOutputClass() as T
        }

        override fun <T: Any> serialize(obj: T, context: SerializationSchemeContext): ByteSequence {
            assertTrue(obj is DummyInputClass)
            return ByteSequence.of(ByteArray(2) { 0x2 })
        }
    }

    class SameBytesInputAndOutputsAndScheme: CustomSerializationScheme {

        private val expectedBytes = "123456789".toByteArray()

        override fun getSchemeId(): Int {
            return DEFAULT_SCHEME_ID
        }

        override fun <T: Any> deserialize(bytes: ByteSequence, clazz: Class<T>, context: SerializationSchemeContext): T {
            bytes.open().use {
                val data = ByteArray(expectedBytes.size) { 0 }
                it.read(data)
                assertTrue(data.contentEquals(expectedBytes))
            }
            @Suppress("UNCHECKED_CAST")
            return DummyOutputClass() as T
        }

        override fun <T: Any> serialize(obj: T, context: SerializationSchemeContext): ByteSequence {
            return ByteSequence.of(expectedBytes)
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
    fun `CustomSerializationSchemeAdapter can adapt a Java implementation`() {
        val scheme = CustomSerializationSchemeAdapter(DummyCustomSerializationSchemeInJava())
        val serializedData = scheme.serialize(DummyInputClass(), serializationContext)
        val roundTripped = scheme.deserialize(serializedData, Any::class.java, serializationContext)
        assertTrue(roundTripped is DummyCustomSerializationSchemeInJava.DummyOutput)
    }
    
    @Test(timeout=300_000)
    fun `CustomSerializationSchemeAdapter validates the magic`() {
        val inScheme = CustomSerializationSchemeAdapter(SingleInputAndOutputScheme())
        val serializedData = inScheme.serialize(DummyInputClass(), serializationContext)
        val outScheme = CustomSerializationSchemeAdapter(SingleInputAndOutputScheme(8))
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