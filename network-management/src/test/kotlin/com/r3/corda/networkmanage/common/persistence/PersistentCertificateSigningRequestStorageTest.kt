/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequestStorage.Companion.DOORMAN_SIGNATURE
import com.r3.corda.networkmanage.common.persistence.entity.CertificateSigningRequestEntity
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.CertRole
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.createDevNodeCaCertPath
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.hibernate.envers.AuditReaderFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.cert.CertPath
import java.util.*
import javax.security.auth.x500.X500Principal
import kotlin.test.*

class PersistentCertificateRequestStorageTest : TestBase() {
    private lateinit var storage: PersistentCertificateSigningRequestStorage
    private lateinit var persistence: CordaPersistence

    @Before
    fun startDb() {
        persistence = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true))
        storage = PersistentCertificateSigningRequestStorage(persistence)
    }

    @After
    fun closeDb() {
        persistence.close()
    }

    @Test
    fun `valid request`() {
        val request = createRequest("LegalName", certRole = CertRole.NODE_CA).first
        val requestId = storage.saveRequest(request)
        assertNotNull(storage.getRequest(requestId)).apply {
            assertEquals(request, this.request)
        }
        assertThat(storage.getRequests(RequestStatus.NEW).map { it.requestId }).containsOnly(requestId)
    }

    @Test
    fun `valid service identity request`() {
        val request = createRequest("LegalName", certRole = CertRole.SERVICE_IDENTITY).first
        val requestId = storage.saveRequest(request)
        assertNotNull(storage.getRequest(requestId)).apply {
            assertEquals(request, this.request)
        }
        assertThat(storage.getRequests(RequestStatus.NEW).map { it.requestId }).containsOnly(requestId)
    }

    @Test
    fun `invalid cert role request`() {
        val request = createRequest("LegalName", certRole = CertRole.INTERMEDIATE_CA).first
        val requestId = storage.saveRequest(request)
        assertNotNull(storage.getRequest(requestId)).apply {
            assertEquals(request, this.request)
        }
        assertThat(storage.getRequests(RequestStatus.REJECTED).map { it.requestId }).containsOnly(requestId)
    }

    @Test
    fun `approve request`() {
        val (request, _) = createRequest("LegalName", certRole = CertRole.NODE_CA)
        // Add request to DB.
        val requestId = storage.saveRequest(request)
        // Pending request should equals to 1.
        assertEquals(1, storage.getRequests(RequestStatus.NEW).size)
        // Certificate should be empty.
        assertNull(storage.getRequest(requestId)!!.certData)
        // Signal that a ticket has been created for the request.
        storage.markRequestTicketCreated(requestId)
        // Store certificate to DB.
        storage.approveRequest(requestId, DOORMAN_SIGNATURE)
        // Check request is not ready yet.
        // assertTrue(storage.getResponse(requestId) is CertificateResponse.NotReady)
        // New request should be empty.
        assertTrue(storage.getRequests(RequestStatus.NEW).isEmpty())
    }

    @Test
    fun `sign request`() {
        val (csr, nodeKeyPair) = createRequest("LegalName", certRole = CertRole.NODE_CA)
        // Add request to DB.
        val requestId = storage.saveRequest(csr)
        // New request should equals to 1.
        assertEquals(1, storage.getRequests(RequestStatus.NEW).size)
        // Certificate should be empty.
        assertNull(storage.getRequest(requestId)!!.certData)
        storage.markRequestTicketCreated(requestId)
        // Store certificate to DB.
        storage.approveRequest(requestId, DOORMAN_SIGNATURE)
        // Check request is not ready yet.
        assertEquals(RequestStatus.APPROVED, storage.getRequest(requestId)!!.status)
        // New request should be empty.
        assertTrue(storage.getRequests(RequestStatus.NEW).isEmpty())
        // Sign certificate
        storage.putCertificatePath(
                requestId,
                generateSignedCertPath(csr, nodeKeyPair),
                DOORMAN_SIGNATURE
        )
        // Check request is ready
        assertNotNull(storage.getRequest(requestId)!!.certData)
    }

    @Test
    fun `get valid certificate path returns correct value`() {
        // given
        val (csr, nodeKeyPair) = createRequest("LegalName", certRole = CertRole.NODE_CA)
        val requestId = storage.saveRequest(csr)
        storage.markRequestTicketCreated(requestId)
        storage.approveRequest(requestId, DOORMAN_SIGNATURE)
        val certPath = generateSignedCertPath(csr, nodeKeyPair)
        storage.putCertificatePath(
                requestId,
                certPath,
                DOORMAN_SIGNATURE
        )

        // when
        val storedCertPath = storage.getValidCertificatePath(nodeKeyPair.public)

        // then
        assertEquals(certPath, storedCertPath)
    }

    @Test
    fun `get valid certificate path returns null if the certificate path cannot be found`() {
        // given
        val (_, nodeKeyPair) = createRequest("LegalName", certRole = CertRole.NODE_CA)

        // when
        val storedCertPath = storage.getValidCertificatePath(nodeKeyPair.public)

        // then
        assertNull(storedCertPath)
    }

    @Test
    fun `sign request ignores subsequent sign requests`() {
        val (csr, nodeKeyPair) = createRequest("LegalName", certRole = CertRole.NODE_CA)
        // Add request to DB.
        val requestId = storage.saveRequest(csr)
        // Store certificate to DB.
        storage.markRequestTicketCreated(requestId)
        storage.approveRequest(requestId, DOORMAN_SIGNATURE)
        // Sign certificate
        storage.putCertificatePath(
                requestId,
                generateSignedCertPath(csr, nodeKeyPair),
                DOORMAN_SIGNATURE
        )
        // When subsequent signature requested
        assertFailsWith(IllegalArgumentException::class) {
            storage.putCertificatePath(
                    requestId,
                    generateSignedCertPath(csr, nodeKeyPair),
                    DOORMAN_SIGNATURE)
        }
    }

    @Test
    fun `sign request rejects requests with the same public key`() {
        val (csr, nodeKeyPair) = createRequest("LegalName", certRole = CertRole.NODE_CA)
        // Add request to DB.
        val requestId = storage.saveRequest(csr)
        // Store certificate to DB.
        storage.markRequestTicketCreated(requestId)
        storage.approveRequest(requestId, DOORMAN_SIGNATURE)
        // Sign certificate
        storage.putCertificatePath(
                requestId,
                generateSignedCertPath(csr, nodeKeyPair),
                DOORMAN_SIGNATURE
        )
        // Sign certificate
        // When request with the same public key is requested
        val (newCsr, _) = createRequest("NewLegalName", nodeKeyPair, certRole = CertRole.NODE_CA)
        val duplicateRequestId = storage.saveRequest(newCsr)
        assertThat(storage.getRequests(RequestStatus.NEW)).isEmpty()
        val duplicateRequest = storage.getRequest(duplicateRequestId)
        assertThat(duplicateRequest!!.status).isEqualTo(RequestStatus.REJECTED)
        assertThat(duplicateRequest.remark).isEqualTo("Duplicate public key")
    }

    @Test
    fun `reject request`() {
        val requestId = storage.saveRequest(createRequest("BankA", certRole = CertRole.NODE_CA).first)
        storage.rejectRequest(requestId, DOORMAN_SIGNATURE, "Because I said so!")
        assertThat(storage.getRequests(RequestStatus.NEW)).isEmpty()
        assertThat(storage.getRequest(requestId)!!.remark).isEqualTo("Because I said so!")
    }

    @Test
    fun `request with the same legal name as a pending request`() {
        val requestId1 = storage.saveRequest(createRequest("BankA", certRole = CertRole.NODE_CA).first)
        assertThat(storage.getRequests(RequestStatus.NEW).map { it.requestId }).containsOnly(requestId1)
        val requestId2 = storage.saveRequest(createRequest("BankA", certRole = CertRole.NODE_CA).first)
        assertThat(storage.getRequests(RequestStatus.NEW).map { it.requestId }).containsOnly(requestId1)
        assertEquals(RequestStatus.REJECTED, storage.getRequest(requestId2)!!.status)
        assertThat(storage.getRequest(requestId2)!!.remark).containsIgnoringCase("duplicate")
        // Make sure the first request is processed properly
        storage.markRequestTicketCreated(requestId1)
        storage.approveRequest(requestId1, DOORMAN_SIGNATURE)
        assertThat(storage.getRequest(requestId1)!!.status).isEqualTo(RequestStatus.APPROVED)
    }

    @Test
    fun `request with the same legal name as a previously approved request`() {
        val requestId1 = storage.saveRequest(createRequest("BankA", certRole = CertRole.NODE_CA).first)
        storage.markRequestTicketCreated(requestId1)
        storage.approveRequest(requestId1, DOORMAN_SIGNATURE)
        val requestId2 = storage.saveRequest(createRequest("BankA", certRole = CertRole.NODE_CA).first)
        assertThat(storage.getRequest(requestId2)!!.remark).containsIgnoringCase("duplicate")
    }

    @Test
    fun `request with the same legal name as a previously signed request`() {
        val (csr, nodeKeyPair) = createRequest("BankA", certRole = CertRole.NODE_CA)
        val requestId = storage.saveRequest(csr)
        storage.markRequestTicketCreated(requestId)
        storage.approveRequest(requestId, DOORMAN_SIGNATURE)
        // Sign certificate
        storage.putCertificatePath(
                requestId,
                generateSignedCertPath(csr, nodeKeyPair),
                DOORMAN_SIGNATURE
        )
        val rejectedRequestId = storage.saveRequest(createRequest("BankA", certRole = CertRole.NODE_CA).first)
        assertThat(storage.getRequest(rejectedRequestId)!!.remark).containsIgnoringCase("duplicate")
    }

    @Test
    fun `request with the same legal name as a previously rejected request`() {
        val requestId1 = storage.saveRequest(createRequest("BankA", certRole = CertRole.NODE_CA).first)
        storage.rejectRequest(requestId1, DOORMAN_SIGNATURE, "Because I said so!")
        val requestId2 = storage.saveRequest(createRequest("BankA", certRole = CertRole.NODE_CA).first)
        assertThat(storage.getRequests(RequestStatus.NEW).map { it.requestId }).containsOnly(requestId2)
        storage.markRequestTicketCreated(requestId2)
        storage.approveRequest(requestId2, DOORMAN_SIGNATURE)
        assertThat(storage.getRequest(requestId2)!!.status).isEqualTo(RequestStatus.APPROVED)
    }

    @Test
    fun `audit data is available for CSRs`() {
        // given
        val approver = "APPROVER"

        // when
        val requestId = storage.saveRequest(createRequest("BankA", certRole = CertRole.NODE_CA).first)
        storage.markRequestTicketCreated(requestId)
        storage.approveRequest(requestId, approver)

        // then
        persistence.transaction {
            val auditReader = AuditReaderFactory.get(persistence.entityManagerFactory.createEntityManager())
            val newRevision = auditReader.find(CertificateSigningRequestEntity::class.java, requestId, 1)
            assertEquals(RequestStatus.NEW, newRevision.status)
            assertEquals(DOORMAN_SIGNATURE, newRevision.modifiedBy)

            val ticketCreatedRevision = auditReader.find(CertificateSigningRequestEntity::class.java, requestId, 2)
            assertEquals(RequestStatus.TICKET_CREATED, ticketCreatedRevision.status)
            assertEquals(DOORMAN_SIGNATURE, ticketCreatedRevision.modifiedBy)

            val approvedRevision = auditReader.find(CertificateSigningRequestEntity::class.java, requestId, 3)
            assertEquals(RequestStatus.APPROVED, approvedRevision.status)
            assertEquals(approver, approvedRevision.modifiedBy)
        }
    }

    private fun generateSignedCertPath(csr: PKCS10CertificationRequest, keyPair: KeyPair): CertPath {
        return JcaPKCS10CertificationRequest(csr).run {
            val (rootCa, intermediateCa, nodeCa) = createDevNodeCaCertPath(CordaX500Name.build(X500Principal(subject.encoded)), keyPair)
            X509Utilities.buildCertPath(nodeCa.certificate, intermediateCa.certificate, rootCa.certificate)
        }
    }

    private fun makeTestDataSourceProperties(nodeName: String = SecureHash.randomSHA256().toString()): Properties {
        val props = Properties()
        props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
        props.setProperty("dataSource.url", "jdbc:h2:mem:${nodeName}_persistence;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
        props.setProperty("dataSource.user", "sa")
        props.setProperty("dataSource.password", "")
        return props
    }
}

internal fun createRequest(organisation: String, keyPair: KeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME), certRole: CertRole): Pair<PKCS10CertificationRequest, KeyPair> {
    val request = X509Utilities.createCertificateSigningRequest(X500Principal("O=$organisation,L=London,C=GB"), "my@mail.com", keyPair, certRole = certRole)
    // encode and decode the request to make sure class information (CertRole) etc are not passed into the test.
    return Pair(JcaPKCS10CertificationRequest(request.encoded), keyPair)
}