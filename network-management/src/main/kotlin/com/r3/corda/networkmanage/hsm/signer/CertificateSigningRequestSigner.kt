package com.r3.corda.networkmanage.hsm.signer

import com.r3.corda.networkmanage.hsm.persistence.ApprovedCertificateRequestData

/**
 * Encapsulates the logic related to the certificate signing process.
 */
interface CertificateSigningRequestSigner {

    /**
     * Signs the provided list of [ApprovedCertificateRequestData] with the key/certificate chosen
     * by the implementing class.
     */
    fun sign(toSign: List<ApprovedCertificateRequestData>)

}