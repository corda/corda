package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.TestBase
import net.corda.nodeapi.internal.network.CertificateRevocationRequest
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.cert.CRLReason
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class PersistentCertificateRevocationRequestStorageTest : TestBase() {
    private lateinit var crrStorage: PersistentCertificateRevocationRequestStorage
    private lateinit var csrStorage: PersistentCertificateSigningRequestStorage
    private lateinit var persistence: CordaPersistence

    companion object {
        const val REPORTER = "TestReporter"
        val REVOCATION_REASON = CRLReason.KEY_COMPROMISE
    }

    @Before
    fun startDb() {
        persistence = configureDatabase(MockServices.makeTestDataSourceProperties(), DatabaseConfig(runMigration = true))
        crrStorage = PersistentCertificateRevocationRequestStorage(persistence)
        csrStorage = PersistentCertificateSigningRequestStorage(persistence)
    }

    @After
    fun closeDb() {
        persistence.close()
    }

    @Test
    fun `Certificate revocation request is persisted correctly`() {
        // given
        val certificate = createNodeCertificate(csrStorage)

        // when
        val requestId = crrStorage.saveRevocationRequest(CertificateRevocationRequest(
                certificateSerialNumber = certificate.serialNumber,
                reason = REVOCATION_REASON,
                reporter = REPORTER))

        // then
        assertNotNull(crrStorage.getRevocationRequest(requestId)).apply {
            assertEquals(certificate.serialNumber, certificateSerialNumber)
            assertEquals(REVOCATION_REASON, reason)
            assertEquals(REPORTER, reporter)
        }
    }

    @Test
    fun `Retrieving a certificate revocation request succeeds`() {
        // given
        val certificate = createNodeCertificate(csrStorage)
        val requestId = crrStorage.saveRevocationRequest(CertificateRevocationRequest(
                certificateSerialNumber = certificate.serialNumber,
                reason = REVOCATION_REASON,
                reporter = REPORTER))

        // when
        val request = crrStorage.getRevocationRequest(requestId)

        // then
        assertNotNull(request)
    }

    @Test
    fun `Retrieving a certificate revocation requests by status returns correct data`() {
        // given
        (1..10).forEach {
            crrStorage.saveRevocationRequest(CertificateRevocationRequest(
                    certificateSerialNumber = createNodeCertificate(csrStorage, "LegalName" + it.toString()).serialNumber,
                    reason = REVOCATION_REASON,
                    reporter = REPORTER))
        }
        (11..15).forEach {
            val requestId = crrStorage.saveRevocationRequest(CertificateRevocationRequest(
                    certificateSerialNumber = createNodeCertificate(csrStorage, "LegalName" + it.toString()).serialNumber,
                    reason = REVOCATION_REASON,
                    reporter = REPORTER))
            crrStorage.markRequestTicketCreated(requestId)
            crrStorage.approveRevocationRequest(requestId, "Approver")
        }

        // when
        val result = crrStorage.getRevocationRequests(RequestStatus.APPROVED)

        // then
        assertEquals(5, result.size)
    }

    @Test
    fun `revocation request fails if a valid certificate cannot be found`() {
        // given

        // then
        assertFailsWith(IllegalArgumentException::class) {
            // when
            crrStorage.saveRevocationRequest(CertificateRevocationRequest(
                    certificateSerialNumber = BigInteger.TEN,
                    reason = REVOCATION_REASON,
                    reporter = REPORTER))
        }
    }

    @Test
    fun `Approving a certificate revocation request changes its status`() {
        // given
        val certificate = createNodeCertificate(csrStorage)
        val requestId = crrStorage.saveRevocationRequest(CertificateRevocationRequest(
                certificateSerialNumber = certificate.serialNumber,
                reason = REVOCATION_REASON,
                reporter = REPORTER))
        crrStorage.markRequestTicketCreated(requestId)

        // when
        crrStorage.approveRevocationRequest(requestId, "Approver")

        // then
        assertNotNull(crrStorage.getRevocationRequest(requestId)).apply {
            assertEquals(RequestStatus.APPROVED, status)
        }
    }

    @Test
    fun `Rejecting a certificate revocation request changes its status`() {
        // given
        val certificate = createNodeCertificate(csrStorage)
        val requestId = crrStorage.saveRevocationRequest(CertificateRevocationRequest(
                certificateSerialNumber = certificate.serialNumber,
                reason = REVOCATION_REASON,
                reporter = REPORTER))
        crrStorage.markRequestTicketCreated(requestId)

        // when
        crrStorage.rejectRevocationRequest(requestId, "Rejector", "No reason")

        // then
        assertNotNull(crrStorage.getRevocationRequest(requestId)).apply {
            assertEquals(RequestStatus.REJECTED, status)
        }
    }
}