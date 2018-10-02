package net.corda.serialization.internal

import net.corda.core.contracts.ContractAttachment
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.*
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.CheckpointSerializationFactory
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.internal.CheckpointSerializationEnvironmentRule
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import org.apache.commons.lang.ArrayUtils.EMPTY_BYTE_ARRAY
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ContractAttachmentSerializerTest {

    @Rule
    @JvmField
    val testCheckpointSerialization = CheckpointSerializationEnvironmentRule()

    private lateinit var factory: CheckpointSerializationFactory
    private lateinit var context: CheckpointSerializationContext
    private lateinit var contextWithToken: CheckpointSerializationContext
    private val mockServices = MockServices(emptyList(), CordaX500Name("MegaCorp", "London", "GB"), rigorousMock())

    @Before
    fun setup() {
        factory = testCheckpointSerialization.checkpointSerializationFactory
        context = testCheckpointSerialization.checkpointSerializationContext
        contextWithToken = context.withTokenContext(CheckpointSerializeAsTokenContextImpl(Any(), factory, context, mockServices))
    }

    @Test
    fun `write contract attachment and read it back`() {
        val contractAttachment = ContractAttachment(GeneratedAttachment(EMPTY_BYTE_ARRAY), DummyContract.PROGRAM_ID)
        // no token context so will serialize the whole attachment
        val serialized = contractAttachment.checkpointSerialize(factory, context)
        val deserialized = serialized.checkpointDeserialize(factory, context)

        assertEquals(contractAttachment.id, deserialized.attachment.id)
        assertEquals(contractAttachment.contract, deserialized.contract)
        assertEquals(contractAttachment.additionalContracts, deserialized.additionalContracts)
        assertArrayEquals(contractAttachment.open().readBytes(), deserialized.open().readBytes())
    }

    @Test
    fun `write contract attachment and read it back using token context`() {
        val attachment = GeneratedAttachment("test".toByteArray())

        mockServices.attachments.importAttachment(attachment.open(), "test", null)

        val contractAttachment = ContractAttachment(attachment, DummyContract.PROGRAM_ID)
        val serialized = contractAttachment.checkpointSerialize(factory, contextWithToken)
        val deserialized = serialized.checkpointDeserialize(factory, contextWithToken)

        assertEquals(contractAttachment.id, deserialized.attachment.id)
        assertEquals(contractAttachment.contract, deserialized.contract)
        assertEquals(contractAttachment.additionalContracts, deserialized.additionalContracts)
        assertArrayEquals(contractAttachment.open().readBytes(), deserialized.open().readBytes())
    }

    @Test
    fun `check only serialize attachment id and contract class name when using token context`() {
        val largeAttachmentSize = 1024 * 1024
        val attachment = GeneratedAttachment(ByteArray(largeAttachmentSize))

        mockServices.attachments.importAttachment(attachment.open(), "test", null)

        val contractAttachment = ContractAttachment(attachment, DummyContract.PROGRAM_ID)
        val serialized = contractAttachment.checkpointSerialize(factory, contextWithToken)

        assertThat(serialized.size).isLessThan(largeAttachmentSize)
    }

    @Test
    fun `throws when missing attachment when using token context`() {
        val attachment = GeneratedAttachment("test".toByteArray())

        // don't importAttachment in mockService

        val contractAttachment = ContractAttachment(attachment, DummyContract.PROGRAM_ID)
        val serialized = contractAttachment.checkpointSerialize(factory, contextWithToken)
        val deserialized = serialized.checkpointDeserialize(factory, contextWithToken)

        assertThatThrownBy { deserialized.attachment.open() }.isInstanceOf(MissingAttachmentsException::class.java)
    }

    @Test
    fun `check attachment in deserialize is lazy loaded when using token context`() {
        val attachment = GeneratedAttachment(EMPTY_BYTE_ARRAY)
        // don't importAttachment in mockService

        val contractAttachment = ContractAttachment(attachment, DummyContract.PROGRAM_ID)
        val serialized = contractAttachment.checkpointSerialize(factory, contextWithToken)
        serialized.checkpointDeserialize(factory, contextWithToken)

        // MissingAttachmentsException thrown if we try to open attachment
    }
}


