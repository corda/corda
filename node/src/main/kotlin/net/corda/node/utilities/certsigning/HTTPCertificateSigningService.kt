package net.corda.node.utilities.certsigning

import net.corda.core.crypto.CertificateStream
import org.apache.commons.io.IOUtils
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.IOException
import java.net.HttpURLConnection
import java.net.HttpURLConnection.*
import java.net.URL
import java.security.cert.Certificate
import java.util.*
import java.util.zip.ZipInputStream

class HTTPCertificateSigningService(val server: URL) : CertificateSigningService {
    companion object {
        // TODO: Propagate version information from gradle
        val clientVersion = "1.0"
    }

    override fun retrieveCertificates(requestId: String): Array<Certificate>? {
        // Poll server to download the signed certificate once request has been approved.
        val url = URL("$server/api/certificate/$requestId")

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"

        return when (conn.responseCode) {
            HTTP_OK -> ZipInputStream(conn.inputStream).use {
                val certificates = ArrayList<Certificate>()
                val stream = CertificateStream(it)
                while (it.nextEntry != null) {
                    certificates.add(stream.nextCertificate())
                }
                certificates.toTypedArray()
            }
            HTTP_NO_CONTENT -> null
            HTTP_UNAUTHORIZED -> throw IOException("Certificate signing request has been rejected: ${conn.errorMessage}")
            else -> throwUnexpectedResponseCode(conn)
        }
    }

    override fun submitRequest(request: PKCS10CertificationRequest): String {
        // Post request to certificate signing server via http.
        val conn = URL("$server/api/certificate").openConnection() as HttpURLConnection
        conn.doOutput = true
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setRequestProperty("Client-Version", clientVersion)
        conn.outputStream.write(request.encoded)

        return when (conn.responseCode) {
            HTTP_OK -> IOUtils.toString(conn.inputStream)
            HTTP_FORBIDDEN -> throw IOException("Client version $clientVersion is forbidden from accessing permissioning server, please upgrade to newer version.")
            else -> throwUnexpectedResponseCode(conn)
        }
    }

    private fun throwUnexpectedResponseCode(connection: HttpURLConnection): Nothing {
        throw IOException("Unexpected response code ${connection.responseCode} - ${connection.errorMessage}")
    }

    private val HttpURLConnection.errorMessage: String get() = IOUtils.toString(errorStream)
}
