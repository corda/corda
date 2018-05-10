/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.jackson

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BinaryNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.Amount
import net.corda.core.cordapp.CordappProvider
import net.corda.core.crypto.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.toBase58String
import net.corda.core.utilities.toBase64
import net.corda.finance.USD
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.*
import net.corda.testing.internal.createNodeInfoAndSigned
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.security.PublicKey
import java.util.*
import kotlin.collections.ArrayList
import kotlin.test.assertEquals

class JacksonSupportTest {
    private companion object {
        val SEED: BigInteger = BigInteger.valueOf(20170922L)
        val ALICE_PUBKEY = TestIdentity(ALICE_NAME, 70).publicKey
        val BOB_PUBKEY = TestIdentity(BOB_NAME, 70).publicKey
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val MINI_CORP = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val partyObjectMapper = TestPartyObjectMapper()
    private val mapper = JacksonSupport.createPartyObjectMapper(partyObjectMapper)

    private lateinit var services: ServiceHub
    private lateinit var cordappProvider: CordappProvider

    @Before
    fun setup() {
        services = rigorousMock()
        cordappProvider = rigorousMock()
        doReturn(cordappProvider).whenever(services).cordappProvider
    }

    private class Dummy(val notional: Amount<Currency>)

    @Test
    fun `read Amount`() {
        val oldJson = """
            {
              "notional": {
                  "quantity": 2500000000,
                  "token": "USD"
              }
            }
        """
        val newJson = """ { "notional" : "$25000000" } """

        assertEquals(Amount(2500000000L, USD), mapper.readValue(newJson, Dummy::class.java).notional)
        assertEquals(Amount(2500000000L, USD), mapper.readValue(oldJson, Dummy::class.java).notional)
    }

    @Test
    fun `write Amount`() {
        val writer = mapper.writer().without(SerializationFeature.INDENT_OUTPUT)
        assertEquals("""{"notional":"25000000.00 GBP"}""", writer.writeValueAsString(Dummy(Amount.parseCurrency("£25000000"))))
        assertEquals("""{"notional":"250000.00 USD"}""", writer.writeValueAsString(Dummy(Amount.parseCurrency("$250000"))))
    }

    @Test
    fun SignedTransaction() {
        val attachmentRef = SecureHash.randomSHA256()
        doReturn(attachmentRef).whenever(cordappProvider).getContractAttachmentID(DummyContract.PROGRAM_ID)
        doReturn(testNetworkParameters()).whenever(services).networkParameters

        val writer = mapper.writer()
        val stx = makeDummyStx()
        val json = writer.writeValueAsString(stx)

        val deserializedTransaction = mapper.readValue(json, SignedTransaction::class.java)

        assertThat(deserializedTransaction).isEqualTo(stx)
    }

    @Test
    fun OpaqueBytes() {
        val opaqueBytes = OpaqueBytes(secureRandomBytes(128))
        val json = mapper.valueToTree<BinaryNode>(opaqueBytes)
        assertThat(json.binaryValue()).isEqualTo(opaqueBytes.bytes)
        assertThat(json.asText()).isEqualTo(opaqueBytes.bytes.toBase64())
        assertThat(mapper.convertValue<OpaqueBytes>(json)).isEqualTo(opaqueBytes)
    }

    @Test
    fun CordaX500Name() {
        testToStringSerialisation(CordaX500Name(commonName = "COMMON", organisationUnit = "ORG UNIT", organisation = "ORG", locality = "NYC", state = "NY", country = "US"))
    }

    @Test
    fun `SecureHash SHA256`() {
        testToStringSerialisation(SecureHash.randomSHA256())
    }

    @Test
    fun NetworkHostAndPort() {
        testToStringSerialisation(NetworkHostAndPort("localhost", 9090))
    }

    @Test
    fun UUID() {
        testToStringSerialisation(UUID.randomUUID())
    }

    @Test
    fun `Date is treated as Instant`() {
        val date = Date()
        val json = mapper.valueToTree<TextNode>(date)
        assertThat(json.textValue()).isEqualTo(date.toInstant().toString())
        assertThat(mapper.convertValue<Date>(json)).isEqualTo(date)
    }

    @Test
    fun `Party serialization`() {
        val json = mapper.valueToTree<TextNode>(MINI_CORP.party)
        assertThat(json.textValue()).isEqualTo(MINI_CORP.name.toString())
    }

    @Test
    fun `Party deserialization on full name`() {
        fun convertToParty() = mapper.convertValue<Party>(TextNode(MINI_CORP.name.toString()))

        assertThatThrownBy { convertToParty() }

        partyObjectMapper.identities += MINI_CORP.party
        assertThat(convertToParty()).isEqualTo(MINI_CORP.party)
    }

    @Test
    fun `Party deserialization on part of name`() {
        fun convertToParty() = mapper.convertValue<Party>(TextNode(MINI_CORP.name.organisation))

        assertThatThrownBy { convertToParty() }

        partyObjectMapper.identities += MINI_CORP.party
        assertThat(convertToParty()).isEqualTo(MINI_CORP.party)
    }

    @Test
    fun `Party deserialization on public key`() {
        fun convertToParty() = mapper.convertValue<Party>(TextNode(MINI_CORP.publicKey.toBase58String()))

        assertThatThrownBy { convertToParty() }

        partyObjectMapper.identities += MINI_CORP.party
        assertThat(convertToParty()).isEqualTo(MINI_CORP.party)
    }

    @Test
    fun PublicKey() {
        val json = mapper.valueToTree<TextNode>(MINI_CORP.publicKey)
        assertThat(json.textValue()).isEqualTo(MINI_CORP.publicKey.toBase58String())
        assertThat(mapper.convertValue<PublicKey>(json)).isEqualTo(MINI_CORP.publicKey)
    }

    @Test
    fun `EdDSA public key`() {
        val publicKey = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, SEED).public
        val json = mapper.valueToTree<TextNode>(publicKey)
        assertThat(json.textValue()).isEqualTo(publicKey.toBase58String())
        assertThat(mapper.convertValue<PublicKey>(json)).isEqualTo(publicKey)
    }

    @Test
    fun CompositeKey() {
        val innerKeys = (1..3).map { i ->
            Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, SEED + i.toBigInteger()).public
        }
        // Build a 2 of 3 composite key
        val publicKey = CompositeKey.Builder().let {
            innerKeys.forEach { key -> it.addKey(key, 1) }
            it.build(2)
        }
        val json = mapper.valueToTree<TextNode>(publicKey)
        assertThat(json.textValue()).isEqualTo(publicKey.toBase58String())
        assertThat(mapper.convertValue<CompositeKey>(json)).isEqualTo(publicKey)
    }

    @Test
    fun AnonymousParty() {
        val anon = AnonymousParty(ALICE_PUBKEY)
        val json = mapper.valueToTree<TextNode>(anon)
        assertThat(json.textValue()).isEqualTo(ALICE_PUBKEY.toBase58String())
        assertThat(mapper.convertValue<AnonymousParty>(json)).isEqualTo(anon)
    }

    @Test
    fun `PartyAndCertificate serialisation`() {
        val json = mapper.valueToTree<ObjectNode>(MINI_CORP.identity)
        assertThat(json.fieldNames()).containsOnly("name", "owningKey")
        assertThat(mapper.convertValue<CordaX500Name>(json["name"])).isEqualTo(MINI_CORP.name)
        assertThat(mapper.convertValue<PublicKey>(json["owningKey"])).isEqualTo(MINI_CORP.publicKey)
    }

    @Test
    fun `NodeInfo serialisation`() {
        val (nodeInfo) = createNodeInfoAndSigned(ALICE_NAME)
        val json = mapper.valueToTree<ObjectNode>(nodeInfo)
        assertThat(json.fieldNames()).containsOnly("addresses", "legalIdentitiesAndCerts", "platformVersion", "serial")
        val address = (json["addresses"] as ArrayNode).also { assertThat(it).hasSize(1) }[0]
        assertThat(mapper.convertValue<NetworkHostAndPort>(address)).isEqualTo(nodeInfo.addresses[0])
        val identity = (json["legalIdentitiesAndCerts"] as ArrayNode).also { assertThat(it).hasSize(1) }[0]
        assertThat(mapper.convertValue<CordaX500Name>(identity["name"])).isEqualTo(ALICE_NAME)
        assertThat(mapper.convertValue<Int>(json["platformVersion"])).isEqualTo(nodeInfo.platformVersion)
        assertThat(mapper.convertValue<Long>(json["serial"])).isEqualTo(nodeInfo.serial)
    }

    @Test
    fun `NodeInfo deserialisation on name`() {
        val (nodeInfo) = createNodeInfoAndSigned(ALICE_NAME)

        fun convertToNodeInfo() = mapper.convertValue<NodeInfo>(TextNode(ALICE_NAME.toString()))

        assertThatThrownBy { convertToNodeInfo() }

        partyObjectMapper.identities += nodeInfo.legalIdentities
        partyObjectMapper.nodes += nodeInfo
        assertThat(convertToNodeInfo()).isEqualTo(nodeInfo)
    }

    @Test
    fun `NodeInfo deserialisation on public key`() {
        val (nodeInfo) = createNodeInfoAndSigned(ALICE_NAME)

        fun convertToNodeInfo() = mapper.convertValue<NodeInfo>(TextNode(nodeInfo.legalIdentities[0].owningKey.toBase58String()))

        assertThatThrownBy { convertToNodeInfo() }

        partyObjectMapper.identities += nodeInfo.legalIdentities
        partyObjectMapper.nodes += nodeInfo
        assertThat(convertToNodeInfo()).isEqualTo(nodeInfo)
    }

    private fun makeDummyStx(): SignedTransaction {
        val wtx = DummyContract.generateInitial(1, DUMMY_NOTARY, MINI_CORP.ref(1))
                .toWireTransaction(services)
        val signatures = listOf(
                TransactionSignature(ByteArray(1), ALICE_PUBKEY, SignatureMetadata(1, Crypto.findSignatureScheme(ALICE_PUBKEY).schemeNumberID)),
                TransactionSignature(ByteArray(1), BOB_PUBKEY, SignatureMetadata(1, Crypto.findSignatureScheme(BOB_PUBKEY).schemeNumberID))
        )
        return SignedTransaction(wtx, signatures)
    }

    private inline fun <reified T : Any> testToStringSerialisation(value: T) {
        val json = mapper.valueToTree<TextNode>(value)
        assertThat(json.textValue()).isEqualTo(value.toString())
        assertThat(mapper.convertValue<T>(json)).isEqualTo(value)
    }

    private class TestPartyObjectMapper : JacksonSupport.PartyObjectMapper {
        val identities = ArrayList<Party>()
        val nodes = ArrayList<NodeInfo>()
        override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? {
            return identities.find { it.name == name }
        }
        override fun partyFromKey(owningKey: PublicKey): Party? {
            return identities.find { it.owningKey == owningKey }
        }
        override fun partiesFromName(query: String): Set<Party> {
            return identities.filter { query in it.name.toString() }.toSet()
        }
        override fun nodeInfoFromParty(party: AbstractParty): NodeInfo? {
            return nodes.find { party in it.legalIdentities }
        }
    }
}
