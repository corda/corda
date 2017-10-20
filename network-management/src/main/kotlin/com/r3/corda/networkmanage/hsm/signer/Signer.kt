package com.r3.corda.networkmanage.hsm.signer

import com.r3.corda.networkmanage.hsm.persistence.CertificateRequestData

/**
 * Encapsulates the logic related to the certificate signing process.
 */
interface Signer {

    /**
     * Signs the provided list of [CertificateRequestData]
     */
    fun sign(toSign: List<CertificateRequestData>)

}