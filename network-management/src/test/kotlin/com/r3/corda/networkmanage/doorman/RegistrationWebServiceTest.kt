package com.r3.corda.networkmanage.doorman

import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.common.persistence.CertificateResponse
import com.r3.corda.networkmanage.common.utils.buildCertPath
import com.r3.corda.networkmanage.common.utils.toX509Certificate
import com.r3.corda.networkmanage.doorman.signer.CsrHandler
import com.r3.corda.networkmanage.doorman.webservice.RegistrationWebService
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.utilities.CertificateStream
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.X509Utilities
import net.corda.node.utilities.X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME
import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.junit.After
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.HttpURLConnection.*
import java.net.URL
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.util.*
import java.util.zip.ZipInputStream
import javax.ws.rs.core.MediaType
import kotlin.test.assertEquals

class RegistrationWebServiceTest {
    private val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val rootCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Corda Node Root CA", locality = "London", organisation = "R3 Ltd", country = "GB"), rootCAKey)
    private val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey, X500Name("CN=Corda Node Intermediate CA,L=London"), intermediateCAKey.public)
    private lateinit var doormanServer: DoormanServer

    private fun startSigningServer(csrHandler: CsrHandler) {
        doormanServer = DoormanServer(NetworkHostAndPort("localhost", 0), RegistrationWebService(csrHandler, DoormanServerStatus()))
        doormanServer.start()
    }

    @After
    fun close() {
        doormanServer.close()
    }

    @Test
    fun `submit request`() {
        val id = SecureHash.randomSHA256().toString()

        val requestProcessor = mock<CsrHandler> {
            on { saveRequest(any()) }.then { id }
        }

        startSigningServer(requestProcessor)

        val keyPair = Crypto.generateKeyPair(DEFAULT_TLS_SIGNATURE_SCHEME)
        val request = X509Utilities.createCertificateSigningRequest(CordaX500Name(locality = "London", organisation = "Legal Name", country = "GB"), "my@mail.com", keyPair)
        // Post request to signing server via http.

        assertEquals(id, submitRequest(request))
        verify(requestProcessor, times(1)).saveRequest(any())
        submitRequest(request)
        verify(requestProcessor, times(2)).saveRequest(any())
    }

    @Test
    fun `retrieve certificate`() {
        val keyPair = Crypto.generateKeyPair(DEFAULT_TLS_SIGNATURE_SCHEME)
        val id = SecureHash.randomSHA256().toString()

        // Mock Storage behaviour.
        val certificateStore = mutableMapOf<String, CertPath>()
        val requestProcessor = mock<CsrHandler> {
            on { getResponse(eq(id)) }.then {
                certificateStore[id]?.let {
                    CertificateResponse.Ready(it)
                } ?: CertificateResponse.NotReady
            }
            on { processApprovedRequests() }.then {
                val request = X509Utilities.createCertificateSigningRequest(CordaX500Name(locality = "London", organisation = "LegalName", country = "GB"), "my@mail.com", keyPair)
                certificateStore[id] = JcaPKCS10CertificationRequest(request).run {
                    val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, intermediateCACert, intermediateCAKey, subject, publicKey).toX509Certificate()
                    buildCertPath(tlsCert, intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
                }
                null
            }
        }

        startSigningServer(requestProcessor)
        assertThat(pollForResponse(id)).isEqualTo(PollResponse.NotReady)

        requestProcessor.processApprovedRequests()

        val certificates = (pollForResponse(id) as PollResponse.Ready).certChain
        verify(requestProcessor, times(2)).getResponse(any())
        assertEquals(3, certificates.size)

        certificates.first().run {
            assertThat(subjectDN.name).contains("O=LegalName")
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
        val certificateStore = mutableMapOf<String, CertPath>()
        val storage = mock<CsrHandler> {
            on { getResponse(eq(id)) }.then {
                certificateStore[id]?.let {
                    CertificateResponse.Ready(it)
                } ?: CertificateResponse.NotReady
            }
            on { processApprovedRequests() }.then {
                val request = X509Utilities.createCertificateSigningRequest(CordaX500Name(locality = "London", organisation = "Legal Name", country = "GB"), "my@mail.com", keyPair)
                certificateStore[id] = JcaPKCS10CertificationRequest(request).run {
                    val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, X500Name("CN=LegalName, L=London")))), arrayOf())
                    val clientCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, subject, publicKey, nameConstraints = nameConstraints).toX509Certificate()
                    buildCertPath(clientCert, intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
                }
                true
            }
        }

        startSigningServer(storage)

        assertThat(pollForResponse(id)).isEqualTo(PollResponse.NotReady)

        storage.processApprovedRequests()

        val certificates = (pollForResponse(id) as PollResponse.Ready).certChain
        verify(storage, times(2)).getResponse(any())
        assertEquals(3, certificates.size)

        val sslKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val sslCert = X509Utilities.createCertificate(CertificateType.TLS, X509CertificateHolder(certificates.first().encoded), keyPair, X500Name("CN=LegalName,L=London"), sslKey.public).toX509Certificate()

        // TODO: This is temporary solution, remove all certificate re-shaping after identity refactoring is done.
        X509Utilities.validateCertificateChain(certificates.last(), sslCert, *certificates.toTypedArray())
    }

    @Test
    fun `request not authorised`() {
        val id = SecureHash.randomSHA256().toString()

        val requestProcessor = mock<CsrHandler> {
            on { getResponse(eq(id)) }.then { CertificateResponse.Unauthorised("Not Allowed") }
        }

        startSigningServer(requestProcessor)
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