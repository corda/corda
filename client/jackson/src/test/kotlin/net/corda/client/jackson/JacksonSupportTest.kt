package net.corda.client.jackson

import com.fasterxml.jackson.databind.SerializationFeature
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.Amount
import net.corda.core.cordapp.CordappProvider
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.USD
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.security.PublicKey
import java.util.*
import kotlin.test.assertEquals

class JacksonSupportTest {
    private companion object {
        val SEED = BigInteger.valueOf(20170922L)!!
        val mapper = JacksonSupport.createNonRpcMapper()
        val ALICE_PUBKEY = TestIdentity(ALICE_NAME, 70).publicKey
        val BOB_PUBKEY = TestIdentity(BOB_NAME, 70).publicKey
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val MINI_CORP = TestIdentity(CordaX500Name("MiniCorp", "London", "GB")).party
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private lateinit var services: ServiceHub
    private lateinit var cordappProvider: CordappProvider

    @Before
    fun setup() {
        services = rigorousMock()
        cordappProvider = rigorousMock()
        doReturn(cordappProvider).whenever(services).cordappProvider
    }

    @Test
    fun `should serialize Composite keys`() {
        val expected = "\"MIHAMBUGE2mtoq+J1bjir/ONk6yd5pab0FoDgaYAMIGiAgECMIGcMDIDLQAwKjAFBgMrZXADIQAgIX1QlJRgaLlD0ttLlJF5kNqT/7P7QwCvrWc9+/248gIBATAyAy0AMCowBQYDK2VwAyEAqS0JPGlzdviBZjB9FaNY+w6cVs3/CQ2A5EimE9Lyng4CAQEwMgMtADAqMAUGAytlcAMhALq4GG0gBQZIlaKE6ucooZsuoKUbH4MtGSmA6cwj136+AgEB\""
        val innerKeys = (1..3).map { i ->
            Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, SEED.plus(BigInteger.valueOf(i.toLong()))).public
        }
        // Build a 2 of 3 composite key
        val publicKey = CompositeKey.Builder().let {
            innerKeys.forEach { key -> it.addKey(key, 1) }
            it.build(2)
        }
        val serialized = mapper.writeValueAsString(publicKey)
        assertEquals(expected, serialized)
        val parsedKey = mapper.readValue(serialized, PublicKey::class.java)
        assertEquals(publicKey, parsedKey)
    }

    private class Dummy(val notional: Amount<Currency>)

    @Test
    fun `should serialize EdDSA keys`() {
        val expected = "\"MCowBQYDK2VwAyEACFTgLk1NOqYXAfxLoR7ctSbZcl9KMXu58Mq31Kv1Dwk=\""
        val publicKey = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, SEED).public
        val serialized = mapper.writeValueAsString(publicKey)
        assertEquals(expected, serialized)
        val parsedKey = mapper.readValue(serialized, PublicKey::class.java)
        assertEquals(publicKey, parsedKey)
    }

    @Test
    fun readAmount() {
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
    fun writeAmount() {
        val writer = mapper.writer().without(SerializationFeature.INDENT_OUTPUT)
        assertEquals("""{"notional":"25000000.00 USD"}""", writer.writeValueAsString(Dummy(Amount.parseCurrency("$25000000"))))
    }

    @Test
    fun `wire transaction can be serialized and de-serialized`() {
        val attachmentRef = SecureHash.randomSHA256()
        doReturn(attachmentRef).whenever(cordappProvider).getContractAttachmentID(DummyContract.PROGRAM_ID)
        doReturn(testNetworkParameters()).whenever(services).networkParameters

        val writer = mapper.writer()
        val transaction = makeDummyTx()
        val json = writer.writeValueAsString(transaction)

        val deserializedTransaction = mapper.readValue(json, SignedTransaction::class.java)

        assertThat(deserializedTransaction).isEqualTo(transaction)
    }

    private fun makeDummyTx(): SignedTransaction {
        val wtx = DummyContract.generateInitial(1, DUMMY_NOTARY, MINI_CORP.ref(1))
                .toWireTransaction(services)
        val signatures = listOf(
                TransactionSignature(ByteArray(1), ALICE_PUBKEY, SignatureMetadata(1, Crypto.findSignatureScheme(ALICE_PUBKEY).schemeNumberID)),
                TransactionSignature(ByteArray(1), BOB_PUBKEY, SignatureMetadata(1, Crypto.findSignatureScheme(BOB_PUBKEY).schemeNumberID))
        )
        return SignedTransaction(wtx, signatures)
    }
}
