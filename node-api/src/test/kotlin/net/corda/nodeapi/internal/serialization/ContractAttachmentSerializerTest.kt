/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization

import net.corda.core.contracts.ContractAttachment
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.*
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.SerializationEnvironmentRule
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
    val testSerialization = SerializationEnvironmentRule()

    private lateinit var factory: SerializationFactory
    private lateinit var context: SerializationContext
    private lateinit var contextWithToken: SerializationContext
    private val mockServices = MockServices(emptyList(), CordaX500Name("MegaCorp", "London", "GB"), rigorousMock())

    @Before
    fun setup() {
        factory = testSerialization.serializationFactory
        context = testSerialization.checkpointContext
        contextWithToken = context.withTokenContext(SerializeAsTokenContextImpl(Any(), factory, context, mockServices))
    }

    @Test
    fun `write contract attachment and read it back`() {
        val contractAttachment = ContractAttachment(GeneratedAttachment(EMPTY_BYTE_ARRAY), DummyContract.PROGRAM_ID)
        // no token context so will serialize the whole attachment
        val serialized = contractAttachment.serialize(factory, context)
        val deserialized = serialized.deserialize(factory, context)

        assertEquals(contractAttachment.id, deserialized.attachment.id)
        assertEquals(contractAttachment.contract, deserialized.contract)
        assertEquals(contractAttachment.additionalContracts, deserialized.additionalContracts)
        assertArrayEquals(contractAttachment.open().readBytes(), deserialized.open().readBytes())
    }

    @Test
    fun `write contract attachment and read it back using token context`() {
        val attachment = GeneratedAttachment("test".toByteArray())

        mockServices.attachments.importAttachment(attachment.open())

        val contractAttachment = ContractAttachment(attachment, DummyContract.PROGRAM_ID)
        val serialized = contractAttachment.serialize(factory, contextWithToken)
        val deserialized = serialized.deserialize(factory, contextWithToken)

        assertEquals(contractAttachment.id, deserialized.attachment.id)
        assertEquals(contractAttachment.contract, deserialized.contract)
        assertEquals(contractAttachment.additionalContracts, deserialized.additionalContracts)
        assertArrayEquals(contractAttachment.open().readBytes(), deserialized.open().readBytes())
    }

    @Test
    fun `check only serialize attachment id and contract class name when using token context`() {
        val largeAttachmentSize = 1024 * 1024
        val attachment = GeneratedAttachment(ByteArray(largeAttachmentSize))

        mockServices.attachments.importAttachment(attachment.open())

        val contractAttachment = ContractAttachment(attachment, DummyContract.PROGRAM_ID)
        val serialized = contractAttachment.serialize(factory, contextWithToken)

        assertThat(serialized.size).isLessThan(largeAttachmentSize)
    }

    @Test
    fun `throws when missing attachment when using token context`() {
        val attachment = GeneratedAttachment("test".toByteArray())

        // don't importAttachment in mockService

        val contractAttachment = ContractAttachment(attachment, DummyContract.PROGRAM_ID)
        val serialized = contractAttachment.serialize(factory, contextWithToken)
        val deserialized = serialized.deserialize(factory, contextWithToken)

        assertThatThrownBy { deserialized.attachment.open() }.isInstanceOf(MissingAttachmentsException::class.java)
    }

    @Test
    fun `check attachment in deserialize is lazy loaded when using token context`() {
        val attachment = GeneratedAttachment(EMPTY_BYTE_ARRAY)
        // don't importAttachment in mockService

        val contractAttachment = ContractAttachment(attachment, DummyContract.PROGRAM_ID)
        val serialized = contractAttachment.serialize(factory, contextWithToken)
        serialized.deserialize(factory, contextWithToken)

        // MissingAttachmentsException thrown if we try to open attachment
    }
}


