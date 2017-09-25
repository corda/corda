package net.corda.client.jackson

import com.fasterxml.jackson.databind.SerializationFeature
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.Amount
import net.corda.core.cordapp.CordappProvider
import net.corda.core.crypto.*
import net.corda.finance.USD
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.ALICE_PUBKEY
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.MINI_CORP
import net.corda.testing.TestDependencyInjectionBase
import net.corda.testing.contracts.DUMMY_PROGRAM_ID
import net.corda.testing.contracts.DummyContract
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertEquals

class JacksonSupportTest : TestDependencyInjectionBase() {
    companion object {
        val mapper = JacksonSupport.createNonRpcMapper()
    }

    private lateinit var services: ServiceHub
    private lateinit var cordappProvider: CordappProvider

    @Before
    fun setup() {
        services = mock()
        cordappProvider = mock()
        whenever(services.cordappProvider).thenReturn(cordappProvider)
    }

    @Test
    fun publicKeySerializingWorks() {
        val publicKey = generateKeyPair().public
        val serialized = mapper.writeValueAsString(publicKey)
        val parsedKey = mapper.readValue(serialized, EdDSAPublicKey::class.java)
        assertEquals(publicKey, parsedKey)
    }

    private class Dummy(val notional: Amount<Currency>)

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
    fun writeTransaction() {
        val attachmentRef = SecureHash.randomSHA256()
        whenever(cordappProvider.getContractAttachmentID(DUMMY_PROGRAM_ID))
            .thenReturn(attachmentRef)
        fun makeDummyTx(): SignedTransaction {
            val wtx = DummyContract.generateInitial(1, DUMMY_NOTARY, MINI_CORP.ref(1))
                .toWireTransaction(services)
            val signatures = TransactionSignature(
                    ByteArray(1),
                    ALICE_PUBKEY,
                    SignatureMetadata(
                            1,
                            Crypto.findSignatureScheme(ALICE_PUBKEY).schemeNumberID
                    )
            )
            return SignedTransaction(wtx, listOf(signatures))
        }

        val writer = mapper.writer()
        // We don't particularly care about the serialized format, just need to make sure it completes successfully.
        writer.writeValueAsString(makeDummyTx())
    }
}
