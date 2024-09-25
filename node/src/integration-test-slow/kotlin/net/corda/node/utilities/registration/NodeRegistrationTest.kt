package net.corda.node.utilities.registration

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.logElapsedTime
import net.corda.core.internal.readFully
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.coretesting.internal.DEV_ROOT_CA
import net.corda.testing.driver.NodeParameters
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.SharedCompatibilityZoneParams
import net.corda.testing.node.internal.internalDriver
import net.corda.testing.node.internal.network.NetworkMapServer
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.security.KeyPair
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NodeRegistrationTest {
    companion object {
        private val notaryName = CordaX500Name("NotaryService", "Zurich", "CH")
        private val aliceName = CordaX500Name("Alice", "London", "GB")
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val portAllocation = incrementalPortAllocation()
    private val registrationHandler = RegistrationHandler(DEV_ROOT_CA)
    private lateinit var server: NetworkMapServer
    private lateinit var serverHostAndPort: NetworkHostAndPort

    @Before
    fun startServer() {
        server = NetworkMapServer(
                pollInterval = 1.seconds,
                hostAndPort = portAllocation.nextHostAndPort(),
                myHostNameValue = "localhost",
                additionalServices = arrayOf(registrationHandler))
        serverHostAndPort = server.start()
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test(timeout=300_000)
	fun `node registration correct root cert`() {
        val compatibilityZone = SharedCompatibilityZoneParams(
                URL("http://$serverHostAndPort"),
                null,
                publishNotaries = { server.networkParameters = testNetworkParameters(it) },
                rootCert = DEV_ROOT_CA.certificate)
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                notarySpecs = listOf(NotarySpec(notaryName)),
                notaryCustomOverrides = mapOf("devMode" to false),
                startNodesInProcess = true,
                allowHibernateToManageAppSchema = false
        ) {
            startNode(NodeParameters(
                    providedName = aliceName,
                    customOverrides = mapOf("devMode" to false)
            )).getOrThrow()

            assertThat(registrationHandler.idsPolled).containsOnly(
                    aliceName.organisation,
                    notaryName.organisation)
        }
    }
}

@Path("certificate")
class RegistrationHandler(private val rootCertAndKeyPair: CertificateAndKeyPair) {
    private val certPaths = ConcurrentHashMap<String, CertPath>()
    val idsPolled = ConcurrentSkipListSet<String>()

    companion object {
        val log = loggerFor<RegistrationHandler>()
    }

    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_PLAIN)
    fun registration(input: InputStream): Response {
        return log.logElapsedTime("Registration") {
            val certificationRequest = JcaPKCS10CertificationRequest(input.readFully())
            val (certPath, name) = createSignedClientCertificate(
                    certificationRequest,
                    rootCertAndKeyPair.keyPair,
                    listOf(rootCertAndKeyPair.certificate))
            require(!name.organisation.contains("\\s".toRegex())) { "Whitespace in the organisation name not supported" }
            certPaths[name.organisation] = certPath
            Response.ok(name.organisation).build()
        }
    }

    @GET
    @Path("{id}")
    fun reply(@PathParam("id") id: String): Response {
        return log.logElapsedTime("Reply by Id") {
            idsPolled += id
            buildResponse(certPaths[id]!!.certificates)
        }
    }

    private fun buildResponse(certificates: List<Certificate>): Response {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            listOf(CORDA_CLIENT_CA, CORDA_INTERMEDIATE_CA, CORDA_ROOT_CA).zip(certificates).forEach {
                zip.putNextEntry(ZipEntry("${it.first}.cer"))
                zip.write(it.second.encoded)
                zip.closeEntry()
            }
        }
        return Response.ok(baos.toByteArray())
                .type("application/zip")
                .header("Content-Disposition", "attachment; filename=\"certificates.zip\"").build()
    }

    private fun createSignedClientCertificate(certificationRequest: PKCS10CertificationRequest,
                                              caKeyPair: KeyPair,
                                              caCertPath: List<X509Certificate>): Pair<CertPath, CordaX500Name> {
        val request = JcaPKCS10CertificationRequest(certificationRequest)
        val name = CordaX500Name.parse(request.subject.toString())
        val nodeCaCert = X509Utilities.createCertificate(
                CertificateType.NODE_CA,
                caCertPath[0],
                caKeyPair,
                name.x500Principal,
                request.publicKey,
                nameConstraints = null)
        val certPath = X509Utilities.buildCertPath(nodeCaCert, caCertPath)
        return Pair(certPath, name)
    }
}
