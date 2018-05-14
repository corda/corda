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

import net.corda.core.internal.errorMessage
import net.corda.core.internal.openHttpConnection
import net.corda.core.internal.post
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import okhttp3.CacheControl
import okhttp3.Headers
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.IOException
import java.net.HttpURLConnection
import java.net.HttpURLConnection.*
import java.net.URL
import java.security.cert.X509Certificate
import java.util.*
import java.util.zip.ZipInputStream

class HTTPNetworkRegistrationService(compatibilityZoneURL: URL) : NetworkRegistrationService {
    private val registrationURL = URL("$compatibilityZoneURL/certificate")

    companion object {
        // TODO: Propagate version information from gradle
        const val CLIENT_VERSION = "1.0"
    }

    @Throws(CertificateRequestException::class)
    override fun retrieveCertificates(requestId: String): CertificateResponse {
        // Poll server to download the signed certificate once request has been approved.
        val conn = URL("$registrationURL/$requestId").openHttpConnection()
        conn.requestMethod = "GET"
        val maxAge = conn.cacheControl.maxAgeSeconds()
        // Default poll interval to 10 seconds if not specified by the server, for backward compatibility.
        val pollInterval = if (maxAge == -1) 10.seconds else maxAge.seconds

        return when (conn.responseCode) {
            HTTP_OK -> ZipInputStream(conn.inputStream).use {
                val certificates = ArrayList<X509Certificate>()
                val factory = X509CertificateFactory()
                while (it.nextEntry != null) {
                    certificates += factory.generateCertificate(it)
                }
                CertificateResponse(pollInterval, certificates)
            }
            HTTP_NO_CONTENT -> CertificateResponse(pollInterval, null)
            HTTP_UNAUTHORIZED -> throw CertificateRequestException("Certificate signing request has been rejected: ${conn.errorMessage}")
            else -> throw IOException("Response Code ${conn.responseCode}: ${conn.errorMessage}")
        }
    }

    override fun submitRequest(request: PKCS10CertificationRequest): String {
        return String(registrationURL.post(OpaqueBytes(request.encoded), "Client-Version" to CLIENT_VERSION))
    }
}

val HttpURLConnection.cacheControl: CacheControl
    get() {
        return CacheControl.parse(Headers.of(headerFields.filterKeys { it != null }.mapValues { it.value[0] }))
    }
