package net.corda.node.services

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.NotarisationPayload
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals

class NotarisationPayloadSerializationTests {
    private val oldNotaryName = CordaX500Name("Old Notary", "Zurich", "CH")
    private val newNotaryName = CordaX500Name("New Notary", "Zurich", "CH")

    private lateinit var mockNet: MockNetwork
    private lateinit var oldNotaryNode: StartedMockNode
    private lateinit var clientNodeA: StartedMockNode
    private lateinit var clientNodeB: StartedMockNode
    private lateinit var newNotaryParty: Party
    private lateinit var oldNotaryParty: Party
    private lateinit var clientA: Party

    @Before
    fun setUp() {
        mockNet = MockNetwork(MockNetworkParameters(
                notarySpecs = listOf(MockNetworkNotarySpec(oldNotaryName), MockNetworkNotarySpec(newNotaryName)),
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP)
        ))
        clientNodeA = mockNet.createNode(MockNodeParameters(legalName = ALICE_NAME))
        clientNodeB = mockNet.createNode(MockNodeParameters(legalName = BOB_NAME))
        clientA = clientNodeA.info.singleIdentity()
        oldNotaryNode = mockNet.notaryNodes[0]
        oldNotaryParty = clientNodeA.services.networkMapCache.getNotary(oldNotaryName)!!
        newNotaryParty = clientNodeA.services.networkMapCache.getNotary(newNotaryName)!!
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

        val stateRef = StateRef(SecureHash.create("61A2ECDC1C54F31B7351F2C39F767D700A5658150C3E3C49F0458D487862A70D"), 0)

        // uncomment to recreate the data
        // This has to be run on a version of Corda that _has_ requiredSigningKeys on NotaryChangeWireTransaction
        // val networkParamsHash = SecureHash.randomSHA256()
        // val notaryChangeTx = NotaryChangeTransactionBuilder(listOf(stateRef), oldNotaryParty, newNotaryParty, networkParamsHash).build()
        // val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(Crypto.generateKeyPair().public, ByteArray(32)), 0)
        // val notarisationPayload = NotarisationPayload(notaryChangeTx, requestSignature)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(notarisationPayload, testSerializationContext).bytes)

        val url = NotaryChangeTests::class.java.getResource(resource)!!
        val sc2 = url.readBytes()
        val deserializedNotarisationPayload = DeserializationInput(sf)
                .deserialize(SerializedBytes<NotarisationPayload>(sc2), testSerializationContext)

        assertEquals(1, deserializedNotarisationPayload.coreTransaction.inputs.size)
        assertEquals(stateRef, deserializedNotarisationPayload.coreTransaction.inputs.first())
    }

    // Read in a serialized NotarisationPayload from 4.14+, with transactionSignatures
    // populated from https://github.com/corda/corda/pull/7991
    @Test(timeout = 300_000)
    fun deserializeNotarisationPayloadWithTransactionSignatures(){
        val resource = "NotarisationPayloadTest.payloadWithTransactionSignatures"
        val sf = testDefaultFactory()

        val stateRef = StateRef(SecureHash.create("36C3ECDC1C54F31B7351F2C39F767D700A5658150C3E3C49F0458D487862A70D"), 0)

        // uncomment to recreate the data.
        // This has to be run on a version of Corda that _has_ signers on NotarisationPayload
        // val networkParamsHash = SecureHash.randomSHA256()
        // val notaryChangeTx = NotaryChangeTransactionBuilder(listOf(stateRef), oldNotaryParty, newNotaryParty, networkParamsHash, emptySet()).build()
        // val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(Crypto.generateKeyPair().public, ByteArray(32)), 0)
        // val notarisationPayload = NotarisationPayload(notaryChangeTx, requestSignature, listOf(requestSignature.digitalSignature))
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(notarisationPayload, testSerializationContext).bytes)

        val url = NotaryChangeTests::class.java.getResource(resource)!!
        val sc2 = url.readBytes()
        val deserializedNotarisationPayload = DeserializationInput(sf)
                .deserialize(SerializedBytes<NotarisationPayload>(sc2), testSerializationContext)

        assertEquals(1, deserializedNotarisationPayload.coreTransaction.inputs.size)
        assertEquals(stateRef, deserializedNotarisationPayload.coreTransaction.inputs.first())
    }

    // Read in a serialized NotarisationPayload from 4.14+, with transactionSignatures
    // present, but not populated.
    @Test(timeout = 300_000)
    fun deserializeNotarisationPayloadWithEmptyTransactionSignatures(){
        val resource = "NotarisationPayloadTest.payloadWithEmptyTransactionSignatures"
        val sf = testDefaultFactory()

        val stateRef = StateRef(SecureHash.create("45D3ECDC1C54F3113351F2C39F767D700A5658150C3E3C49F0458D487862A70D"), 0)

        // uncomment to recreate the data
        // This has to be run on a version of Corda that _has_ requiredSigningKeys on NotaryChangeWireTransaction
        // val networkParamsHash = SecureHash.randomSHA256()
        // val notaryChangeTx = NotaryChangeTransactionBuilder(listOf(stateRef), oldNotaryParty, newNotaryParty, networkParamsHash, emptySet()).build()
        // val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(Crypto.generateKeyPair().public, ByteArray(32)), 0)
        // val notarisationPayload = NotarisationPayload(notaryChangeTx, requestSignature, emptyList())
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(notarisationPayload, testSerializationContext).bytes)

        val url = NotaryChangeTests::class.java.getResource(resource)!!
        val sc2 = url.readBytes()
        val deserializedNotarisationPayload = DeserializationInput(sf)
                .deserialize(SerializedBytes<NotarisationPayload>(sc2), testSerializationContext)

        assertEquals(1, deserializedNotarisationPayload.coreTransaction.inputs.size)
        assertEquals(stateRef, deserializedNotarisationPayload.coreTransaction.inputs.first())
    }
}
