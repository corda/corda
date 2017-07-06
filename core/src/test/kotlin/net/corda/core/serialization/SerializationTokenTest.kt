package net.corda.core.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.io.Output
import com.nhaarman.mockito_kotlin.mock
import net.corda.core.node.ServiceHub
import net.corda.core.utilities.OpaqueBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class SerializationTokenTest {

    lateinit var kryo: Kryo

    @Before
    fun setup() {
        kryo = storageKryo().borrow()
    }

    @After
    fun cleanup() {
        storageKryo().release(kryo)
    }

    // Large tokenizable object so we can tell from the smaller number of serialized bytes it was actually tokenized
    private class LargeTokenizable : SingletonSerializeAsToken() {
        val bytes = OpaqueBytes(ByteArray(1024))

        val numBytes: Int
            get() = bytes.size

        override fun hashCode() = bytes.size

        override fun equals(other: Any?) = other is LargeTokenizable && other.bytes.size == this.bytes.size
    }

    companion object {
        private fun serializeAsTokenContext(toBeTokenized: Any) = SerializeAsTokenContext(toBeTokenized, storageKryo(), mock<ServiceHub>())
    }

    @Test
    fun `write token and read tokenizable`() {
        val tokenizableBefore = LargeTokenizable()
        val context = serializeAsTokenContext(tokenizableBefore)
        kryo.withSerializationContext(context) {
            val serializedBytes = tokenizableBefore.serialize(kryo)
            assertThat(serializedBytes.size).isLessThan(tokenizableBefore.numBytes)
            val tokenizableAfter = serializedBytes.deserialize(kryo)
            assertThat(tokenizableAfter).isSameAs(tokenizableBefore)
        }
    }

    private class UnitSerializeAsToken : SingletonSerializeAsToken()

    @Test
    fun `write and read singleton`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        kryo.withSerializationContext(context) {
            val serializedBytes = tokenizableBefore.serialize(kryo)
            val tokenizableAfter = serializedBytes.deserialize(kryo)
            assertThat(tokenizableAfter).isSameAs(tokenizableBefore)
        }
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `new token encountered after context init`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(emptyList<Any>())
        kryo.withSerializationContext(context) {
            tokenizableBefore.serialize(kryo)
        }
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `deserialize unregistered token`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(emptyList<Any>())
        kryo.withSerializationContext(context) {
            val serializedBytes = tokenizableBefore.toToken(serializeAsTokenContext(emptyList<Any>())).serialize(kryo)
            serializedBytes.deserialize(kryo)
        }
    }

    @Test(expected = KryoException::class)
    fun `no context set`() {
        val tokenizableBefore = UnitSerializeAsToken()
        tokenizableBefore.serialize(kryo)
    }

    @Test(expected = KryoException::class)
    fun `deserialize non-token`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        kryo.withSerializationContext(context) {
            val stream = ByteArrayOutputStream()
            Output(stream).use {
                kryo.writeClass(it, SingletonSerializeAsToken::class.java)
                kryo.writeObject(it, emptyList<Any>())
            }
            val serializedBytes = SerializedBytes<Any>(stream.toByteArray())
            serializedBytes.deserialize(kryo)
        }
    }

    private class WrongTypeSerializeAsToken : SerializeAsToken {
        override fun toToken(context: SerializeAsTokenContext): SerializationToken {
            return object : SerializationToken {
                override fun fromToken(context: SerializeAsTokenContext): Any = UnitSerializeAsToken()
            }
        }
    }

    @Test(expected = KryoException::class)
    fun `token returns unexpected type`() {
        val tokenizableBefore = WrongTypeSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        kryo.withSerializationContext(context) {
            val serializedBytes = tokenizableBefore.serialize(kryo)
            serializedBytes.deserialize(kryo)
        }
    }
}
