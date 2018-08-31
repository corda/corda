package net.corda.node.utilities.registration

import com.google.common.net.MediaType
import net.corda.core.internal.openHttpConnection
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import okhttp3.CacheControl
import okhttp3.Headers
import org.apache.commons.io.IOUtils
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.IOException
import java.net.HttpURLConnection
import java.net.HttpURLConnection.*
import java.net.URL
import java.security.cert.X509Certificate
import java.util.*
import java.util.zip.ZipInputStream
import javax.naming.ServiceUnavailableException

class HTTPNetworkRegistrationService(compatibilityZoneURL: URL) : NetworkRegistrationService {
    private val registrationURL = URL("$compatibilityZoneURL/certificate")

    companion object {
        // TODO: Propagate version information from gradle
        val clientVersion = "1.0"
        private val TRANSIENT_ERROR_STATUS_CODES = setOf(HTTP_BAD_GATEWAY, HTTP_UNAVAILABLE, HTTP_GATEWAY_TIMEOUT)
    }

    @Throws(CertificateRequestException::class)
    override fun retrieveCertificates(requestId: String): CertificateResponse {
        // Poll server to download the signed certificate once request has been approved.
        val conn = URL("$registrationURL/$requestId").openHttpConnection()
        conn.requestMethod = "GET"
        val maxAge = conn.cacheControl().maxAgeSeconds()
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
            else -> throwUnexpectedResponseCode(conn)
        }
    }

    override fun submitRequest(request: PKCS10CertificationRequest): String {
        // Post request to certificate signing server via http.
        val conn = URL("$registrationURL").openHttpConnection()
        conn.doOutput = true
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setRequestProperty("Client-Version", clientVersion)
        conn.outputStream.write(request.encoded)

        return when (conn.responseCode) {
            HTTP_OK -> IOUtils.toString(conn.inputStream, conn.charset)
            HTTP_FORBIDDEN -> throw IOException("Client version $clientVersion is forbidden from accessing permissioning server, please upgrade to newer version.")
            else -> throwUnexpectedResponseCode(conn)
        }
    }

    private fun throwUnexpectedResponseCode(connection: HttpURLConnection): Nothing {
        throw IOException("Unexpected response code ${connection.responseCode} - ${connection.errorMessage}")
    }

    private val HttpURLConnection.charset: String get() = MediaType.parse(contentType).charset().or(Charsets.UTF_8).name()

    private val HttpURLConnection.errorMessage: String get() = IOUtils.toString(errorStream, charset)
}

fun HttpURLConnection.cacheControl(): CacheControl = CacheControl.parse(Headers.of(headerFields.filterKeys { it != null }.mapValues { it.value[0] }))
