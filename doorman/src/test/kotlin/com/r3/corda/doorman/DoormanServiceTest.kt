package com.r3.corda.doorman

import com.google.common.net.HostAndPort
import com.nhaarman.mockito_kotlin.*
import com.r3.corda.doorman.persistence.CertificateResponse
import com.r3.corda.doorman.persistence.CertificationRequestData
import com.r3.corda.doorman.persistence.CertificationRequestStorage
import net.corda.core.crypto.*
import net.corda.core.crypto.X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME
import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.junit.After
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.HttpURLConnection.*
import java.net.URL
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.*
import java.util.zip.ZipInputStream
import javax.ws.rs.core.MediaType
import kotlin.test.assertEquals

class DoormanServiceTest {
    private val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val rootCACert = X509Utilities.createSelfSignedCACertificate(X500Name("CN=Corda Node Root CA,L=London"), rootCAKey)
    private val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey, X500Name("CN=Corda Node Intermediate CA,L=London"), intermediateCAKey.public)
    private lateinit var doormanServer: DoormanServer

    private fun startSigningServer(storage: CertificationRequestStorage) {
        doormanServer = DoormanServer(HostAndPort.fromParts("localhost", 0), CertificateAndKeyPair(intermediateCACert, intermediateCAKey), rootCACert, storage)
        doormanServer.start()
    }

    @After
    fun close() {
        doormanServer.close()
    }

    @Test
    fun `submit request`() {
        val id = SecureHash.randomSHA256().toString()

        val storage = mock<CertificationRequestStorage> {
            on { saveRequest(any()) }.then { id }
        }

        startSigningServer(storage)

        val keyPair = Crypto.generateKeyPair(DEFAULT_TLS_SIGNATURE_SCHEME)
        val request = X509Utilities.createCertificateSigningRequest(X500Name("CN=LegalName"), keyPair)
        // Post request to signing server via http.

        assertEquals(id, submitRequest(request))
        verify(storage, times(1)).saveRequest(any())
        submitRequest(request)
        verify(storage, times(2)).saveRequest(any())
    }

    @Test
    fun `retrieve certificate`() {
        val keyPair = Crypto.generateKeyPair(DEFAULT_TLS_SIGNATURE_SCHEME)
        val id = SecureHash.randomSHA256().toString()

        // Mock Storage behaviour.
        val certificateStore = mutableMapOf<String, Certificate>()
        val storage = mock<CertificationRequestStorage> {
            on { getResponse(eq(id)) }.then {
                certificateStore[id]?.let { CertificateResponse.Ready(it) } ?: CertificateResponse.NotReady
            }
            on { approveRequest(eq(id), any()) }.then {
                @Suppress("UNCHECKED_CAST")
                val certGen = it.arguments[1] as ((CertificationRequestData) -> Certificate)
                val request = CertificationRequestData("", "", X509Utilities.createCertificateSigningRequest(X500Name("CN=LegalName,L=London"), keyPair))
                certificateStore[id] = certGen(request)
                Unit
            }
            on { getPendingRequestIds() }.then { listOf(id) }
        }

        startSigningServer(storage)

        assertThat(pollForResponse(id)).isEqualTo(PollResponse.NotReady)

        storage.approveRequest(id) {
            JcaPKCS10CertificationRequest(request).run {
                X509Utilities.createCertificate(CertificateType.TLS, intermediateCACert, intermediateCAKey, subject, publicKey)
            }
        }

        val certificates = (pollForResponse(id) as PollResponse.Ready).certChain
        verify(storage, times(2)).getResponse(any())
        assertEquals(3, certificates.size)

        certificates.first().run {
            assertThat(subjectDN.name).contains("CN=LegalName")
            assertThat(subjectDN.name).contains("L=London")
        }

        certificates.last().run {
            assertThat(subjectDN.name).contains("CN=Corda Node Root CA")
            assertThat(subjectDN.name).contains("L=London")
        }
    }

    @Test
    fun `retrieve certificate and create valid TLS certificate`() {
        val keyPair = Crypto.generateKeyPair(DEFAULT_TLS_SIGNATURE_SCHEME)
        val id = SecureHash.randomSHA256().toString()

        // Mock Storage behaviour.
        val certificateStore = mutableMapOf<String, Certificate>()
        val storage = mock<CertificationRequestStorage> {
            on { getResponse(eq(id)) }.then {
                certificateStore[id]?.let { CertificateResponse.Ready(it) } ?: CertificateResponse.NotReady
            }
            on { approveRequest(eq(id), any()) }.then {
                @Suppress("UNCHECKED_CAST")
                val certGen = it.arguments[1] as ((CertificationRequestData) -> Certificate)
                val request = CertificationRequestData("", "", X509Utilities.createCertificateSigningRequest(X500Name("CN=LegalName,L=London"), keyPair))
                certificateStore[id] = certGen(request)
                Unit
            }
            on { getPendingRequestIds() }.then { listOf(id) }
        }

        startSigningServer(storage)

        assertThat(pollForResponse(id)).isEqualTo(PollResponse.NotReady)

        storage.approveRequest(id) {
            JcaPKCS10CertificationRequest(request).run {
                val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, X500Name("CN=LegalName, L=London")))), arrayOf())
                X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, subject, publicKey, nameConstraints = nameConstraints)
            }
        }

        val certificates = (pollForResponse(id) as PollResponse.Ready).certChain
        verify(storage, times(2)).getResponse(any())
        assertEquals(3, certificates.size)

        val sslKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val sslCert = X509Utilities.createCertificate(CertificateType.TLS, certificates.first(), keyPair, X500Name("CN=LegalName,L=London"), sslKey.public)

        X509Utilities.validateCertificateChain(certificates.last(), sslCert, *certificates.toTypedArray())
    }

    @Test
    fun `request not authorised`() {
        val id = SecureHash.randomSHA256().toString()

        val storage = mock<CertificationRequestStorage> {
            on { getResponse(eq(id)) }.then { CertificateResponse.Unauthorised("Not Allowed") }
            on { getPendingRequestIds() }.then { listOf(id) }
        }

        startSigningServer(storage)

        assertThat(pollForResponse(id)).isEqualTo(PollResponse.Unauthorised("Not Allowed"))
    }

    private fun submitRequest(request: PKCS10CertificationRequest): String {
        val conn = URL("http://${doormanServer.hostAndPort}/api/certificate").openConnection() as HttpURLConnection
        conn.doOutput = true
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", MediaType.APPLICATION_OCTET_STREAM)
        conn.outputStream.write(request.encoded)
        return conn.inputStream.bufferedReader().use { it.readLine() }
    }

    private fun pollForResponse(id: String): PollResponse {
        val url = URL("http://${doormanServer.hostAndPort}/api/certificate/$id")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"

        return when (conn.responseCode) {
            HTTP_OK -> ZipInputStream(conn.inputStream).use {
                val stream = CertificateStream(it)
                val certificates = ArrayList<X509Certificate>()
                while (it.nextEntry != null) {
                    certificates.add(stream.nextCertificate())
                }
                PollResponse.Ready(certificates)
            }
            HTTP_NO_CONTENT -> PollResponse.NotReady
            HTTP_UNAUTHORIZED -> PollResponse.Unauthorised(IOUtils.toString(conn.errorStream))
            else -> throw IOException("Cannot connect to Certificate Signing Server, HTTP response code : ${conn.responseCode}")
        }
    }

    private interface PollResponse {
        object NotReady : PollResponse
        data class Ready(val certChain: List<X509Certificate>) : PollResponse
        data class Unauthorised(val message: String) : PollResponse
    }
}