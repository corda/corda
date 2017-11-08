package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.KryoException
import net.corda.core.contracts.ContractAttachment
import net.corda.core.serialization.*
import net.corda.testing.SerializationEnvironmentRule
import net.corda.testing.contracts.DummyContract
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ContractAttachmentSerializerTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private lateinit var factory: SerializationFactory
    private lateinit var context: SerializationContext
    private lateinit var testContext: SerializationContext

    private val mockServices = MockServices()

    @Before
    fun setup() {
        factory = testSerialization.env.SERIALIZATION_FACTORY
        context = testSerialization.env.CHECKPOINT_CONTEXT

        testContext = context.withTokenContext(SerializeAsTokenContextImpl(Any(), factory, context, mockServices))
    }

    @Test
    fun `write contract attachment and read it back`() {
        val attachment = GeneratedAttachment("test".toByteArray())

        mockServices.attachments.importAttachment(attachment.open())

        val contractAttachment = ContractAttachment(attachment, DummyContract.PROGRAM_ID)
        val serialized = contractAttachment.serialize(factory, testContext)
        val deserialized = serialized.deserialize(factory, testContext)

        assertEquals(contractAttachment.id, deserialized.attachment.id)
        assertEquals(contractAttachment.contract, deserialized.contract)
        assertArrayEquals(contractAttachment.open().readBytes(), deserialized.open().readBytes())
    }


    @Test
    fun `throws when no serializationContext`() {
        val contractAttachment = ContractAttachment(GeneratedAttachment(ByteArray(0)), DummyContract.PROGRAM_ID)
        // don't pass context in serialize
        val serialized = contractAttachment.serialize()

        Assertions.assertThatThrownBy { serialized.deserialize() }
                .isInstanceOf(KryoException::class.java).hasMessageContaining(
                "Attempt to read a ${ContractAttachment::class.java.name} instance without initialising a context")
    }

    @Test
    fun `throws when missing attachment`() {
        val attachment = GeneratedAttachment("test".toByteArray())

        // don't importAttachment in mockService

        val contractAttachment = ContractAttachment(attachment, DummyContract.PROGRAM_ID)
        val serialized = contractAttachment.serialize(factory, testContext)
        val deserialized = serialized.deserialize(factory, testContext)

        Assertions.assertThatThrownBy { deserialized.attachment.open() }.isInstanceOf(MissingAttachmentsException::class.java)
    }

    @Test
    fun `check only serialize attachment id and contract class name`() {
        val largeAttachmentSize = 1024 * 1024
        val attachment = GeneratedAttachment(ByteArray(largeAttachmentSize))

        mockServices.attachments.importAttachment(attachment.open())

        val contractAttachment = ContractAttachment(attachment, DummyContract.PROGRAM_ID)
        val serialized = contractAttachment.serialize(factory, testContext)

        assertThat(serialized.size).isLessThan(largeAttachmentSize)
    }

    @Test
    fun `check attachment in deserialize is lazy loaded`() {
        val attachment = GeneratedAttachment(ByteArray(0))

        // don't importAttachment in mockService

        val contractAttachment = ContractAttachment(attachment, DummyContract.PROGRAM_ID)
        val serialized = contractAttachment.serialize(factory, testContext)
        serialized.deserialize(factory, testContext)

        // MissingAttachmentsException thrown if we try to open attachment
    }
}


