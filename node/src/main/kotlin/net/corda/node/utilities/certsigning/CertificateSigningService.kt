package net.corda.node.utilities.certsigning

import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.Certificate

interface CertificateSigningService {
    /** Submits a CSR to the signing service and returns an opaque request ID. */
    fun submitRequest(request: PKCS10CertificationRequest): String

    /** Poll Certificate Signing Server for the request and returns a chain of certificates if request has been approved, null otherwise. */
    fun retrieveCertificates(requestId: String): Array<Certificate>?
}
