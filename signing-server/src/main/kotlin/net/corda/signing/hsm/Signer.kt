package net.corda.signing.hsm

import net.corda.signing.persistence.ApprovedCertificateRequestData

/**
 * Encapsulates the logic related to the certificate signing process.
 */
interface Signer {

    /**
     * Signs the provided list of [ApprovedCertificateRequestData]
     */
    fun sign(toSign: List<ApprovedCertificateRequestData>)

}