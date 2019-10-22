package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.*
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v2CRLBuilder
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.security.KeyPairGenerator
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.Date
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeCertificatesTest : TestBase(KOTLIN) {
    companion object {
        // The sandbox's localisation may not match that of the host.
        // E.g. line separator characters.
        fun String.toUNIX(): String {
            return replace(System.lineSeparator(), "\n")
        }

        val factory: CertificateFactory = CertificateFactory.getInstance("X.509")
        lateinit var certificate: X509Certificate

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun loadCertificate() {
            certificate = this::class.java.classLoader.getResourceAsStream("testing.cert")?.use { input ->
                factory.generateCertificate(input) as X509Certificate
            } ?: fail("Certificate not found")
        }
    }

    @Test
    fun `test deserialize certificate path`() {
        val certPath = factory.generateCertPath(listOf(certificate))
        val data = certPath.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxCertPath = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showCertPath = classLoader.createTaskFor(taskFactory, ShowCertPath::class.java)
            val result = showCertPath.apply(sandboxCertPath) ?: fail("Result cannot be null")

            assertEquals(ShowCertPath().apply(certPath).toUNIX(), result.toString())
            assertThat(result::class.java.name).startsWith("sandbox.")
        }
    }

    class ShowCertPath : Function<CertPath, String> {
        override fun apply(certPath: CertPath): String {
            return "CertPath -> $certPath"
        }
    }

    @Test
    fun `test deserialize X509 certificate`() {
        val data = certificate.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxCertificate = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showCertificate = classLoader.createTaskFor(taskFactory, ShowCertificate::class.java)
            val result = showCertificate.apply(sandboxCertificate) ?: fail("Result cannot be null")

            assertEquals(ShowCertificate().apply(certificate).toUNIX(), result.toString())
            assertThat(result::class.java.name).startsWith("sandbox.")
        }
    }

    class ShowCertificate : Function<X509Certificate, String> {
        override fun apply(certificate: X509Certificate): String {
            return "X.509 Certificate -> $certificate"
        }
    }

    @Test
    fun `test X509 CRL`() {
        val caKeyPair = KeyPairGenerator.getInstance("RSA")
            .generateKeyPair()
        val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .build(caKeyPair.private)

        val now = Date()
        val crl = with(X509v2CRLBuilder(X500Name("CN=Test CA"), now)) {
            addCRLEntry(certificate.serialNumber, now, CRLReason.privilegeWithdrawn)
            JcaX509CRLConverter().getCRL(build(signer))
        }
        val data = crl.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxCRL = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showCRL = classLoader.createTaskFor(taskFactory, ShowCRL::class.java)
            val result = showCRL.apply(sandboxCRL) ?: fail("Result cannot be null")

            assertEquals(ShowCRL().apply(crl).toUNIX(), result.toString())
            assertThat(result::class.java.name).startsWith("sandbox.")
        }
    }

    class ShowCRL : Function<X509CRL, String> {
        override fun apply(crl: X509CRL): String {
            return "X.509 CRL -> $crl"
        }
    }
}
