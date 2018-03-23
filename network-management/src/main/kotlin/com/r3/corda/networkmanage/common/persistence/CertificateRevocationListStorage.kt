package com.r3.corda.networkmanage.common.persistence

import java.security.cert.X509CRL
import java.time.Instant

/**
 * Interface for managing certificate revocation list persistence
 */
interface CertificateRevocationListStorage {
    companion object {
        val DOORMAN_SIGNATURE = "Doorman-Crl-Signer"
    }

    /**
     * Retrieves the latest certificate revocation list.
     *
     * @param crlIssuer CRL issuer CA type.
     * @return latest revocation list.
     */
    fun getCertificateRevocationList(crlIssuer: CrlIssuer): X509CRL?

    /**
     * Persists a new revocation list. Upon saving, statuses
     * of the approved revocation requests will automatically change to [RequestStatus.DONE].
     *
     * @param crl signed instance of the certificate revocation list. It will be serialized and stored as part of a
     * database entity.
     * @param crlIssuer CRL issuer CA type.
     * @param signedBy who signed this CRL.
     * @param revokedAt revocation time.
     */
    fun saveCertificateRevocationList(crl: X509CRL, crlIssuer: CrlIssuer, signedBy: String, revokedAt: Instant)
}

/**
 * There are 2 CAs that issue CRLs (i.e. Root CA and Doorman CA).
 */
enum class CrlIssuer {
    ROOT, DOORMAN
}