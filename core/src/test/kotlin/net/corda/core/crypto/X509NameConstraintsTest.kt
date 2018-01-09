package net.corda.core.crypto

import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.*
import net.corda.testing.internal.createDevIntermediateCaCertPath
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Test
import java.security.KeyStore
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.PKIXParameters
import javax.security.auth.x500.X500Principal
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class X509NameConstraintsTest {

    private fun makeKeyStores(subjectName: X500Name, nameConstraints: NameConstraints): Pair<KeyStore, KeyStore> {
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()
        val nodeCaKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val nodeCaCert = X509Utilities.createCertificate(
                CertificateType.NODE_CA,
                intermediateCa.certificate,
                intermediateCa.keyPair,
                CordaX500Name("Corda Client CA", "R3 Ltd", "London", "GB").x500Principal,
                nodeCaKeyPair.public,
                nameConstraints = nameConstraints)

        val keyPass = "password"
        val trustStore = KeyStore.getInstance(KEYSTORE_TYPE)
        trustStore.load(null, keyPass.toCharArray())
        trustStore.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCa.certificate)

        val tlsKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val tlsCert = X509Utilities.createCertificate(
                CertificateType.TLS,
                nodeCaCert,
                nodeCaKeyPair,
                X500Principal(subjectName.encoded),
                tlsKeyPair.public)

        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        keyStore.load(null, keyPass.toCharArray())
        keyStore.addOrReplaceKey(
                X509Utilities.CORDA_CLIENT_TLS,
                tlsKeyPair.private,
                keyPass.toCharArray(),
                arrayOf(tlsCert, nodeCaCert, intermediateCa.certificate, rootCa.certificate))
        return Pair(keyStore, trustStore)
    }

    @Test
    fun `illegal common name`() {
        val acceptableNames = listOf("CN=Bank A TLS, O=Bank A", "CN=Bank A")
                .map { GeneralSubtree(GeneralName(X500Name(it))) }.toTypedArray()

        val nameConstraints = NameConstraints(acceptableNames, arrayOf())
        val pathValidator = CertPathValidator.getInstance("PKIX")
        val certFactory = X509CertificateFactory()

        assertFailsWith(CertPathValidatorException::class) {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank B"), nameConstraints)
            val params = PKIXParameters(trustStore)
            params.isRevocationEnabled = false
            val certPath = certFactory.generateCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).asList())
            pathValidator.validate(certPath, params)
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A TLS, O=Bank A"), nameConstraints)
            val params = PKIXParameters(trustStore)
            params.isRevocationEnabled = false
            val certPath = certFactory.generateCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).asList())
            pathValidator.validate(certPath, params)
            true
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A"), nameConstraints)
            val params = PKIXParameters(trustStore)
            params.isRevocationEnabled = false
            val certPath = certFactory.generateCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).asList())
            pathValidator.validate(certPath, params)
            true
        }
    }

    @Test
    fun `x500 name with correct cn and extra attribute`() {
        val acceptableNames = listOf("CN=Bank A TLS, UID=", "O=Bank A")
                .map { GeneralSubtree(GeneralName(X500Name(it))) }.toTypedArray()

        val nameConstraints = NameConstraints(acceptableNames, arrayOf())
        val certFactory = X509CertificateFactory().delegate
        Crypto.ECDSA_SECP256R1_SHA256
        val pathValidator = CertPathValidator.getInstance("PKIX", BouncyCastleProvider.PROVIDER_NAME)

        assertFailsWith(CertPathValidatorException::class) {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A"), nameConstraints)
            val params = PKIXParameters(trustStore)
            params.isRevocationEnabled = false
            val certPath = certFactory.generateCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).asList())
            pathValidator.validate(certPath, params)
        }

        assertFailsWith(CertPathValidatorException::class) {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A, UID=12345"), nameConstraints)
            val params = PKIXParameters(trustStore)
            params.isRevocationEnabled = false
            val certPath = certFactory.generateCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).asList())
            pathValidator.validate(certPath, params)
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A TLS, UID=, E=me@email.com, C=GB"), nameConstraints)
            val params = PKIXParameters(trustStore)
            params.isRevocationEnabled = false
            val certPath = certFactory.generateCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).asList())
            pathValidator.validate(certPath, params)
            true
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("O=Bank A, UID=, E=me@email.com, C=GB"), nameConstraints)
            val params = PKIXParameters(trustStore)
            params.isRevocationEnabled = false
            val certPath = certFactory.generateCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).asList())
            pathValidator.validate(certPath, params)
            true
        }

    }
}
