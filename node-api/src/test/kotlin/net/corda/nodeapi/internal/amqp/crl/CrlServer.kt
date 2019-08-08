package net.corda.nodeapi.internal.amqp.crl

import net.corda.core.crypto.Crypto
import net.corda.core.utilities.*
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.crypto.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
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
import java.security.PrivateKey
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

/**
 * In-process HTTP server which helps functional testing of CRL functionality.
 */
class CrlServer(hostAndPort: NetworkHostAndPort, private val crlServerHitCount: AtomicInteger, private val ROOT_CA: CertificateAndKeyPair, private val intermediateCaFunc: () -> CertificateAndKeyPair,
                private val revokedNodeCerts: MutableSet<BigInteger>, private val revokedIntermediateCerts: MutableSet<BigInteger>) : Closeable {

    companion object {
        private val logger = contextLogger()
    }

    private val server: Server = Server(InetSocketAddress(hostAndPort.host, hostAndPort.port))
            .apply {
        handler = HandlerCollection().apply {
            addHandler(buildServletContextHandler())
        }
    }

    val hostAndPort: NetworkHostAndPort
        get() = server.connectors.mapNotNull { it as? ServerConnector }
                .map { NetworkHostAndPort(it.host, it.localPort) }
                .first()

    override fun close() {
        logger.info("Shutting down network management web services...")
        server.stop()
        server.join()
    }

    fun start() {
        server.start()
        logger.info("Network management web services started on $hostAndPort")
    }

    private fun buildServletContextHandler(): ServletContextHandler {
        val crlServer = this
        return ServletContextHandler().apply {
            contextPath = "/"
            val resourceConfig = ResourceConfig().apply {
                register(CrlServlet(crlServer, crlServerHitCount, ROOT_CA, intermediateCaFunc, revokedNodeCerts, revokedIntermediateCerts))
            }
            val jerseyServlet = ServletHolder(ServletContainer(resourceConfig)).apply { initOrder = 0 }
            addServlet(jerseyServlet, "/*")
        }
    }
}

/**
 * A servlet to work along side of [CrlServer]
 */
@Path("crl")
class CrlServlet(private val server: CrlServer, private val crlServerHitCount: AtomicInteger, private val ROOT_CA: CertificateAndKeyPair, intermediateCaFunc: () -> CertificateAndKeyPair,
                 private val revokedNodeCerts: MutableSet<BigInteger>, private val revokedIntermediateCerts: MutableSet<BigInteger>) {

    companion object {
        const val FORBIDDEN_CRL = "forbidden.crl"
        const val NODE_CRL = "node.crl"
        const val INTERMEDIATE_CRL = "intermediate.crl"
        const val EMPTY_CRL = "empty.crl"

        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

        private val logger = contextLogger()

        fun createRevocationList(clrServer: CrlServer, signatureAlgorithm: String, caCertificate: X509Certificate,
                                 caPrivateKey: PrivateKey,
                                 endpoint: String,
                                 indirect: Boolean,
                                 serialNumbers: Set<BigInteger>): X509CRL {
            logger.info("Generating CRL for $endpoint")
            val builder = JcaX509v2CRLBuilder(caCertificate.subjectX500Principal, Date(System.currentTimeMillis() - 1.minutes.toMillis()))
            val extensionUtils = JcaX509ExtensionUtils()
            builder.addExtension(Extension.authorityKeyIdentifier,
                    false, extensionUtils.createAuthorityKeyIdentifier(caCertificate))
            val issuingDistPointName = GeneralName(
                    GeneralName.uniformResourceIdentifier,
                    "http://${clrServer.hostAndPort.host}:${clrServer.hostAndPort.port}/crl/$endpoint")
            // This is required and needs to match the certificate settings with respect to being indirect
            val issuingDistPoint = IssuingDistributionPoint(DistributionPointName(GeneralNames(issuingDistPointName)), indirect, false)
            builder.addExtension(Extension.issuingDistributionPoint, true, issuingDistPoint)
            builder.setNextUpdate(Date(System.currentTimeMillis() + 1.seconds.toMillis()))
            serialNumbers.forEach {
                builder.addCRLEntry(it, Date(System.currentTimeMillis() - 10.minutes.toMillis()), ReasonFlags.certificateHold)
            }
            val signer = JcaContentSignerBuilder(signatureAlgorithm).setProvider(Crypto.findProvider("BC")).build(caPrivateKey)
            return JcaX509CRLConverter().setProvider(Crypto.findProvider("BC")).getCRL(builder.build(signer))
        }

        fun replaceCrlDistPointCaCertificate(currentCaCert: X509Certificate, certType: CertificateType, issuerKeyPair: KeyPair, crlDistPoint: String?, crlIssuer: X500Name? = null): X509Certificate {
            val signatureScheme = Crypto.findSignatureScheme(issuerKeyPair.private)
            val provider = Crypto.findProvider(signatureScheme.providerName)
            val issuerSigner = ContentSignerBuilder.build(signatureScheme, issuerKeyPair.private, provider)
            val builder = X509Utilities.createPartialCertificate(
                    certType,
                    currentCaCert.issuerX500Principal,
                    issuerKeyPair.public,
                    currentCaCert.subjectX500Principal,
                    currentCaCert.publicKey,
                    Pair(Date(System.currentTimeMillis() - 5.minutes.toMillis()), Date(System.currentTimeMillis() + 10.days.toMillis())),
                    null
            )
            crlDistPoint?.let {
                val distPointName = DistributionPointName(GeneralNames(GeneralName(GeneralName.uniformResourceIdentifier, it)))
                val crlIssuerGeneralNames = crlIssuer?.let {
                    GeneralNames(GeneralName(crlIssuer))
                }
                val distPoint = DistributionPoint(distPointName, null, crlIssuerGeneralNames)
                builder.addExtension(Extension.cRLDistributionPoints, false, CRLDistPoint(arrayOf(distPoint)))
            }
            return builder.build(issuerSigner).toJca()
        }

        fun Pair<CertificateStoreSupplier, MutualSslConfiguration>.recreateNodeCaAndTlsCertificates(nodeCaCrlDistPoint: String, tlsCrlDistPoint: String?, ROOT_CA: CertificateAndKeyPair, INTERMEDIATE_CA: CertificateAndKeyPair): X509Certificate {

            val (signingCertificateStore, p2pSslConfiguration) = this
            val nodeKeyStore = signingCertificateStore.get()
            val (nodeCert, nodeKeys) = nodeKeyStore.query { getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA, nodeKeyStore.entryPassword) }
            val newNodeCert = replaceCrlDistPointCaCertificate(nodeCert, CertificateType.NODE_CA, INTERMEDIATE_CA.keyPair, nodeCaCrlDistPoint)
            val nodeCertChain = listOf(newNodeCert, INTERMEDIATE_CA.certificate, *nodeKeyStore.query { getCertificateChain(X509Utilities.CORDA_CLIENT_CA) }.drop(2).toTypedArray())
            nodeKeyStore.update {
                internal.deleteEntry(X509Utilities.CORDA_CLIENT_CA)
            }
            nodeKeyStore.update {
                setPrivateKey(X509Utilities.CORDA_CLIENT_CA, nodeKeys.private, nodeCertChain, nodeKeyStore.entryPassword)
            }
            val sslKeyStore = p2pSslConfiguration.keyStore.get()
            val (tlsCert, tlsKeys) = sslKeyStore.query { getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_TLS, sslKeyStore.entryPassword) }
            val newTlsCert = replaceCrlDistPointCaCertificate(tlsCert, CertificateType.TLS, nodeKeys, tlsCrlDistPoint, X500Name.getInstance(ROOT_CA.certificate.subjectX500Principal.encoded))
            val sslCertChain = listOf(newTlsCert, newNodeCert, INTERMEDIATE_CA.certificate, *sslKeyStore.query { getCertificateChain(X509Utilities.CORDA_CLIENT_TLS) }.drop(3).toTypedArray())

            sslKeyStore.update {
                internal.deleteEntry(X509Utilities.CORDA_CLIENT_TLS)
            }
            sslKeyStore.update {
                setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKeys.private, sslCertChain, sslKeyStore.entryPassword)
            }
            return newNodeCert
        }
    }

    private val intermediateCa by lazy(intermediateCaFunc)

    @GET
    @Path(NODE_CRL)
    @Produces("application/pkcs7-crl")
    fun getNodeCRL(): Response {
        crlServerHitCount.incrementAndGet()
        return Response.ok(createRevocationList(
                server,
                SIGNATURE_ALGORITHM,
                intermediateCa.certificate,
                intermediateCa.keyPair.private,
                NODE_CRL,
                false,
                revokedNodeCerts).encoded)
                .build()
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
        crlServerHitCount.incrementAndGet()
        return Response.ok(createRevocationList(
                server,
                SIGNATURE_ALGORITHM,
                ROOT_CA.certificate,
                ROOT_CA.keyPair.private,
                INTERMEDIATE_CRL,
                false,
                revokedIntermediateCerts).encoded)
                .build()
    }

    @GET
    @Path(EMPTY_CRL)
    @Produces("application/pkcs7-crl")
    fun getEmptyCRL(): Response {
        crlServerHitCount.incrementAndGet()
        return Response.ok(createRevocationList(
                server,
                SIGNATURE_ALGORITHM,
                ROOT_CA.certificate,
                ROOT_CA.keyPair.private,
                EMPTY_CRL,
                true,
                emptySet()).encoded)
                .build()
    }
}