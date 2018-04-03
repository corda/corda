package net.corda.node.utilities.registration

import net.corda.annotations.serialization.Serializable
import net.corda.core.CordaException
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.X509Certificate
import java.time.Duration

interface NetworkRegistrationService {
    /** Submits a CSR to the signing service and returns an opaque request ID. */
    fun submitRequest(request: PKCS10CertificationRequest): String

    /** Poll Certificate Signing Server for the request and returns a chain of certificates if request has been approved, null otherwise. */
    @Throws(CertificateRequestException::class)
    fun retrieveCertificates(requestId: String): CertificateResponse
}

data class CertificateResponse(val pollInterval: Duration, val certificates: List<X509Certificate>?)

@Serializable
class CertificateRequestException(message: String) : CordaException(message)
