package net.corda.nodeapi.internal.network

import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import java.math.BigInteger
import java.security.cert.CRLReason

/**
 * This data class is intended to be used by the certificate revocation request (CRR) service client to create a new
 * CRR submission.
 */
@CordaSerializable
data class CertificateRevocationRequest(val certificateSerialNumber: BigInteger? = null,
                                        val csrRequestId: String? = null,
                                        val legalName: CordaX500Name? = null,
                                        val reason: CRLReason,
                                        val reporter: String) {
    companion object {
        fun validateOptional(certificateSerialNumber: BigInteger?, csrRequestId: String?, legalName: CordaX500Name?) {
            require(certificateSerialNumber != null || csrRequestId != null || legalName != null) {
                "At least one of the following needs to be specified: certificateSerialNumber, csrRequestId, legalName."
            }
        }
    }

    init {
        validateOptional(certificateSerialNumber, csrRequestId, legalName)
    }
}