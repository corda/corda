package net.corda.nodeapi.internal.revocation

import net.corda.core.crypto.Crypto
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfigImpl
import net.corda.nodeapi.internal.protonwrapper.netty.trustManagerFactoryWithRevocation
import net.corda.testing.core.createCRL
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal
import kotlin.test.assertFailsWith

@RunWith(Parameterized::class)
class RevocationTest(private val revocationMode: RevocationConfig.Mode) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "revocationMode = {0}")
        fun data() = listOf(RevocationConfig.Mode.OFF, RevocationConfig.Mode.SOFT_FAIL, RevocationConfig.Mode.HARD_FAIL)
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var rootCRL: File
    private lateinit var doormanCRL: File
    private lateinit var tlsCRL: File

    private lateinit var trustManager: X509TrustManager

    private val rootKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
    private val tlsCRLIssuerKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
    private val doormanKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
    private val nodeCAKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
    private val tlsKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)

    private lateinit var rootCert: X509Certificate
    private lateinit var tlsCRLIssuerCert: X509Certificate
    private lateinit var doormanCert: X509Certificate
    private lateinit var nodeCACert: X509Certificate
    private lateinit var tlsCert: X509Certificate

    private val chain
        get() = arrayOf(tlsCert, nodeCACert, doormanCert, rootCert)

    @Before
    fun before() {
        rootCRL = tempFolder.newFile("root.crl")
        doormanCRL = tempFolder.newFile("doorman.crl")
        tlsCRL = tempFolder.newFile("tls.crl")

        rootCert = X509Utilities.createSelfSignedCACertificate(X500Principal("CN=root"), rootKeyPair)
        tlsCRLIssuerCert = X509Utilities.createSelfSignedCACertificate(X500Principal("CN=issuer"), tlsCRLIssuerKeyPair)

        val trustStore = KeyStore.getInstance("JKS")
        trustStore.load(null, null)
        trustStore.setCertificateEntry("cordatlscrlsigner", tlsCRLIssuerCert)
        trustStore.setCertificateEntry("cordarootca", rootCert)

        val trustManagerFactory = trustManagerFactoryWithRevocation(
                CertificateStore.of(X509KeyStore(trustStore, "pass"), "pass", "pass"),
                RevocationConfigImpl(revocationMode),
                CertDistPointCrlSource()
        )
        trustManager = trustManagerFactory.trustManagers.single() as X509TrustManager

        doormanCert = X509Utilities.createCertificate(
                CertificateType.INTERMEDIATE_CA, rootCert, rootKeyPair, X500Principal("CN=doorman"), doormanKeyPair.public,
                crlDistPoint = rootCRL.toURI().toString()
        )
        nodeCACert = X509Utilities.createCertificate(
                CertificateType.NODE_CA, doormanCert, doormanKeyPair, X500Principal("CN=node"), nodeCAKeyPair.public,
                crlDistPoint = doormanCRL.toURI().toString()
        )
        tlsCert = X509Utilities.createCertificate(
                CertificateType.TLS, nodeCACert, nodeCAKeyPair, X500Principal("CN=tls"), tlsKeyPair.public,
                crlDistPoint = tlsCRL.toURI().toString(), crlIssuer = X500Name.getInstance(tlsCRLIssuerCert.issuerX500Principal.encoded)
        )

        rootCRL.writeCRL(rootCert, rootKeyPair.private, false)
        doormanCRL.writeCRL(doormanCert, doormanKeyPair.private, false)
        tlsCRL.writeCRL(tlsCRLIssuerCert, tlsCRLIssuerKeyPair.private, true)
    }

    private fun File.writeCRL(certificate: X509Certificate, privateKey: PrivateKey, indirect: Boolean, vararg revoked: X509Certificate) {
        val crl = createCRL(
                CertificateAndKeyPair(certificate, KeyPair(certificate.publicKey, privateKey)),
                revoked.asList(),
                indirect = indirect
        )
        writeBytes(crl.encoded)
    }

    private fun assertFailsFor(vararg modes: RevocationConfig.Mode) {
        if (revocationMode in modes) assertFailsWith(CertificateException::class, ::doRevocationCheck) else doRevocationCheck()
    }

    @Test(timeout = 300_000)
    fun `ok with empty CRLs`() {
        doRevocationCheck()
    }

    @Test(timeout = 300_000)
    fun `soft fail with revoked TLS certificate`() {
        tlsCRL.writeCRL(tlsCRLIssuerCert, tlsCRLIssuerKeyPair.private, true, tlsCert)

        assertFailsFor(RevocationConfig.Mode.SOFT_FAIL, RevocationConfig.Mode.HARD_FAIL)
    }

    @Test(timeout = 300_000)
    fun `hard fail with unavailable CRL in TLS certificate`() {
        tlsCert = X509Utilities.createCertificate(
                CertificateType.TLS, nodeCACert, nodeCAKeyPair, X500Principal("CN=tls"), tlsKeyPair.public,
                crlDistPoint = "http://unknown-host:10000/certificate-revocation-list/tls",
                crlIssuer = X500Name.getInstance(tlsCRLIssuerCert.issuerX500Principal.encoded)
        )

        assertFailsFor(RevocationConfig.Mode.HARD_FAIL)
    }

    @Test(timeout = 300_000)
    fun `hard fail with invalid CRL issuer in TLS certificate`() {
        tlsCert = X509Utilities.createCertificate(
                CertificateType.TLS, nodeCACert, nodeCAKeyPair, X500Principal("CN=tls"), tlsKeyPair.public,
                crlDistPoint = tlsCRL.toURI().toString(), crlIssuer = X500Name("CN=unknown")
        )

        assertFailsFor(RevocationConfig.Mode.HARD_FAIL)
    }

    @Test(timeout = 300_000)
    fun `hard fail without CRL issuer in TLS certificate`() {
        tlsCert = X509Utilities.createCertificate(
                CertificateType.TLS, nodeCACert, nodeCAKeyPair, X500Principal("CN=tls"), tlsKeyPair.public,
                crlDistPoint = tlsCRL.toURI().toString()
        )

        assertFailsFor(RevocationConfig.Mode.HARD_FAIL)
    }

    @Test(timeout = 300_000)
    fun `ok with other certificate in TLS CRL`() {
        val otherKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val otherCert = X509Utilities.createCertificate(
                CertificateType.TLS, nodeCACert, nodeCAKeyPair, X500Principal("CN=other"), otherKeyPair.public,
                crlDistPoint = tlsCRL.toURI().toString(), crlIssuer = X500Name.getInstance(tlsCRLIssuerCert.issuerX500Principal.encoded)
        )
        tlsCRL.writeCRL(tlsCRLIssuerCert, tlsCRLIssuerKeyPair.private, true, otherCert)

        doRevocationCheck()
    }

    @Test(timeout = 300_000)
    fun `soft fail with revoked node CA certificate`() {
        doormanCRL.writeCRL(doormanCert, doormanKeyPair.private, false, nodeCACert)

        assertFailsFor(RevocationConfig.Mode.SOFT_FAIL, RevocationConfig.Mode.HARD_FAIL)
    }

    @Test(timeout = 300_000)
    fun `hard fail with unavailable CRL in node CA certificate`() {
        nodeCACert = X509Utilities.createCertificate(
                CertificateType.NODE_CA, doormanCert, doormanKeyPair, X500Principal("CN=node"), nodeCAKeyPair.public,
                crlDistPoint = "http://unknown-host:10000/certificate-revocation-list/doorman"
        )

        assertFailsFor(RevocationConfig.Mode.HARD_FAIL)
    }

    @Test(timeout = 300_000)
    fun `ok with other certificate in doorman CRL`() {
        val otherKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val otherCert = X509Utilities.createCertificate(
                CertificateType.NODE_CA, doormanCert, doormanKeyPair, X500Principal("CN=other"), otherKeyPair.public,
                crlDistPoint = doormanCRL.toURI().toString()
        )
        doormanCRL.writeCRL(doormanCert, doormanKeyPair.private, false, otherCert)

        doRevocationCheck()
    }

    private fun doRevocationCheck() {
        trustManager.checkClientTrusted(chain, "ECDHE_ECDSA")
    }
}
