package com.r3.corda.doorman.internal.persistence

import com.r3.corda.doorman.buildCertPath
import com.r3.corda.doorman.persistence.DoormanSchemaService
import com.r3.corda.doorman.persistence.DBCertificateRequestStorage
import com.r3.corda.doorman.persistence.RequestStatus
import com.r3.corda.doorman.toX509Certificate
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.X509Utilities
import net.corda.node.utilities.configureDatabase
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.util.*
import kotlin.test.*

class DBCertificateRequestStorageTest {
    private lateinit var storage: DBCertificateRequestStorage
    private lateinit var persistence: CordaPersistence

    @Before
    fun startDb() {
        persistence = configureDatabase(makeTestDataSourceProperties(), makeTestDatabaseProperties(), { DoormanSchemaService() }, createIdentityService = { throw UnsupportedOperationException() })
        storage = DBCertificateRequestStorage(persistence)
    }

    @After
    fun closeDb() {
        persistence.close()
    }

    @Test
    fun `valid request`() {
        val request = createRequest("LegalName").first
        val requestId = storage.saveRequest(request)
        assertNotNull(storage.getRequest(requestId)).apply {
            assertEquals(request, PKCS10CertificationRequest(this.request))
        }
        assertThat(storage.getRequests(RequestStatus.New).map { it.requestId }).containsOnly(requestId)
    }

    @Test
    fun `approve request`() {
        val (request, _) = createRequest("LegalName")
        // Add request to DB.
        val requestId = storage.saveRequest(request)
        // Pending request should equals to 1.
        assertEquals(1, storage.getRequests(RequestStatus.New).size)
        // Certificate should be empty.
        assertNull(storage.getRequest(requestId)!!.certificateData)
        // Store certificate to DB.
        val result = storage.approveRequest(requestId)
        // Check request request has been approved
        assertTrue(result)
        // Check request is not ready yet.
        // assertTrue(storage.getResponse(requestId) is CertificateResponse.NotReady)
        // New request should be empty.
        assertTrue(storage.getRequests(RequestStatus.New).isEmpty())
    }

    @Test
    fun `approve request ignores subsequent approvals`() {
        // Given
        val (request, _) = createRequest("LegalName")
        // Add request to DB.
        val requestId = storage.saveRequest(request)
        storage.approveRequest(requestId)

        // When subsequent approval is performed
        val result = storage.approveRequest(requestId)
        // Then check request has not been approved
        assertFalse(result)
    }

    @Test
    fun `sign request`() {
        val (csr, _) = createRequest("LegalName")
        // Add request to DB.
        val requestId = storage.saveRequest(csr)
        // New request should equals to 1.
        assertEquals(1, storage.getRequests(RequestStatus.New).size)
        // Certificate should be empty.
        assertNull(storage.getRequest(requestId)!!.certificateData)
        // Store certificate to DB.
        storage.approveRequest(requestId)
        // Check request is not ready yet.
        assertEquals(RequestStatus.Approved, storage.getRequest(requestId)!!.status)
        // New request should be empty.
        assertTrue(storage.getRequests(RequestStatus.New).isEmpty())
        // Sign certificate
        storage.putCertificatePath(requestId, JcaPKCS10CertificationRequest(csr).run {
                val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
                val rootCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Corda Node Root CA", locality = "London", organisation = "R3 LTD", country = "GB"), rootCAKey)
                val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
                val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey, X500Name("CN=Corda Node Intermediate CA,L=London"), intermediateCAKey.public)
                val ourCertificate = X509Utilities.createCertificate(CertificateType.TLS, intermediateCACert, intermediateCAKey, subject, publicKey).toX509Certificate()
                buildCertPath(ourCertificate, intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        })
        // Check request is ready
        assertNotNull(storage.getRequest(requestId)!!.certificateData)
    }

    @Test
    fun `sign request ignores subsequent sign requests`() {
        val (csr, _) = createRequest("LegalName")
        // Add request to DB.
        val requestId = storage.saveRequest(csr)
        // Store certificate to DB.
        storage.approveRequest(requestId)
        storage.putCertificatePath(requestId, JcaPKCS10CertificationRequest(csr).run {
                val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
                val rootCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Corda Node Root CA", locality = "London", organisation = "R3 LTD", country = "GB"), rootCAKey)
                val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
                val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey, X500Name("CN=Corda Node Intermediate CA,L=London"), intermediateCAKey.public)
                val ourCertificate = X509Utilities.createCertificate(CertificateType.TLS, intermediateCACert, intermediateCAKey, subject, publicKey).toX509Certificate()
                buildCertPath(ourCertificate, intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        })
        // Sign certificate
        // When subsequent signature requested
        assertFailsWith(IllegalArgumentException::class){
            storage.putCertificatePath(requestId, JcaPKCS10CertificationRequest(csr).run {
                val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
                val rootCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Corda Node Root CA", locality = "London", organisation = "R3 LTD", country = "GB"), rootCAKey)
                val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
                val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey, X500Name("CN=Corda Node Intermediate CA,L=London"), intermediateCAKey.public)
                val ourCertificate = X509Utilities.createCertificate(CertificateType.TLS, intermediateCACert, intermediateCAKey, subject, publicKey).toX509Certificate()
                buildCertPath(ourCertificate, intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
            })
        }
    }

    @Test
    fun `reject request`() {
        val requestId = storage.saveRequest(createRequest("BankA").first)
        storage.rejectRequest(requestId, rejectReason = "Because I said so!")
        assertThat(storage.getRequests(RequestStatus.New)).isEmpty()
        assertThat(storage.getRequest(requestId)!!.rejectReason).isEqualTo("Because I said so!")
    }

    @Test
    fun `request with the same legal name as a pending request`() {
        val requestId1 = storage.saveRequest(createRequest("BankA").first)
        assertThat(storage.getRequests(RequestStatus.New).map { it.requestId }).containsOnly(requestId1)
        val requestId2 = storage.saveRequest(createRequest("BankA").first)
        assertThat(storage.getRequests(RequestStatus.New).map { it.requestId }).containsOnly(requestId1)
        assertEquals(RequestStatus.Rejected, storage.getRequest(requestId2)!!.status)
        assertThat(storage.getRequest(requestId2)!!.rejectReason).containsIgnoringCase("duplicate")
        // Make sure the first request is processed properly
        storage.approveRequest(requestId1)
        assertThat(storage.getRequest(requestId1)!!.status).isEqualTo(RequestStatus.Approved)
    }

    @Test
    fun `request with the same legal name as a previously approved request`() {
        val requestId1 = storage.saveRequest(createRequest("BankA").first)
        storage.approveRequest(requestId1)
        val requestId2 = storage.saveRequest(createRequest("BankA").first)
        assertThat(storage.getRequest(requestId2)!!.rejectReason).containsIgnoringCase("duplicate")
    }

    @Test
    fun `request with the same legal name as a previously rejected request`() {
        val requestId1 = storage.saveRequest(createRequest("BankA").first)
        storage.rejectRequest(requestId1, rejectReason = "Because I said so!")
        val requestId2 = storage.saveRequest(createRequest("BankA").first)
        assertThat(storage.getRequests(RequestStatus.New).map { it.requestId }).containsOnly(requestId2)
        storage.approveRequest(requestId2)
        assertThat(storage.getRequest(requestId2)!!.status).isEqualTo(RequestStatus.Approved)
    }

    private fun createRequest(legalName: String): Pair<PKCS10CertificationRequest, KeyPair> {
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val request = X509Utilities.createCertificateSigningRequest(CordaX500Name(organisation = legalName, locality = "London", country = "GB"), "my@mail.com", keyPair)
        return Pair(request, keyPair)
    }

    private fun makeTestDataSourceProperties(nodeName: String = SecureHash.randomSHA256().toString()): Properties {
        val props = Properties()
        props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
        props.setProperty("dataSource.url", "jdbc:h2:mem:${nodeName}_persistence;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
        props.setProperty("dataSource.user", "sa")
        props.setProperty("dataSource.password", "")
        return props
    }

    private fun makeTestDatabaseProperties(key: String? = null, value: String? = null): Properties {
        val props = Properties()
        props.setProperty("transactionIsolationLevel", "repeatableRead") //for other possible values see net.corda.node.utilities.CordaPeristence.parserTransactionIsolationLevel(String)
        if (key != null) {
            props.setProperty(key, value)
        }
        return props
    }
}
