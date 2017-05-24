package com.r3.corda.doorman.internal.persistence

import com.r3.corda.doorman.persistence.CertificateResponse
import com.r3.corda.doorman.persistence.CertificationRequestData
import com.r3.corda.doorman.persistence.DBCertificateRequestStorage
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.X509Utilities
import net.corda.core.crypto.X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME
import net.corda.node.utilities.configureDatabase
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.security.KeyPair
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DBCertificateRequestStorageTest {
    private val intermediateCA = X509Utilities.createSelfSignedCACert(X500Name("CN=Corda Node Intermediate CA"))
    private var closeDb: Closeable? = null
    private lateinit var storage: DBCertificateRequestStorage

    @Before
    fun startDb() {
        configureDatabase(makeTestDataSourceProperties()).apply {
            closeDb = first
            storage = DBCertificateRequestStorage(second)
        }
    }

    @After
    fun closeDb() {
        closeDb?.close()
    }

    @Test
    fun `valid request`() {
        val request = createRequest("LegalName").first
        val requestId = storage.saveRequest(request)
        assertNotNull(storage.getRequest(requestId)).apply {
            assertEquals(request.hostName, hostName)
            assertEquals(request.ipAddress, ipAddress)
            assertEquals(request.request, this.request)
        }
        assertThat(storage.getPendingRequestIds()).containsOnly(requestId)
    }

    @Test
    fun `approve request`() {
        val (request, keyPair) = createRequest("LegalName")
        // Add request to DB.
        val requestId = storage.saveRequest(request)
        // Pending request should equals to 1.
        assertEquals(1, storage.getPendingRequestIds().size)
        // Certificate should be empty.
        assertEquals(CertificateResponse.NotReady, storage.getResponse(requestId))
        // Store certificate to DB.
        approveRequest(requestId)
        // Check certificate is stored in DB correctly.
        val response = storage.getResponse(requestId) as CertificateResponse.Ready
        assertThat(response.certificate.publicKey).isEqualTo(keyPair.public)
        // Pending request should be empty.
        assertTrue(storage.getPendingRequestIds().isEmpty())
    }

    @Test
    fun `reject request`() {
        val requestId = storage.saveRequest(createRequest("BankA").first)
        storage.rejectRequest(requestId, "Because I said so!")
        assertThat(storage.getPendingRequestIds()).isEmpty()
        val response = storage.getResponse(requestId) as CertificateResponse.Unauthorised
        assertThat(response.message).isEqualTo("Because I said so!")
    }

    @Test
    fun `request with the same legal name as a pending request`() {
        val requestId1 = storage.saveRequest(createRequest("BankA").first)
        assertThat(storage.getPendingRequestIds()).containsOnly(requestId1)
        val requestId2 = storage.saveRequest(createRequest("BankA").first)
        assertThat(storage.getPendingRequestIds()).containsOnly(requestId1)
        val response2 = storage.getResponse(requestId2) as CertificateResponse.Unauthorised
        assertThat(response2.message).containsIgnoringCase("duplicate")
        // Make sure the first request is processed properly
        approveRequest(requestId1)
        assertThat(storage.getResponse(requestId1)).isInstanceOf(CertificateResponse.Ready::class.java)
    }

    @Test
    fun `request with the same legal name as a previously approved request`() {
        val requestId1 = storage.saveRequest(createRequest("BankA").first)
        approveRequest(requestId1)
        val requestId2 = storage.saveRequest(createRequest("BankA").first)
        val response2 = storage.getResponse(requestId2) as CertificateResponse.Unauthorised
        assertThat(response2.message).containsIgnoringCase("duplicate")
    }

    @Test
    fun `request with the same legal name as a previously rejected request`() {
        val requestId1 = storage.saveRequest(createRequest("BankA").first)
        storage.rejectRequest(requestId1, "Because I said so!")
        val requestId2 = storage.saveRequest(createRequest("BankA").first)
        assertThat(storage.getPendingRequestIds()).containsOnly(requestId2)
        approveRequest(requestId2)
        assertThat(storage.getResponse(requestId2)).isInstanceOf(CertificateResponse.Ready::class.java)
    }

    @Test
    fun `request with equals symbol in legal name`() {
        val requestId = storage.saveRequest(createRequest("Bank\\=A").first)
        assertThat(storage.getPendingRequestIds()).isEmpty()
        val response = storage.getResponse(requestId) as CertificateResponse.Unauthorised
        assertThat(response.message).contains("=")
    }

    @Test
    fun `request with comma in legal name`() {
        val requestId = storage.saveRequest(createRequest("Bank\\,A").first)
        assertThat(storage.getPendingRequestIds()).isEmpty()
        val response = storage.getResponse(requestId) as CertificateResponse.Unauthorised
        assertThat(response.message).contains(",")
    }

    private fun createRequest(legalName: String): Pair<CertificationRequestData, KeyPair> {
        val keyPair = Crypto.generateKeyPair(DEFAULT_TLS_SIGNATURE_SCHEME)
        val request = CertificationRequestData(
                "hostname",
                "0.0.0.0",
                X509Utilities.createCertificateSigningRequest(X500Name("CN=$legalName"), keyPair))
        return Pair(request, keyPair)
    }

    private fun approveRequest(requestId: String) {
        storage.approveRequest(requestId) {
            JcaPKCS10CertificationRequest(request).run {
                X509Utilities.createTlsServerCert(
                        subject,
                        publicKey,
                        intermediateCA,
                        if (ipAddress == hostName) listOf() else listOf(hostName),
                        listOf(ipAddress))
            }
        }
    }
}
