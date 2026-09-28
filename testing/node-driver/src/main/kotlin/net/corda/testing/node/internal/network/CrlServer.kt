@file:Suppress("MagicNumber")

package net.corda.testing.node.internal.network

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Response
import net.corda.core.crypto.Crypto
import net.corda.core.internal.CertRole
import net.corda.core.internal.toX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.days
import net.corda.core.utilities.minutes
import net.corda.coretesting.internal.DEV_INTERMEDIATE_CA
import net.corda.coretesting.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.ContentSignerBuilder
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.toGeneralNames
import net.corda.nodeapi.internal.crypto.certificateType
import net.corda.nodeapi.internal.crypto.toJca
import net.corda.testing.core.createCRL
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.URI
import java.security.KeyPair
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.*
import javax.security.auth.x500.X500Principal
import kotlin.collections.ArrayList

class CrlServer(hostAndPort: NetworkHostAndPort) : Closeable {
    companion object {
        private val logger = contextLogger()

        const val NODE_CRL = "node.crl"
        const val FORBIDDEN_CRL = "forbidden.crl"
        const val INTERMEDIATE_CRL = "intermediate.crl"
        const val EMPTY_CRL = "empty.crl"

        fun X509Certificate.withCrlDistPoint(issuerKeyPair: KeyPair, crlDistPoint: String?, crlIssuer: X500Principal? = null): X509Certificate {
            val signatureScheme = Crypto.findSignatureScheme(issuerKeyPair.private)
            val provider = Crypto.findProvider(signatureScheme.providerName)
            val issuerSigner = ContentSignerBuilder.build(signatureScheme, issuerKeyPair.private, provider)
            val builder = X509Utilities.createPartialCertificate(
                    CertRole.extract(this)!!.certificateType,
                    issuerX500Principal,
                    issuerKeyPair.public,
                    subjectX500Principal,
                    publicKey,
                    Pair(Date(System.currentTimeMillis() - 5.minutes.toMillis()), Date(System.currentTimeMillis() + 10.days.toMillis())),
                    null
            )
            if (crlDistPoint != null) {
                val distPointName = DistributionPointName(toGeneralNames(crlDistPoint, GeneralName.uniformResourceIdentifier))
                val crlIssuerGeneralNames = crlIssuer?.let { GeneralNames(GeneralName(it.toX500Name())) }
                val distPoint = DistributionPoint(distPointName, null, crlIssuerGeneralNames)
                builder.addExtension(Extension.cRLDistributionPoints, false, CRLDistPoint(arrayOf(distPoint)))
            }
            return builder.build(issuerSigner).toJca()
        }
    }

    private val server: Server = Server(InetSocketAddress(hostAndPort.host, hostAndPort.port)).apply {
        handler = ContextHandlerCollection().apply {
            addHandler(buildServletContextHandler())
        }
    }

    val revokedNodeCerts: MutableList<X509Certificate> = ArrayList()
    val revokedIntermediateCerts: MutableList<X509Certificate> = ArrayList()

    val rootCa: CertificateAndKeyPair = DEV_ROOT_CA

    private lateinit var _intermediateCa: CertificateAndKeyPair
    val intermediateCa: CertificateAndKeyPair get() = _intermediateCa

    @Volatile
    var delay: Duration? = null

    val hostAndPort: NetworkHostAndPort
        get() = server.connectors.mapNotNull { it as? ServerConnector }
                .map { NetworkHostAndPort(it.host, it.localPort) }
                .first()

    fun start() {
        server.start()
        _intermediateCa = CertificateAndKeyPair(
                DEV_INTERMEDIATE_CA.certificate.withCrlDistPoint(rootCa.keyPair, "http://$hostAndPort/crl/$INTERMEDIATE_CRL"),
                DEV_INTERMEDIATE_CA.keyPair
        )
        logger.info("Network management web services started on $hostAndPort")
    }

    fun replaceNodeCertDistPoint(nodeCaCert: X509Certificate,
                                 nodeCaCrlDistPoint: String? = "http://$hostAndPort/crl/$NODE_CRL",
                                 crlIssuer: X500Principal? = null): X509Certificate {
        return nodeCaCert.withCrlDistPoint(intermediateCa.keyPair, nodeCaCrlDistPoint, crlIssuer)
    }

    private fun createServerCRL(issuer: CertificateAndKeyPair,
                                endpoint: String,
                                indirect: Boolean,
                                revokedCerts: List<X509Certificate>): X509CRL {
        logger.info("Generating CRL for /$endpoint: ${revokedCerts.map { it.serialNumber }}")
        return createCRL(
                issuer,
                revokedCerts,
                issuingDistPoint = URI("http://$hostAndPort/crl/$endpoint"),
                indirect = indirect
        )
    }

    override fun close() {
        server.stop()
        server.join()
    }

    private fun buildServletContextHandler(): ServletContextHandler {
        return ServletContextHandler().apply {
            contextPath = "/"
            val resourceConfig = ResourceConfig().apply {
                register(CrlServlet(this@CrlServer))
            }
            val jerseyServlet = ServletHolder(ServletContainer(resourceConfig)).apply { initOrder = 0 }
            addServlet(jerseyServlet, "/*")
        }
    }

    @Path("crl")
    class CrlServlet(private val crlServer: CrlServer) {
        @GET
        @Path(NODE_CRL)
        @Produces("application/pkcs7-crl")
        fun getNodeCRL(): Response {
            crlServer.delay?.toMillis()?.let(Thread::sleep)
            return Response.ok(crlServer.createServerCRL(
                    crlServer.intermediateCa,
                    NODE_CRL,
                    false,
                    crlServer.revokedNodeCerts
            ).encoded).build()
        }

        @GET
        @Path(FORBIDDEN_CRL)
        @Produces("application/pkcs7-crl")
        fun getNodeSlowCRL(): Response {
            return Response.status(Response.Status.FORBIDDEN).build()
        }

        @GET
        @Path(INTERMEDIATE_CRL)
        @Produces("application/pkcs7-crl")
        fun getIntermediateCRL(): Response {
            crlServer.delay?.toMillis()?.let(Thread::sleep)
            return Response.ok(crlServer.createServerCRL(
                    crlServer.rootCa,
                    INTERMEDIATE_CRL,
                    false,
                    crlServer.revokedIntermediateCerts
            ).encoded).build()
        }

        @GET
        @Path(EMPTY_CRL)
        @Produces("application/pkcs7-crl")
        fun getEmptyCRL(): Response {
            return Response.ok(crlServer.createServerCRL(
                    crlServer.rootCa,
                    EMPTY_CRL,
                    true,
                    emptyList()
            ).encoded).build()
        }
    }
}
