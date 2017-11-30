package net.corda.node.utilities.registration

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.cert
import net.corda.core.internal.toX509CertHolder
import net.corda.core.utilities.minutes
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.testing.ALICE_NAME
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.node.network.NetworkMapServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.security.KeyPair
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

private const val REQUEST_ID = "requestId"

private val x509CertificateFactory = X509CertificateFactory()
private val portAllocation = PortAllocation.Incremental(10000)

/**
 * Driver based tests for [NetworkRegistrationHelper]
 */
class NetworkRegistrationHelperDriverTest {
    val rootCertAndKeyPair = createSelfKeyAndSelfSignedCertificate()
    val rootCert = rootCertAndKeyPair.certificate
    val handler = RegistrationHandler(rootCertAndKeyPair)
    lateinit var server: NetworkMapServer
    lateinit var host: String
    var port: Int = 0
    val compatibilityZoneUrl get() = URL("http", host, port, "")

    @Before
    fun startServer() {
        server = NetworkMapServer(1.minutes, portAllocation.nextHostAndPort(), handler)
        val (host, port) = server.start()
        this.host = host
        this.port = port
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun `node registration correct root cert`() {
        driver(portAllocation = portAllocation,
                compatibilityZoneURL = compatibilityZoneUrl,
                startNodesInProcess = true,
                rootCertificate = rootCert) {

            // Wait for the node to have started.
            startNode(providedName = ALICE_NAME, initialRegistration = true).get()
        }

        // We're getting:
        //   a request to sign the certificate then
        //   at least one poll request to see if the request has been approved.
        //   all the network map registration and download.
        assertThat(handler.requests).startsWith("/certificate", "/certificate/" + REQUEST_ID)
    }

    @Test
    fun `node registration without root cert`() {
        driver(portAllocation = portAllocation,
                compatibilityZoneURL = compatibilityZoneUrl,
                startNodesInProcess = true) {

            assertThatThrownBy {
                startNode(providedName = ALICE_NAME, initialRegistration = true).get()
            }.isInstanceOf(java.nio.file.NoSuchFileException::class.java)
        }
    }

    @Test
    fun `node registration wrong root cert`() {
        driver(portAllocation = portAllocation,
                compatibilityZoneURL = compatibilityZoneUrl,
                startNodesInProcess = true,
                rootCertificate = createSelfKeyAndSelfSignedCertificate().certificate) {

            assertThatThrownBy {
                startNode(providedName = ALICE_NAME, initialRegistration = true).get()
            }.isInstanceOf(WrongRootCaCertificateException::class.java)
        }
    }
}


/**
 * Simple registration handler which can handle a single request, which will be given request id [REQUEST_ID].
 */
@Path("certificate")
class RegistrationHandler(private val certificateAndKeyPair: CertificateAndKeyPair) {
    val requests = mutableListOf<String>()
    lateinit var certificationRequest: JcaPKCS10CertificationRequest

    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_PLAIN)
    fun registration(input: InputStream): Response {
        requests += "/certificate"
        certificationRequest = input.use { JcaPKCS10CertificationRequest(it.readBytes()) }
        return Response.ok(REQUEST_ID).build()
    }

    @GET
    @Path(REQUEST_ID)
    fun reply(): Response {
        requests += "/certificate/" + REQUEST_ID
        val certPath = createSignedClientCertificate(certificationRequest,
                certificateAndKeyPair.keyPair, arrayOf(certificateAndKeyPair.certificate.cert))
        return buildDoormanReply(certPath.certificates.toTypedArray())
    }
}

// TODO this logic is shared with doorman itself, refactor this to be somewhere where both doorman and these tests
// can depend on
private fun createSignedClientCertificate(certificationRequest: PKCS10CertificationRequest,
                                          caKeyPair: KeyPair,
                                          caCertPath: Array<Certificate>): CertPath {
    val request = JcaPKCS10CertificationRequest(certificationRequest)
    val x509CertificateHolder = X509Utilities.createCertificate(CertificateType.CLIENT_CA,
            caCertPath.first().toX509CertHolder(),
            caKeyPair,
            CordaX500Name.parse(request.subject.toString()).copy(commonName = X509Utilities.CORDA_CLIENT_CA_CN),
            request.publicKey,
            nameConstraints = null)
    return x509CertificateFactory.buildCertPath(x509CertificateHolder.cert, *caCertPath)
}

// TODO this logic is shared with doorman itself, refactor this to be somewhere where both doorman and these tests
// can depend on
private fun buildDoormanReply(certificates: Array<Certificate>): Response {
    // Write certificate chain to a zip stream and extract the bit array output.
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zip ->
        // Client certificate must come first and root certificate should come last.
        listOf(X509Utilities.CORDA_CLIENT_CA, CORDA_ROOT_CA).zip(certificates).forEach {
            zip.putNextEntry(ZipEntry("${it.first}.cer"))
            zip.write(it.second.encoded)
            zip.closeEntry()
        }
    }
    return Response.ok(baos.toByteArray())
            .type("application/zip")
            .header("Content-Disposition", "attachment; filename=\"certificates.zip\"").build()
}

private fun createSelfKeyAndSelfSignedCertificate(): CertificateAndKeyPair {
    val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val rootCACert = X509Utilities.createSelfSignedCACertificate(
            CordaX500Name(commonName = "Integration Test Corda Node Root CA",
                    organisation = "R3 Ltd", locality = "London",
                    country = "GB"), rootCAKey)
    return CertificateAndKeyPair(rootCACert, rootCAKey)
}
