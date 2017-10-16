package com.r3.corda.signing.hsm

import com.r3.corda.signing.persistence.ApprovedCertificateRequestData

/**
 * Encapsulates the logic related to the certificate signing process.
 */
interface Signer {

    /**
     * Signs the provided list of [ApprovedCertificateRequestData]
     */
    fun sign(toSign: List<ApprovedCertificateRequestData>)

}