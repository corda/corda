package com.r3.corda.networkmanage.hsm.persistence

/**
 * Provides an API for storing signed CSRs (Certificate Signing Requests).
 */
interface SignedCertificateRequestStorage {

    /**
     * Returns all certificate signing requests that have been approved for signing.
     */
    fun getApprovedRequests(): List<CertificateRequestData>

    /**
     * Marks the database CSR entries as signed. Also it persists the certificate and the signature in the database.
     *
     * @param requests Signed requests that are to be stored.
     * @param signers List of user names that signed those requests. To be specific, each request has been signed by all of those users.
     */
    fun store(requests: List<CertificateRequestData>, signers: List<String>)
}