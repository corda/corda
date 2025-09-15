package net.corda.node.services

import net.corda.core.flows.NotarisationPayload
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals

class NotarisationPayloadSerializationTests {
    private val notaryName = CordaX500Name("Notary", "Zurich", "CH")

    private lateinit var mockNet: MockNetwork
    private lateinit var notaryNode: StartedMockNode
    private lateinit var notaryParty: Party

    @Before
    fun setUp() {
        mockNet = MockNetwork(MockNetworkParameters(
                notarySpecs = listOf(MockNetworkNotarySpec(notaryName)),
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP)
        ))
        notaryNode = mockNet.notaryNodes[0]
        notaryParty = notaryNode.services.networkMapCache.getNotary(notaryName)!!
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    // When regenerating the test files this needs to be set to the file system location of the resource files
    @Suppress("UNUSED")
    var localPath: URI = projectRootDir.toUri().resolve("node/src/test/resources/net/corda/node/services/")

    // Read in a serialized NotarisationPayload from 4.13 (or earlier)
    // `transactionSignatures` did not exist as a field when serializing
    @Test(timeout = 300_000)
    fun deserializeNotaryChangeTransactionWithoutTransactionSignatures(){
        val resource = "NotarisationPayloadTest.transactionWithoutTransactionSignatures"
        val sf = testDefaultFactory()

        // uncomment to recreate the data
        // This has to be run on a version of Corda that _has_ transactionSignatures on NotarisationPayload
        //  val state = issueState(notaryNode.services, notaryParty, notaryNode.info.singleIdentity())
        // val tx = TransactionBuilder(notaryParty).apply {
        //     addCommand(dummyCommand())
        //     addInputState(state)
        // }.toWireTransaction(notaryNode.services)
        // val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(Crypto.generateKeyPair().public, ByteArray(32)), 0)
        // val notarisationPayload = NotarisationPayload(tx, requestSignature)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(notarisationPayload, testSerializationContext).bytes)

        val url = NotarisationPayloadSerializationTests::class.java.getResource(resource)!!
        val sc2 = url.readBytes()
        val deserializedNotarisationPayload = DeserializationInput(sf)
                .deserialize(SerializedBytes<NotarisationPayload>(sc2), testSerializationContext)

        assertEquals(1, deserializedNotarisationPayload.coreTransaction.inputs.size)
    }

    // Read in a serialized NotarisationPayload from 4.14+, with transactionSignatures
    // populated from https://github.com/corda/corda/pull/7991
    @Test(timeout = 300_000)
    fun deserializeNotarisationPayloadWithTransactionSignatures(){
        val resource = "NotarisationPayloadTest.payloadWithTransactionSignatures"
        val sf = testDefaultFactory()

        // uncomment to recreate the data.
        // This has to be run on a version of Corda that _has_ transactionSignatures on NotarisationPayload
        // val state = issueState(notaryNode.services, notaryParty, notaryNode.info.singleIdentity())
        // val tx = TransactionBuilder(notaryParty).apply {
        //    addCommand(dummyCommand())
        //    addInputState(state)
        // }.toWireTransaction(notaryNode.services)
        // val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(Crypto.generateKeyPair().public, ByteArray(32)), 0)
        // val notarisationPayload = NotarisationPayload(tx, requestSignature, listOf(requestSignature.digitalSignature))
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(notarisationPayload, testSerializationContext).bytes)

        val url = NotarisationPayloadSerializationTests::class.java.getResource(resource)!!
        val sc2 = url.readBytes()
        val deserializedNotarisationPayload = DeserializationInput(sf)
                .deserialize(SerializedBytes<NotarisationPayload>(sc2), testSerializationContext)

        assertEquals(1, deserializedNotarisationPayload.coreTransaction.inputs.size)
    }

    // Read in a serialized NotarisationPayload from 4.14+, with transactionSignatures
    // present, but not populated.
    @Test(timeout = 300_000)
    fun deserializeNotarisationPayloadWithEmptyTransactionSignatures(){
        val resource = "NotarisationPayloadTest.payloadWithEmptyTransactionSignatures"
        val sf = testDefaultFactory()

        // uncomment to recreate the data
        // This has to be run on a version of Corda that _has_ transactionSignatures on NotarisationPayload
        // val state = issueState(notaryNode.services, notaryParty, notaryNode.info.singleIdentity())
        // val tx = TransactionBuilder(notaryParty).apply {
        //    addCommand(dummyCommand())
        //    addInputState(state)
        // }.toWireTransaction(notaryNode.services)
        // val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(Crypto.generateKeyPair().public, ByteArray(32)), 0)
        // val notarisationPayload = NotarisationPayload(tx, requestSignature, emptyList())
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(notarisationPayload, testSerializationContext).bytes)

        val url = NotarisationPayloadSerializationTests::class.java.getResource(resource)!!
        val sc2 = url.readBytes()
        val deserializedNotarisationPayload = DeserializationInput(sf)
                .deserialize(SerializedBytes<NotarisationPayload>(sc2), testSerializationContext)

        assertEquals(1, deserializedNotarisationPayload.coreTransaction.inputs.size)
    }
}
