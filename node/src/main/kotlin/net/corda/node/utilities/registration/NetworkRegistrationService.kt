package net.corda.node.utilities.registration

import net.corda.core.CordaException
import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.Certificate

interface NetworkRegistrationService {
    /** Submits a CSR to the signing service and returns an opaque request ID. */
    fun submitRequest(request: PKCS10CertificationRequest): String

    /** Poll Certificate Signing Server for the request and returns a chain of certificates if request has been approved, null otherwise. */
    @Throws(CertificateRequestException::class)
    fun retrieveCertificates(requestId: String): Array<Certificate>?
}

@CordaSerializable
class CertificateRequestException(message: String) : CordaException(message)
