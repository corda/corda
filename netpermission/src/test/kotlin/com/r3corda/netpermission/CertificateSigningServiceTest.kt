package com.r3corda.netpermission

import com.google.common.net.HostAndPort
import com.nhaarman.mockito_kotlin.*
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.X509Utilities
import com.r3corda.core.seconds
import com.r3corda.netpermission.CertificateSigningServer.Companion.hostAndPort
import com.r3corda.netpermission.internal.CertificateSigningService
import com.r3corda.netpermission.internal.persistence.CertificationData
import com.r3corda.netpermission.internal.persistence.CertificationRequestStorage
import org.junit.Test
import sun.security.x509.X500Name
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals

class CertificateSigningServiceTest {
    private fun getSigningServer(storage: CertificationRequestStorage): CertificateSigningServer {
        val rootCA = X509Utilities.createSelfSignedCACert("Corda Node Root CA")
        val intermediateCA = X509Utilities.createSelfSignedCACert("Corda Node Intermediate CA")
        return CertificateSigningServer(HostAndPort.fromParts("localhost", 0), CertificateSigningService(intermediateCA, rootCA.certificate, storage))
    }

    @Test
    fun testSubmitRequest() {
        val id = SecureHash.randomSHA256().toString()

        val storage: CertificationRequestStorage = mock {
            on { saveRequest(any()) }.then { id }
        }

        getSigningServer(storage).use {
            val keyPair = X509Utilities.generateECDSAKeyPairForSSL()
            val request = X509Utilities.createCertificateSigningRequest("Test", "London", "admin@test.com", keyPair)
            // Post request to signing server via http.
            val submitRequest = {
                val conn = URL("http://${it.server.hostAndPort()}/api/certificate").openConnection() as HttpURLConnection
                conn.doOutput = true
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/octet-stream")
                conn.outputStream.write(request.encoded)
                conn.inputStream.bufferedReader().readLine()
            }

            assertEquals(id, submitRequest())
            verify(storage, times(1)).saveRequest(any())
            submitRequest()
            verify(storage, times(2)).saveRequest(any())
        }
    }

    @Test
    fun testRetrieveCertificate() {
        val keyPair = X509Utilities.generateECDSAKeyPairForSSL()
        val id = SecureHash.randomSHA256().toString()
        var count = 0
        val storage: CertificationRequestStorage = mock {
            on { getApprovedRequest(eq(id)) }.then {
                if (count < 5) null else CertificationData("", "", X509Utilities.createCertificateSigningRequest("LegalName",
                        "London", "admin@test.com", keyPair))
            }
            on { getOrElseCreateCertificate(eq(id), any()) }.thenAnswer { (it.arguments[1] as () -> Certificate)() }
        }

        getSigningServer(storage).use {
            val poll = {
                val url = URL("http://${it.server.hostAndPort()}/api/certificate/$id")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"

                when (conn.responseCode) {
                    HttpURLConnection.HTTP_OK -> conn.inputStream.use {
                        ZipInputStream(it).use {
                            val certificates = ArrayList<Certificate>()
                            while (it.nextEntry != null) {
                                certificates.add(CertificateFactory.getInstance("X.509").generateCertificate(it))
                            }
                            certificates
                        }
                    }
                    HttpURLConnection.HTTP_NO_CONTENT -> null
                    else ->
                        throw IOException("Cannot connect to Certificate Signing Server, HTTP response code : ${conn.responseCode}")
                }
            }

            var certificates = poll()

            while (certificates == null) {
                Thread.sleep(1.seconds.toMillis())
                count++
                certificates = poll()
            }

            verify(storage, times(6)).getApprovedRequest(any())
            assertEquals(3, certificates.size)

            (certificates.first() as X509Certificate).run {
                assertEquals("LegalName", (subjectDN as X500Name).commonName)
                assertEquals("London", (subjectDN as X500Name).locality)
            }

            (certificates.last() as X509Certificate).run {
                assertEquals("Corda Node Root CA", (subjectDN as X500Name).commonName)
                assertEquals("London", (subjectDN as X500Name).locality)
            }
        }
    }
}