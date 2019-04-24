package net.corda.serialization.internal

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.io.Output
import net.corda.core.serialization.*
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.core.utilities.OpaqueBytes
import net.corda.node.serialization.kryo.CordaClassResolver
import net.corda.node.serialization.kryo.CordaKryo
import net.corda.node.serialization.kryo.DefaultKryoCustomizer
import net.corda.node.serialization.kryo.kryoMagic
import net.corda.testing.core.CheckpointSerializationEnvironmentExtension
import net.corda.testing.internal.rigorousMock
import net.corda.testing.core.internal.CheckpointSerializationEnvironmentRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.ByteArrayOutputStream

class SerializationTokenTest {

    @RegisterExtension
    @JvmField
    val testCheckpointSerialization = CheckpointSerializationEnvironmentExtension()

    private lateinit var context: CheckpointSerializationContext

    @BeforeEach
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
    @Test
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

    @Test
    fun `write and read singleton`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)
        val serializedBytes = tokenizableBefore.checkpointSerialize(testContext)
        val tokenizableAfter = serializedBytes.checkpointDeserialize(testContext)
        assertThat(tokenizableAfter).isSameAs(tokenizableBefore)
    }

    @Test
    fun `new token encountered after context init`() = assertThrows<UnsupportedOperationException> {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(emptyList<Any>())
        val testContext = this.context.withTokenContext(context)
        tokenizableBefore.checkpointSerialize(testContext)
    }

    @Test
    fun `deserialize unregistered token`() = assertThrows<UnsupportedOperationException> {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(emptyList<Any>())
        val testContext = this.context.withTokenContext(context)
        val serializedBytes = tokenizableBefore.toToken(serializeAsTokenContext(emptyList<Any>())).checkpointSerialize(testContext)
        serializedBytes.checkpointDeserialize(testContext)
    }

    @Test
    fun `no context set`() = assertThrows<KryoException> {
        val tokenizableBefore = UnitSerializeAsToken()
        tokenizableBefore.checkpointSerialize(context)
    }

    @Test
    fun `deserialize non-token`() = assertThrows<KryoException> {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)

        val kryo: Kryo = DefaultKryoCustomizer.customize(CordaKryo(CordaClassResolver(this.context)))
        val stream = ByteArrayOutputStream()
        Output(stream).use {
            kryoMagic.writeTo(it)
            SectionId.ALT_DATA_AND_STOP.writeTo(it)
            kryo.writeClass(it, SingletonSerializeAsToken::class.java)
            kryo.writeObject(it, emptyList<Any>())
        }
        val serializedBytes = SerializedBytes<Any>(stream.toByteArray())
        serializedBytes.checkpointDeserialize(testContext)
    }

    private class WrongTypeSerializeAsToken : SerializeAsToken {
        object UnitSerializationToken : SerializationToken {
            override fun fromToken(context: SerializeAsTokenContext): Any = UnitSerializeAsToken()
        }

        override fun toToken(context: SerializeAsTokenContext): SerializationToken = UnitSerializationToken
    }

    @Test
    fun `token returns unexpected type`() = assertThrows<KryoException> {
        val tokenizableBefore = WrongTypeSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)
        val serializedBytes = tokenizableBefore.checkpointSerialize(testContext)
        serializedBytes.checkpointDeserialize(testContext)
    }
}
