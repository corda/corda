package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.TestBase
import net.corda.core.utilities.minutes
import net.corda.nodeapi.internal.network.CertificateRevocationRequest
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.DEV_INTERMEDIATE_CA
import net.corda.testing.node.MockServices
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.cert.CRLReason
import java.security.cert.X509CRL
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PersistentCertificateRevocationListStorageTest : TestBase() {
    private lateinit var crrStorage: PersistentCertificateRevocationRequestStorage
    private lateinit var csrStorage: PersistentCertificateSigningRequestStorage
    private lateinit var crlStorage: PersistentCertificateRevocationListStorage
    private lateinit var persistence: CordaPersistence

    companion object {
        const val REPORTER = "TestReporter"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        val REVOCATION_REASON = CRLReason.KEY_COMPROMISE
    }

    @Before
    fun startDb() {
        persistence = configureDatabase(MockServices.makeTestDataSourceProperties(), DatabaseConfig(runMigration = true))
        crrStorage = PersistentCertificateRevocationRequestStorage(persistence)
        csrStorage = PersistentCertificateSigningRequestStorage(persistence)
        crlStorage = PersistentCertificateRevocationListStorage(persistence)
    }

    @After
    fun closeDb() {
        persistence.close()
    }

    @Test
    fun `Saving CRL persists it in the DB and changes the status of the certificate revocation requests to DONE`() {
        // given
        val certificate = createNodeCertificate(csrStorage)
        val requestId = crrStorage.saveRevocationRequest(CertificateRevocationRequest(
                certificateSerialNumber = certificate.serialNumber,
                reason = REVOCATION_REASON,
                reporter = REPORTER))
        crrStorage.markRequestTicketCreated(requestId)
        crrStorage.approveRevocationRequest(requestId, "Approver")
        val revocationRequest = crrStorage.getRevocationRequest(requestId)!!
        val crl = createDummyCertificateRevocationList(listOf(revocationRequest.certificateSerialNumber))

        // when
        crlStorage.saveCertificateRevocationList(crl, CrlIssuer.DOORMAN, "TestSigner", Instant.now())

        // then
        assertNotNull(crlStorage.getCertificateRevocationList(CrlIssuer.DOORMAN)).apply {
            assertEquals(crl, this)
        }
        assertNotNull(crrStorage.getRevocationRequest(requestId)).apply {
            assertEquals(RequestStatus.DONE, status)
        }
    }

    @Test
    fun `Saving CRL does not change the status of other requests`() {
        // given
        val done = crrStorage.saveRevocationRequest(CertificateRevocationRequest(
                certificateSerialNumber = createNodeCertificate(csrStorage, legalName = "Bank A").serialNumber,
                reason = REVOCATION_REASON,
                reporter = REPORTER))
        crrStorage.markRequestTicketCreated(done)
        crrStorage.approveRevocationRequest(done, "Approver")
        val doneRevocationRequest = crrStorage.getRevocationRequest(done)!!

        val new = crrStorage.saveRevocationRequest(CertificateRevocationRequest(
                certificateSerialNumber = createNodeCertificate(csrStorage, legalName = "Bank B").serialNumber,
                reason = REVOCATION_REASON,
                reporter = REPORTER))

        val crl = createDummyCertificateRevocationList(listOf(doneRevocationRequest.certificateSerialNumber))
        crlStorage.saveCertificateRevocationList(crl, CrlIssuer.DOORMAN, "TestSigner", Instant.now())

        val approved = crrStorage.saveRevocationRequest(CertificateRevocationRequest(
                certificateSerialNumber = createNodeCertificate(csrStorage, legalName = "Bank C").serialNumber,
                reason = REVOCATION_REASON,
                reporter = REPORTER))
        crrStorage.markRequestTicketCreated(approved)
        crrStorage.approveRevocationRequest(approved, "Approver")
        val approvedRevocationRequest = crrStorage.getRevocationRequest(approved)!!

        val newCrl = createDummyCertificateRevocationList(listOf(doneRevocationRequest.certificateSerialNumber, approvedRevocationRequest.certificateSerialNumber))
        // when
        crlStorage.saveCertificateRevocationList(newCrl, CrlIssuer.DOORMAN, "TestSigner", Instant.now())

        // then
        assertNotNull(crrStorage.getRevocationRequest(done)).apply {
            assertEquals(RequestStatus.DONE, status)
        }
        assertNotNull(crrStorage.getRevocationRequest(new)).apply {
            assertEquals(RequestStatus.NEW, status)
        }
    }

    private fun createDummyCertificateRevocationList(serialNumbers: List<BigInteger> = emptyList()): X509CRL {
        val (doormanCert, doormanKeys) = DEV_INTERMEDIATE_CA
        val builder = JcaX509v2CRLBuilder(doormanCert.subjectX500Principal, Date())
        val extensionUtils = JcaX509ExtensionUtils()
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensionUtils.createAuthorityKeyIdentifier(doormanCert))
        val issuingDistPointName = GeneralName(
                GeneralName.uniformResourceIdentifier, "http://dummy.com")
        // This is required and needs to match the certificate settings with respect to being indirect
        val issuingDistPoint = IssuingDistributionPoint(DistributionPointName(GeneralNames(issuingDistPointName)), false, false)
        builder.addExtension(Extension.issuingDistributionPoint, true, issuingDistPoint)
        builder.setNextUpdate(Date(System.currentTimeMillis() + 10.minutes.toMillis()))
        serialNumbers.forEach {
            builder.addCRLEntry(it, Date(System.currentTimeMillis() - 10.minutes.toMillis()), ReasonFlags.certificateHold)
        }
        val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(BouncyCastleProvider.PROVIDER_NAME).build(doormanKeys.private)
        return JcaX509CRLConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCRL(builder.build(signer))
    }
}