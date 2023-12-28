package net.corda.serialization.internal

import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.io.Output
import net.corda.core.serialization.SerializationToken
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.SingletonSerializationToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.core.utilities.OpaqueBytes
import net.corda.coretesting.internal.rigorousMock
import net.corda.nodeapi.internal.serialization.kryo.KryoCheckpointSerializer
import net.corda.nodeapi.internal.serialization.kryo.kryoMagic
import net.corda.testing.core.internal.CheckpointSerializationEnvironmentRule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

class SerializationTokenTest {

    @Rule
    @JvmField
    val testCheckpointSerialization = CheckpointSerializationEnvironmentRule()

    private lateinit var context: CheckpointSerializationContext

    @Before
    fun setup() {
        context = testCheckpointSerialization.checkpointSerializationContext.withWhitelisted(SingletonSerializationToken::class.java)
    }

    // Large tokenizable object so we can tell from the smaller number of serialized bytes it was actually tokenized
    private class LargeTokenizable : SingletonSerializeAsToken() {
        val bytes = OpaqueBytes(ByteArray(1024))

        val numBytes: Int
            get() = bytes.size

        override fun hashCode() = bytes.size

        override fun equals(other: Any?) = other is LargeTokenizable && other.bytes.size == this.bytes.size
    }

    private fun serializeAsTokenContext(toBeTokenized: Any) = CheckpointSerializeAsTokenContextImpl(toBeTokenized, testCheckpointSerialization.checkpointSerializer, context, rigorousMock())
    @Test(timeout=300_000)
	fun `write token and read tokenizable`() {
        val tokenizableBefore = LargeTokenizable()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)

        val serializedBytes = tokenizableBefore.checkpointSerialize(testContext)
        assertThat(serializedBytes.size).isLessThan(tokenizableBefore.numBytes)
        val tokenizableAfter = serializedBytes.checkpointDeserialize(testContext)
        assertThat(tokenizableAfter).isSameAs(tokenizableBefore)
    }

    private class UnitSerializeAsToken : SingletonSerializeAsToken()

    @Test(timeout=300_000)
	fun `write and read singleton`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)
        val serializedBytes = tokenizableBefore.checkpointSerialize(testContext)
        val tokenizableAfter = serializedBytes.checkpointDeserialize(testContext)
        assertThat(tokenizableAfter).isSameAs(tokenizableBefore)
    }

    @Test(timeout=300_000)
    fun `new token encountered after context init`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(emptyList<Any>())
        val testContext = this.context.withTokenContext(context)
        assertThatExceptionOfType(UnsupportedOperationException::class.java).isThrownBy {
            tokenizableBefore.checkpointSerialize(testContext)
        }
    }

    @Test(timeout=300_000)
    fun `deserialize unregistered token`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(emptyList<Any>())
        val testContext = this.context.withTokenContext(context)
        assertThatExceptionOfType(UnsupportedOperationException::class.java).isThrownBy {
            tokenizableBefore.toToken(serializeAsTokenContext(emptyList<Any>())).checkpointSerialize(testContext)
        }
    }

    @Test(timeout=300_000)
    fun `no context set`() {
        val tokenizableBefore = UnitSerializeAsToken()
        assertThatExceptionOfType(KryoException::class.java).isThrownBy {
            tokenizableBefore.checkpointSerialize(context)
        }
    }

    @Test(timeout=300_000)
    fun `deserialize non-token`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)

        val kryo = KryoCheckpointSerializer.createFiberSerializer(this.context).kryo
        val stream = ByteArrayOutputStream()
        Output(stream).use {
            kryoMagic.writeTo(it)
            SectionId.ALT_DATA_AND_STOP.writeTo(it)
            kryo.writeClass(it, SingletonSerializeAsToken::class.java)
            kryo.writeObject(it, emptyList<Any>())
        }
        val serializedBytes = SerializedBytes<Any>(stream.toByteArray())
        assertThatExceptionOfType(KryoException::class.java).isThrownBy {
            serializedBytes.checkpointDeserialize(testContext)
        }
    }

    private class WrongTypeSerializeAsToken : SerializeAsToken {
        object UnitSerializationToken : SerializationToken {
            override fun fromToken(context: SerializeAsTokenContext): Any = UnitSerializeAsToken()
        }

        override fun toToken(context: SerializeAsTokenContext): SerializationToken = UnitSerializationToken
    }

    @Test(timeout=300_000)
    fun `token returns unexpected type`() {
        val tokenizableBefore = WrongTypeSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)
        val serializedBytes = tokenizableBefore.checkpointSerialize(testContext)
        assertThatExceptionOfType(KryoException::class.java).isThrownBy {
            serializedBytes.checkpointDeserialize(testContext)
        }
    }
}
