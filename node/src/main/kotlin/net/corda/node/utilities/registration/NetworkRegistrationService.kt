/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.utilities.registration

import net.corda.core.CordaException
import net.corda.core.serialization.CordaSerializable
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

@CordaSerializable
class CertificateRequestException(message: String) : CordaException(message)
