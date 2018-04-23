package com.r3.corda.networkmanage.common.signer

import com.r3.corda.networkmanage.common.persistence.CertificateRevocationListStorage
import com.r3.corda.networkmanage.common.persistence.CertificateRevocationRequestData
import com.r3.corda.networkmanage.common.persistence.CrlIssuer
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import com.r3.corda.networkmanage.common.utils.createSignedCrl
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.trace
import java.net.URL
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant

class CertificateRevocationListSigner(
        private val revocationListStorage: CertificateRevocationListStorage,
        private val issuerCertificate: X509Certificate,
        private val updateInterval: Duration,
        private val endpoint: URL,
        private val signer: Signer) {
    private companion object {
        private val logger = contextLogger()
    }

    /**
     * Builds, signs and persists a new certificate revocation list.
     * It happens only if there are new entries to be added to the current list.
     *
     * @param newCRRs list of approved certificate revocation requests. An approved certificate revocation request
     *                  is a request that has status [RequestStatus.APPROVED] - i.e. it has not yet been included in any CRL.
     * @param existingCRRs list of revoked certificate revocation requests. A revoked certificate revocation request
     *                  is a request that has status [RequestStatus.DONE] - i.e. it has already been included in another CRL.
     * @param signedBy who signs this CRL.
     *
     * @return A new signed CRL containing both revoked and approved certificate revocation requests.
     */
    fun createSignedCRL(newCRRs: List<CertificateRevocationRequestData>,
                        existingCRRs: List<CertificateRevocationRequestData>,
                        signedBy: String): X509CRL {
        require(existingCRRs.all { it.status == RequestStatus.DONE }) { "All existing CRRs need to be in the ${RequestStatus.DONE} state" }
        require(newCRRs.all { it.status == RequestStatus.APPROVED }) { "All newly included CRRs need to be in the ${RequestStatus.APPROVED} state" }
        logger.info("Signing a new Certificate Revocation List...")
        logger.debug("Retrieving approved Certificate Revocation Requests...")
        val revocationTime = Instant.now()
        val approvedWithTimestamp = newCRRs.map { it.copy(modifiedAt = revocationTime) }
        logger.trace { "Approved Certificate Revocation Requests to be included in the new Certificate Revocation List: $approvedWithTimestamp" }
        logger.debug("Retrieving revoked Certificate Revocation Requests...")
        logger.trace { "Revoked Certificate Revocation Requests to be included in the new Certificate Revocation List: $existingCRRs" }
        val crl = createSignedCrl(issuerCertificate, endpoint, updateInterval, signer, existingCRRs + approvedWithTimestamp)
        logger.debug { "Created a new Certificate Revocation List $crl" }
        revocationListStorage.saveCertificateRevocationList(crl, CrlIssuer.DOORMAN, signedBy, revocationTime)
        logger.info("A new Certificate Revocation List has been persisted.")
        return crl
    }
}