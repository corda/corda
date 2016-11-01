package com.r3corda.netpermission

import com.google.common.net.HostAndPort
import com.nhaarman.mockito_kotlin.*
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.X509Utilities
import com.r3corda.netpermission.CertificateSigningServer.Companion.hostAndPort
import com.r3corda.netpermission.internal.CertificateSigningService
import com.r3corda.netpermission.internal.persistence.CertificationData
import com.r3corda.netpermission.internal.persistence.CertificationRequestStorage
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CertificateSigningServiceTest {
    val rootCA = X509Utilities.createSelfSignedCACert("Corda Node Root CA")
    val intermediateCA = X509Utilities.createSelfSignedCACert("Corda Node Intermediate CA")

    private fun getSigningServer(storage: CertificationRequestStorage): CertificateSigningServer {
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

        // Mock Storage behaviour.
        val certificateStore = mutableMapOf<String, Certificate>()
        val storage: CertificationRequestStorage = mock {
            on { getCertificate(eq(id)) }.then { certificateStore[id] }
            on { saveCertificate(eq(id), any()) }.then {
                val certGen = it.arguments[1] as (CertificationData) -> Certificate
                val request = CertificationData("", "", X509Utilities.createCertificateSigningRequest("LegalName", "London", "admin@test.com", keyPair))
                certificateStore[id] = certGen(request)
                Unit
            }
            on { pendingRequestIds() }.then { listOf(id) }
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

            assertNull(poll())
            assertNull(poll())

            storage.saveCertificate(id, {
                JcaPKCS10CertificationRequest(it.request).run {
                    X509Utilities.createServerCert(subject, publicKey, intermediateCA,
                            if (it.ipAddr == it.hostName) listOf() else listOf(it.hostName), listOf(it.ipAddr))
                }
            })

            val certificates = assertNotNull(poll())

            verify(storage, times(3)).getCertificate(any())

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