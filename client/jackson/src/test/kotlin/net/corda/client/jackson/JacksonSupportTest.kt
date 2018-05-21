package net.corda.client.jackson

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.BinaryNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.convertValue
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.client.jackson.internal.valueAs
import net.corda.core.contracts.Amount
import net.corda.core.cordapp.CordappProvider
import net.corda.core.crypto.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.*
import net.corda.finance.USD
import net.corda.nodeapi.internal.crypto.x509Certificates
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
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.math.BigInteger
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.util.*
import javax.security.auth.x500.X500Principal
import kotlin.collections.ArrayList

@RunWith(Parameterized::class)
class JacksonSupportTest(@Suppress("unused") private val name: String, factory: JsonFactory) {
    private companion object {
        val SEED: BigInteger = BigInteger.valueOf(20170922L)
        val ALICE_PUBKEY = TestIdentity(ALICE_NAME, 70).publicKey
        val BOB_PUBKEY = TestIdentity(BOB_NAME, 70).publicKey
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val MINI_CORP = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))

        @Parameters(name = "{0}")
        @JvmStatic
        fun factories() = arrayOf(arrayOf("JSON", JsonFactory()), arrayOf("YAML", YAMLFactory()))
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val partyObjectMapper = TestPartyObjectMapper()
    private val mapper = JacksonSupport.createPartyObjectMapper(partyObjectMapper, factory)

    private lateinit var services: ServiceHub
    private lateinit var cordappProvider: CordappProvider

    @Before
    fun setup() {
        services = rigorousMock()
        cordappProvider = rigorousMock()
        doReturn(cordappProvider).whenever(services).cordappProvider
    }

    @Test
    fun `Amount(Currency) serialization`() {
        assertThat(mapper.valueToTree<TextNode>(Amount.parseCurrency("£25000000")).textValue()).isEqualTo("25000000.00 GBP")
        assertThat(mapper.valueToTree<TextNode>(Amount.parseCurrency("$250000")).textValue()).isEqualTo("250000.00 USD")
    }

    @Test
    fun `Amount(Currency) deserialization`() {
        val old = mapOf(
                "quantity" to 2500000000,
                "token" to "USD"
        )
        assertThat(mapper.convertValue<Amount<Currency>>(old)).isEqualTo(Amount(2_500_000_000, USD))
        assertThat(mapper.convertValue<Amount<Currency>>(TextNode("$25000000"))).isEqualTo(Amount(2_500_000_000, USD))
    }

    @Test
    fun ByteSequence() {
        val byteSequence: ByteSequence = OpaqueBytes.of(1, 2, 3, 4).subSequence(0, 2)
        val json = mapper.valueToTree<BinaryNode>(byteSequence)
        assertThat(json.binaryValue()).containsExactly(1, 2)
        assertThat(json.asText()).isEqualTo(byteArrayOf(1, 2).toBase64())
        assertThat(mapper.convertValue<ByteSequence>(json)).isEqualTo(byteSequence)
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
    fun SerializedBytes() {
        val data = TestData(BOB_NAME, "Summary", SubTestData(1234))
        val serializedBytes = data.serialize()
        val json = mapper.valueToTree<ObjectNode>(serializedBytes)
        println(mapper.writeValueAsString(json))
        assertThat(json["class"].textValue()).isEqualTo(TestData::class.java.name)
        assertThat(json["deserialized"].valueAs<TestData>(mapper)).isEqualTo(data)
        // Check that the entire JSON object can be converted back to the same SerializedBytes
        assertThat(mapper.convertValue<SerializedBytes<*>>(json)).isEqualTo(serializedBytes)
        assertThat(mapper.convertValue<SerializedBytes<*>>(BinaryNode(serializedBytes.bytes))).isEqualTo(serializedBytes)
    }

    // This is the class that was used to serialise the message for the test below. It's commented out so that it's no
    // longer on the classpath.
//    @CordaSerializable
//    data class ClassNotOnClasspath(val name: CordaX500Name, val value: Int)

    @Test
    fun `SerializedBytes of class not on classpath`() {
        // The contents of the file were written out as follows:
//        ClassNotOnClasspath(BOB_NAME, 54321).serialize().open().copyTo("build" / "class-not-on-classpath-data")

        val serializedBytes = SerializedBytes<Any>(javaClass.getResource("class-not-on-classpath-data").readBytes())
        val json = mapper.valueToTree<ObjectNode>(serializedBytes)
        println(mapper.writeValueAsString(json))
        assertThat(json["class"].textValue()).isEqualTo("net.corda.client.jackson.JacksonSupportTest\$ClassNotOnClasspath")
        assertThat(json["deserialized"].valueAs<Map<*, *>>(mapper)).isEqualTo(mapOf(
                "name" to BOB_NAME.toString(),
                "value" to 54321
        ))
        assertThat(mapper.convertValue<SerializedBytes<*>>(BinaryNode(serializedBytes.bytes))).isEqualTo(serializedBytes)
    }

    @Test
    fun DigitalSignature() {
        val digitalSignature = DigitalSignature(secureRandomBytes(128))
        val json = mapper.valueToTree<BinaryNode>(digitalSignature)
        assertThat(json.binaryValue()).isEqualTo(digitalSignature.bytes)
        assertThat(json.asText()).isEqualTo(digitalSignature.bytes.toBase64())
        assertThat(mapper.convertValue<DigitalSignature>(json)).isEqualTo(digitalSignature)
    }

    @Test
    fun `DigitalSignature WithKey`() {
        val digitalSignature = DigitalSignature.WithKey(BOB_PUBKEY, secureRandomBytes(128))
        val json = mapper.valueToTree<ObjectNode>(digitalSignature)
        val (by, bytes) = json.assertHasOnlyFields("by", "bytes")
        assertThat(by.valueAs<PublicKey>(mapper)).isEqualTo(BOB_PUBKEY)
        assertThat(bytes.binaryValue()).isEqualTo(digitalSignature.bytes)
        assertThat(mapper.convertValue<DigitalSignature.WithKey>(json)).isEqualTo(digitalSignature)
    }

    @Test
    fun DigitalSignatureWithCert() {
        val digitalSignature = DigitalSignatureWithCert(MINI_CORP.identity.certificate, secureRandomBytes(128))
        val json = mapper.valueToTree<ObjectNode>(digitalSignature)
        val (by, bytes) = json.assertHasOnlyFields("by", "bytes")
        assertThat(by.valueAs<X509Certificate>(mapper)).isEqualTo(MINI_CORP.identity.certificate)
        assertThat(bytes.binaryValue()).isEqualTo(digitalSignature.bytes)
        assertThat(mapper.convertValue<DigitalSignatureWithCert>(json)).isEqualTo(digitalSignature)
    }

    @Test
    fun TransactionSignature() {
        val metadata = SignatureMetadata(1, 1)
        val transactionSignature = TransactionSignature(secureRandomBytes(128), BOB_PUBKEY, metadata)
        val json = mapper.valueToTree<ObjectNode>(transactionSignature)
        val (bytes, by, signatureMetadata, partialMerkleTree) = json.assertHasOnlyFields(
                "bytes",
                "by",
                "signatureMetadata",
                "partialMerkleTree"
        )
        assertThat(bytes.binaryValue()).isEqualTo(transactionSignature.bytes)
        assertThat(by.valueAs<PublicKey>(mapper)).isEqualTo(BOB_PUBKEY)
        assertThat(signatureMetadata.valueAs<SignatureMetadata>(mapper)).isEqualTo(metadata)
        assertThat(partialMerkleTree.isNull).isTrue()
        assertThat(mapper.convertValue<TransactionSignature>(json)).isEqualTo(transactionSignature)
    }

    // TODO Add test for PartialMerkleTree

    @Test
    fun SignedTransaction() {
        val attachmentRef = SecureHash.randomSHA256()
        doReturn(attachmentRef).whenever(cordappProvider).getContractAttachmentID(DummyContract.PROGRAM_ID)
        doReturn(testNetworkParameters()).whenever(services).networkParameters

        val stx = makeDummyStx()
        val json = mapper.valueToTree<ObjectNode>(stx)
        println(mapper.writeValueAsString(json))
        val (txBits, signatures) = json.assertHasOnlyFields("txBits", "signatures")
        assertThat(txBits.binaryValue()).isEqualTo(stx.txBits.bytes)
        val sigs = signatures.elements().asSequence().map { it.valueAs<TransactionSignature>(mapper) }.toList()
        assertThat(sigs).isEqualTo(stx.sigs)
        assertThat(mapper.convertValue<SignedTransaction>(json)).isEqualTo(stx)
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
        val anonymousParty = AnonymousParty(ALICE_PUBKEY)
        val json = mapper.valueToTree<TextNode>(anonymousParty)
        assertThat(json.textValue()).isEqualTo(ALICE_PUBKEY.toBase58String())
        assertThat(mapper.convertValue<AnonymousParty>(json)).isEqualTo(anonymousParty)
    }

    @Test
    fun `PartyAndCertificate serialisation`() {
        val json = mapper.valueToTree<ObjectNode>(MINI_CORP.identity)
        val (name, owningKey) = json.assertHasOnlyFields("name", "owningKey")
        assertThat(name.valueAs<CordaX500Name>(mapper)).isEqualTo(MINI_CORP.name)
        assertThat(owningKey.valueAs<PublicKey>(mapper)).isEqualTo(MINI_CORP.publicKey)
    }

    @Test
    fun `NodeInfo serialisation`() {
        val (nodeInfo) = createNodeInfoAndSigned(ALICE_NAME)
        val json = mapper.valueToTree<ObjectNode>(nodeInfo)
        val (addresses, legalIdentitiesAndCerts, platformVersion, serial) = json.assertHasOnlyFields(
                "addresses",
                "legalIdentitiesAndCerts",
                "platformVersion",
                "serial"
        )
        addresses.run {
            assertThat(this).hasSize(1)
            assertThat(this[0].valueAs<NetworkHostAndPort>(mapper)).isEqualTo(nodeInfo.addresses[0])
        }
        legalIdentitiesAndCerts.run {
            assertThat(this).hasSize(1)
            assertThat(this[0]["name"].valueAs<CordaX500Name>(mapper)).isEqualTo(ALICE_NAME)
        }
        assertThat(platformVersion.intValue()).isEqualTo(nodeInfo.platformVersion)
        assertThat(serial.longValue()).isEqualTo(nodeInfo.serial)
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

    @Test
    fun CertPath() {
        val certPath = MINI_CORP.identity.certPath
        val json = mapper.valueToTree<ObjectNode>(certPath)
        println(mapper.writeValueAsString(json))
        val (type, certificates) = json.assertHasOnlyFields("type", "certificates")
        assertThat(type.textValue()).isEqualTo(certPath.type)
        certificates.run {
            val serialNumbers = elements().asSequence().map { it["serialNumber"].bigIntegerValue() }.toList()
            assertThat(serialNumbers).isEqualTo(certPath.x509Certificates.map { it.serialNumber })
        }
        assertThat(mapper.convertValue<CertPath>(json).encoded).isEqualTo(certPath.encoded)
    }

    @Test
    fun X509Certificate() {
        val cert: X509Certificate = MINI_CORP.identity.certificate
        val json = mapper.valueToTree<ObjectNode>(cert)
        println(mapper.writeValueAsString(json))
        assertThat(json["serialNumber"].bigIntegerValue()).isEqualTo(cert.serialNumber)
        assertThat(json["issuer"].valueAs<X500Principal>(mapper)).isEqualTo(cert.issuerX500Principal)
        assertThat(json["subject"].valueAs<X500Principal>(mapper)).isEqualTo(cert.subjectX500Principal)
        assertThat(json["publicKey"].valueAs<PublicKey>(mapper)).isEqualTo(cert.publicKey)
        assertThat(json["notAfter"].valueAs<Date>(mapper)).isEqualTo(cert.notAfter)
        assertThat(json["notBefore"].valueAs<Date>(mapper)).isEqualTo(cert.notBefore)
        assertThat(json["encoded"].binaryValue()).isEqualTo(cert.encoded)
        assertThat(mapper.convertValue<X509Certificate>(json).encoded).isEqualTo(cert.encoded)
    }

    @Test
    fun X500Principal() {
        testToStringSerialisation(X500Principal("CN=Common,L=London,O=Org,C=UK"))
    }

    @Test
    fun `@CordaSerializable class which has non-c'tor properties`() {
        val data = NonCtorPropertiesData(4434)
        val json = mapper.valueToTree<ObjectNode>(data)
        val (value) = json.assertHasOnlyFields("value")
        assertThat(value.intValue()).isEqualTo(4434)
        assertThat(mapper.convertValue<NonCtorPropertiesData>(json)).isEqualTo(data)
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

    private fun JsonNode.assertHasOnlyFields(vararg fieldNames: String): List<JsonNode> {
        assertThat(fieldNames()).containsOnly(*fieldNames)
        return fieldNames.map { this[it] }
    }

    @CordaSerializable
    private data class TestData(val name: CordaX500Name, val summary: String, val subData: SubTestData)

    @CordaSerializable
    private data class SubTestData(val value: Int)

    @CordaSerializable
    private data class NonCtorPropertiesData(val value: Int) {
        @Suppress("unused")
        val nonCtor: Int get() = value
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
