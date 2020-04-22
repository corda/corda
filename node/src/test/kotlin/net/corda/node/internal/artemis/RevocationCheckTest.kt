package net.corda.node.internal.artemis

import net.corda.core.crypto.Crypto
import net.corda.core.utilities.days
import net.corda.node.internal.artemis.CertificateChainCheckPolicy.RevocationCheck
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.IssuingDistributionPoint
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.*
import javax.security.auth.x500.X500Principal
import kotlin.test.assertFails

@RunWith(Parameterized::class)
class RevocationCheckTest(private val revocationMode: RevocationConfig.Mode) {
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

    private val keyStore = KeyStore.getInstance("JKS")
    private val trustStore = KeyStore.getInstance("JKS")

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
        get() = listOf(tlsCert, nodeCACert, doormanCert, rootCert).map {
            javax.security.cert.X509Certificate.getInstance(it.encoded)
        }.toTypedArray()

    @Before
    fun before() {
        rootCRL = tempFolder.newFile("root.crl")
        doormanCRL = tempFolder.newFile("doorman.crl")
        tlsCRL = tempFolder.newFile("tls.crl")

        rootCert = X509Utilities.createSelfSignedCACertificate(X500Principal("CN=root"), rootKeyPair)
        tlsCRLIssuerCert = X509Utilities.createSelfSignedCACertificate(X500Principal("CN=issuer"), tlsCRLIssuerKeyPair)

        trustStore.load(null, null)
        trustStore.setCertificateEntry("cordatlscrlsigner", tlsCRLIssuerCert)
        trustStore.setCertificateEntry("cordarootca", rootCert)

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

        rootCRL.createCRL(rootCert, rootKeyPair.private, false)
        doormanCRL.createCRL(doormanCert, doormanKeyPair.private, false)
        tlsCRL.createCRL(tlsCRLIssuerCert, tlsCRLIssuerKeyPair.private, true)
    }

    private fun File.createCRL(certificate: X509Certificate, privateKey: PrivateKey, indirect: Boolean, vararg revoked: X509Certificate) {
        val builder = JcaX509v2CRLBuilder(certificate.subjectX500Principal, Date())
        builder.setNextUpdate(Date.from(Date().toInstant() + 7.days))
        builder.addExtension(Extension.issuingDistributionPoint, true, IssuingDistributionPoint(null, indirect, false))
        revoked.forEach {
            val extensionsGenerator = ExtensionsGenerator()
            extensionsGenerator.addExtension(Extension.reasonCode, false, CRLReason.lookup(CRLReason.keyCompromise))
            // Certificate issuer is required for indirect CRL
            val certificateIssuerName = X500Name.getInstance(it.issuerX500Principal.encoded)
            extensionsGenerator.addExtension(Extension.certificateIssuer, true, GeneralNames(GeneralName(certificateIssuerName)))
            builder.addCRLEntry(it.serialNumber, Date(), extensionsGenerator.generate())
        }
        val holder = builder.build(JcaContentSignerBuilder("SHA256withECDSA").setProvider(Crypto.findProvider("BC")).build(privateKey))
        outputStream().use { it.write(holder.encoded) }
    }

    private fun assertFailsFor(vararg modes: RevocationConfig.Mode, block: () -> Unit) {
        if (revocationMode in modes) assertFails(block) else block()
    }

    @Test(timeout = 300_000)
    fun `ok with empty CRLs`() {
        RevocationCheck(revocationMode).createCheck(keyStore, trustStore).checkCertificateChain(chain)
    }

    @Test(timeout = 300_000)
    fun `soft fail with revoked TLS certificate`() {
        tlsCRL.createCRL(tlsCRLIssuerCert, tlsCRLIssuerKeyPair.private, true, tlsCert)

        assertFailsFor(RevocationConfig.Mode.SOFT_FAIL, RevocationConfig.Mode.HARD_FAIL) {
            RevocationCheck(revocationMode).createCheck(keyStore, trustStore).checkCertificateChain(chain)
        }
    }

    @Test(timeout = 300_000)
    fun `hard fail with unavailable CRL in TLS certificate`() {
        tlsCert = X509Utilities.createCertificate(
                CertificateType.TLS, nodeCACert, nodeCAKeyPair, X500Principal("CN=tls"), tlsKeyPair.public,
                crlDistPoint = "http://unknown-host:10000/certificate-revocation-list/tls",
                crlIssuer = X500Name.getInstance(tlsCRLIssuerCert.issuerX500Principal.encoded)
        )

        assertFailsFor(RevocationConfig.Mode.HARD_FAIL) {
            RevocationCheck(revocationMode).createCheck(keyStore, trustStore).checkCertificateChain(chain)
        }
    }

    @Test(timeout = 300_000)
    fun `hard fail with invalid CRL issuer in TLS certificate`() {
        tlsCert = X509Utilities.createCertificate(
                CertificateType.TLS, nodeCACert, nodeCAKeyPair, X500Principal("CN=tls"), tlsKeyPair.public,
                crlDistPoint = tlsCRL.toURI().toString(), crlIssuer = X500Name("CN=unknown")
        )

        assertFailsFor(RevocationConfig.Mode.HARD_FAIL) {
            RevocationCheck(revocationMode).createCheck(keyStore, trustStore).checkCertificateChain(chain)
        }
    }

    @Test(timeout = 300_000)
    fun `hard fail without CRL issuer in TLS certificate`() {
        tlsCert = X509Utilities.createCertificate(
                CertificateType.TLS, nodeCACert, nodeCAKeyPair, X500Principal("CN=tls"), tlsKeyPair.public,
                crlDistPoint = tlsCRL.toURI().toString()
        )

        assertFailsFor(RevocationConfig.Mode.HARD_FAIL) {
            RevocationCheck(revocationMode).createCheck(keyStore, trustStore).checkCertificateChain(chain)
        }
    }

    @Test(timeout = 300_000)
    fun `ok with other certificate in TLS CRL`() {
        val otherKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val otherCert = X509Utilities.createCertificate(
                CertificateType.TLS, nodeCACert, nodeCAKeyPair, X500Principal("CN=other"), otherKeyPair.public,
                crlDistPoint = tlsCRL.toURI().toString(), crlIssuer = X500Name.getInstance(tlsCRLIssuerCert.issuerX500Principal.encoded)
        )
        tlsCRL.createCRL(tlsCRLIssuerCert, tlsCRLIssuerKeyPair.private, true, otherCert)

        RevocationCheck(revocationMode).createCheck(keyStore, trustStore).checkCertificateChain(chain)
    }

    @Test(timeout = 300_000)
    fun `soft fail with revoked node CA certificate`() {
        doormanCRL.createCRL(doormanCert, doormanKeyPair.private, false, nodeCACert)

        assertFailsFor(RevocationConfig.Mode.SOFT_FAIL, RevocationConfig.Mode.HARD_FAIL) {
            RevocationCheck(revocationMode).createCheck(keyStore, trustStore).checkCertificateChain(chain)
        }
    }

    @Test(timeout = 300_000)
    fun `hard fail with unavailable CRL in node CA certificate`() {
        nodeCACert = X509Utilities.createCertificate(
                CertificateType.NODE_CA, doormanCert, doormanKeyPair, X500Principal("CN=node"), nodeCAKeyPair.public,
                crlDistPoint = "http://unknown-host:10000/certificate-revocation-list/doorman"
        )

        assertFailsFor(RevocationConfig.Mode.HARD_FAIL) {
            RevocationCheck(revocationMode).createCheck(keyStore, trustStore).checkCertificateChain(chain)
        }
    }

    @Test(timeout = 300_000)
    fun `ok with other certificate in doorman CRL`() {
        val otherKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val otherCert = X509Utilities.createCertificate(
                CertificateType.NODE_CA, doormanCert, doormanKeyPair, X500Principal("CN=other"), otherKeyPair.public,
                crlDistPoint = doormanCRL.toURI().toString()
        )
        doormanCRL.createCRL(doormanCert, doormanKeyPair.private, false, otherCert)

        RevocationCheck(revocationMode).createCheck(keyStore, trustStore).checkCertificateChain(chain)
    }
}
