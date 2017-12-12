package net.corda.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.attestation.message.Message1
import net.corda.attestation.message.Message2
import org.apache.http.HttpStatus.*
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType.*
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1OutputStream
import org.bouncycastle.asn1.DLSequence
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyFactory
import java.security.KeyPair
import java.security.Signature
import java.security.interfaces.ECPublicKey

class PostMessage1IT {
    private companion object {
        private const val KEY_PASSWORD = "attestation"
        private const val SERVICE_KEYSTORE = "isv-svc.pfx"
        private val httpPort = Integer.getInteger("test.isv.httpPort")
    }
    private lateinit var httpClient: CloseableHttpClient
    private lateinit var serviceKeyPair: KeyPair
    private lateinit var keyPair: KeyPair
    private lateinit var ecPublicKey: ECPublicKey
    private lateinit var mapper: ObjectMapper
    private lateinit var crypto: Crypto

    @Rule
    @JvmField
    val cryptoProvider = CryptoProvider()

    @Rule
    @JvmField
    val keyStoreProvider = KeyStoreProvider(SERVICE_KEYSTORE, KEY_PASSWORD)

    @Before
    fun setup() {
        serviceKeyPair = keyStoreProvider.getKeyPair("isv-svc", KEY_PASSWORD)
        mapper = ObjectMapper()
        crypto = Crypto()
        keyPair = crypto.generateKeyPair()
        ecPublicKey = keyPair.public as ECPublicKey
        val httpRequestConfig: RequestConfig = RequestConfig.custom()
            .setConnectTimeout(20_000)
            .setSocketTimeout(5_000)
            .build()
        val httpSocketConfig: SocketConfig = SocketConfig.custom()
            .setSoReuseAddress(true)
            .setTcpNoDelay(true)
            .build()
        httpClient = HttpClients.custom()
            .setConnectionManager(BasicHttpClientConnectionManager().apply { socketConfig = httpSocketConfig })
            .setDefaultRequestConfig(httpRequestConfig)
            .build()
    }

    @After
    fun done() {
        httpClient.close()
    }

    @Test
    fun postMsg1() {
        val ecParameters = ecPublicKey.params
        val request = HttpPost("http://localhost:$httpPort/attest/msg1")
        val msg1 = Message1(ga = ecPublicKey.toLittleEndian(), platformGID = "00000000")
        request.entity = StringEntity(mapper.writeValueAsString(msg1), APPLICATION_JSON)
        val response = httpClient.execute(request).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }

        val msg2 = mapper.readValue(response, Message2::class.java)
        assertEquals("84D402C36BA9EF9B0A86EF1A9CC8CE4F", msg2.spid.toUpperCase())
        assertEquals(KEY_SIZE, msg2.signatureGbGa.size)
        assertEquals(80, msg2.revocationList.size)

        KeyFactory.getInstance("EC").generatePublic(msg2.gb.toBigEndianKeySpec(ecParameters))

        val asn1Signature = ByteArrayOutputStream().let { baos ->
            ASN1OutputStream(baos).apply {
                writeObject(DLSequence(ASN1EncodableVector().apply {
                    add(ASN1Integer(msg2.signatureGbGa.copyOf(KEY_SIZE / 2).reversedArray().toPositiveInteger()))
                    add(ASN1Integer(msg2.signatureGbGa.copyOfRange(KEY_SIZE / 2, KEY_SIZE).reversedArray().toPositiveInteger()))
                }))
            }
            baos.toByteArray()
        }
        val verified = Signature.getInstance("SHA256WithECDSA").let { verifier ->
            verifier.initVerify(serviceKeyPair.public)
            verifier.update(msg2.gb)
            verifier.update(msg1.ga)
            verifier.verify(asn1Signature)
        }
        assertTrue(verified)
    }

    @Test
    fun testEmptyRevocationList() {
        val request = HttpPost("http://localhost:$httpPort/attest/msg1")
        val msg1 = Message1(ga = ecPublicKey.toLittleEndian(), platformGID = "0000000b")
        request.entity = StringEntity(mapper.writeValueAsString(msg1), APPLICATION_JSON)
        val response = httpClient.execute(request).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }

        val msg2 = mapper.readValue(response, Message2::class.java)
        assertEquals(0, msg2.revocationList.size)
    }
}