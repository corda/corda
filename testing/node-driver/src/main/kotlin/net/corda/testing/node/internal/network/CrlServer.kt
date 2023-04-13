@file:Suppress("MagicNumber")

package net.corda.testing.node.internal.network

import net.corda.core.crypto.Crypto
import net.corda.core.internal.CertRole
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.days
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.coretesting.internal.DEV_INTERMEDIATE_CA
import net.corda.coretesting.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.ContentSignerBuilder
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.certificateType
import net.corda.nodeapi.internal.crypto.toJca
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.IssuingDistributionPoint
import org.bouncycastle.asn1.x509.ReasonFlags
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import java.io.Closeable
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPair
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.*
import javax.security.auth.x500.X500Principal
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.collections.ArrayList

class CrlServer(hostAndPort: NetworkHostAndPort) : Closeable {
    companion object {
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

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
                val distPointName = DistributionPointName(GeneralNames(GeneralName(GeneralName.uniformResourceIdentifier, crlDistPoint)))
                val crlIssuerGeneralNames = crlIssuer?.let { GeneralNames(GeneralName(X500Name.getInstance(it.encoded))) }
                val distPoint = DistributionPoint(distPointName, null, crlIssuerGeneralNames)
                builder.addExtension(Extension.cRLDistributionPoints, false, CRLDistPoint(arrayOf(distPoint)))
            }
            return builder.build(issuerSigner).toJca()
        }
    }

    private val server: Server = Server(InetSocketAddress(hostAndPort.host, hostAndPort.port)).apply {
        handler = HandlerCollection().apply {
            addHandler(buildServletContextHandler())
        }
    }

    val revokedNodeCerts: MutableList<BigInteger> = ArrayList()
    val revokedIntermediateCerts: MutableList<BigInteger> = ArrayList()

    val rootCa: CertificateAndKeyPair = DEV_ROOT_CA

    private lateinit var _intermediateCa: CertificateAndKeyPair
    val intermediateCa: CertificateAndKeyPair get() = _intermediateCa

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
        println("Network management web services started on $hostAndPort")
    }

    fun replaceNodeCertDistPoint(nodeCaCert: X509Certificate,
                                 nodeCaCrlDistPoint: String? = "http://$hostAndPort/crl/$NODE_CRL",
                                 crlIssuer: X500Principal? = null): X509Certificate {
        return nodeCaCert.withCrlDistPoint(intermediateCa.keyPair, nodeCaCrlDistPoint, crlIssuer)
    }

    fun createRevocationList(signatureAlgorithm: String,
                             ca: CertificateAndKeyPair,
                             endpoint: String,
                             indirect: Boolean,
                             serialNumbers: List<BigInteger>): X509CRL {
        println("Generating CRL for $endpoint")
        val builder = JcaX509v2CRLBuilder(ca.certificate.subjectX500Principal, Date(System.currentTimeMillis() - 1.minutes.toMillis()))
        val extensionUtils = JcaX509ExtensionUtils()
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensionUtils.createAuthorityKeyIdentifier(ca.certificate))
        val issuingDistPointName = GeneralName(GeneralName.uniformResourceIdentifier, "http://$hostAndPort/crl/$endpoint")
        // This is required and needs to match the certificate settings with respect to being indirect
        val issuingDistPoint = IssuingDistributionPoint(DistributionPointName(GeneralNames(issuingDistPointName)), indirect, false)
        builder.addExtension(Extension.issuingDistributionPoint, true, issuingDistPoint)
        builder.setNextUpdate(Date(System.currentTimeMillis() + 1.seconds.toMillis()))
        serialNumbers.forEach {
            builder.addCRLEntry(it, Date(System.currentTimeMillis() - 10.minutes.toMillis()), ReasonFlags.certificateHold)
        }
        val signer = JcaContentSignerBuilder(signatureAlgorithm).setProvider(Crypto.findProvider("BC")).build(ca.keyPair.private)
        return JcaX509CRLConverter().setProvider(Crypto.findProvider("BC")).getCRL(builder.build(signer))
    }

    override fun close() {
        println("Shutting down network management web services...")
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
            return Response.ok(crlServer.createRevocationList(
                    SIGNATURE_ALGORITHM,
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
            return Response.ok(crlServer.createRevocationList(
                    SIGNATURE_ALGORITHM,
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
            return Response.ok(crlServer.createRevocationList(
                    SIGNATURE_ALGORITHM,
                    crlServer.rootCa,
                    EMPTY_CRL,
                    true, emptyList()
            ).encoded).build()
        }
    }
}
