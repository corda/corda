package net.corda.signing.persistence

/**
 * Provides an API for database level manipulations of CSRs (Certificate Signing Requests).
 */
interface CertificateRequestStorage {
    /**
     * Returns all certificate signing requests that have been approved for signing.
     */
    fun getApprovedRequests(): List<ApprovedCertificateRequestData>

    /**
     * Marks the database CSR entries as signed. Also it persists the certificate and the signature in the database.
     *
     * @param requests Requests that are to be marked as signed.
     * @param signers List of user names that signed those requests. To be specific, each request has been signed by all of those users.
     */
    fun sign(requests: List<ApprovedCertificateRequestData>, signers: List<String>)
}