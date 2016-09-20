package com.r3corda.node.utilities.certsigning

import com.google.common.net.HostAndPort
import org.apache.commons.io.IOUtils
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.*
import java.util.zip.ZipInputStream

class HTTPCertificateSigningService(val server: HostAndPort) : CertificateSigningService {
    companion object {
        // TODO: Propagate version information from gradle
        val clientVersion = "1.0"
    }

    override fun retrieveCertificates(requestId: String): Array<Certificate>? {
        // Poll server to download the signed certificate once request has been approved.
        val url = URL("http://$server/api/certificate/$requestId")

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"

        return when (conn.responseCode) {
            HttpURLConnection.HTTP_OK -> conn.inputStream.use {
                ZipInputStream(it).use {
                    val certificates = ArrayList<Certificate>()
                    while (it.nextEntry != null) {
                        certificates.add(CertificateFactory.getInstance("X.509").generateCertificate(it))
                    }
                    certificates.toTypedArray()
                }
            }
            HttpURLConnection.HTTP_NO_CONTENT -> null
            HttpURLConnection.HTTP_UNAUTHORIZED -> throw IOException("Certificate signing request has been rejected, please contact Corda network administrator for more information.")
            else -> throw IOException("Unexpected response code ${conn.responseCode} - ${IOUtils.toString(conn.errorStream)}")
        }
    }

    override fun submitRequest(request: PKCS10CertificationRequest): String {
        // Post request to certificate signing server via http.
        val conn = URL("http://$server/api/certificate").openConnection() as HttpURLConnection
        conn.doOutput = true
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setRequestProperty("Client-Version", clientVersion)
        conn.outputStream.write(request.encoded)

        return when (conn.responseCode) {
            HttpURLConnection.HTTP_OK -> IOUtils.toString(conn.inputStream)
            HttpURLConnection.HTTP_FORBIDDEN -> throw IOException("Client version $clientVersion is forbidden from accessing permissioning server, please upgrade to newer version.")
            else -> throw IOException("Unexpected response code ${conn.responseCode} - ${IOUtils.toString(conn.errorStream)}")
        }

    }
}