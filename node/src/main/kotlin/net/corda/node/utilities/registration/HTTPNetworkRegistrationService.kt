package net.corda.node.utilities.registration

import net.corda.core.internal.errorMessage
import net.corda.core.internal.openHttpConnection
import net.corda.core.internal.post
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.seconds
import net.corda.node.VersionInfo
import net.corda.node.services.config.NetworkServicesConfig
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import okhttp3.CacheControl
import okhttp3.Headers.Companion.toHeaders
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.IOException
import java.net.HttpURLConnection
import java.net.HttpURLConnection.*
import java.net.URL
import java.security.cert.X509Certificate
import java.util.*
import java.util.zip.ZipInputStream
import javax.naming.ServiceUnavailableException

class HTTPNetworkRegistrationService(
        val config : NetworkServicesConfig,
        val versionInfo: VersionInfo,
        private val registrationURL: URL = URL("${config.doormanURL}/certificate")
) : NetworkRegistrationService {

    companion object {
        private val TRANSIENT_ERROR_STATUS_CODES = setOf(HTTP_BAD_GATEWAY, HTTP_UNAVAILABLE, HTTP_GATEWAY_TIMEOUT)
        private const val CENM_SUBMISSION_TOKEN = "X-CENM-Submission-Token"
    }

    @Throws(CertificateRequestException::class)
    override fun retrieveCertificates(requestId: String): CertificateResponse {
        // Poll server to download the signed certificate once request has been approved.
        val conn = URL("$registrationURL/$requestId").openHttpConnection()
        conn.requestMethod = "GET"
        val maxAge = conn.cacheControl.maxAgeSeconds
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
            in TRANSIENT_ERROR_STATUS_CODES -> throw ServiceUnavailableException("Could not connect with Doorman. Http response status code was ${conn.responseCode}.")
            else -> throw IOException("Error while connecting to the Doorman. Http response status code was ${conn.responseCode}.")
        }
    }

    override fun submitRequest(request: PKCS10CertificationRequest): String {
        return String(registrationURL.post(OpaqueBytes(request.encoded),
                "Platform-Version" to "${versionInfo.platformVersion}",
                "Client-Version" to versionInfo.releaseVersion,
                "Private-Network-Map" to (config.pnm?.toString() ?: ""),
                *(config.csrToken?.let { arrayOf(CENM_SUBMISSION_TOKEN to it) } ?: arrayOf())))
    }
}

val HttpURLConnection.cacheControl: CacheControl
    get() {
        return CacheControl.parse(headerFields.filterKeys { it != null }.mapValues { it.value[0] }.toHeaders())
    }

val HttpURLConnection.cordaServerVersion: String
    get() {
        return headerFields["X-Corda-Server-Version"]?.singleOrNull() ?: "1"
    }
