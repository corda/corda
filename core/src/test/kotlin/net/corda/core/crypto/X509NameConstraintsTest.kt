package net.corda.core.crypto

import net.corda.core.internal.toTypedArray
import net.corda.node.utilities.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Test
import java.security.KeyStore
import java.security.cert.*
import java.util.stream.Stream
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class X509NameConstraintsTest {

    private fun makeKeyStores(subjectName: X500Name, nameConstraints: NameConstraints): Pair<KeyStore, KeyStore> {
        val rootKeys = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val rootCACert = X509Utilities.createSelfSignedCACertificate(getX509Name("Corda Root CA", "London", "demo@r3.com", null), rootKeys)

        val intermediateCAKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootKeys, getX509Name("Corda Intermediate CA", "London", "demo@r3.com", null), intermediateCAKeyPair.public)

        val clientCAKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, intermediateCACert, intermediateCAKeyPair, getX509Name("Corda Client CA", "London", "demo@r3.com", null), clientCAKeyPair.public, nameConstraints = nameConstraints)

        val keyPass = "password"
        val trustStore = KeyStore.getInstance(KEYSTORE_TYPE)
        trustStore.load(null, keyPass.toCharArray())
        trustStore.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCACert.cert)

        val tlsKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, clientCACert, clientCAKeyPair, subjectName, tlsKey.public)

        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        keyStore.load(null, keyPass.toCharArray())
        keyStore.addOrReplaceKey(X509Utilities.CORDA_CLIENT_TLS, tlsKey.private, keyPass.toCharArray(),
                Stream.of(tlsCert, clientCACert, intermediateCACert, rootCACert).map { it.cert }.toTypedArray<Certificate>())
        return Pair(keyStore, trustStore)
    }

    @Test
    fun `illegal common name`() {
        val acceptableNames = listOf("CN=Bank A TLS, O=Bank A", "CN=Bank A")
                .map { GeneralSubtree(GeneralName(X500Name(it))) }.toTypedArray()

        val nameConstraints = NameConstraints(acceptableNames, arrayOf())
        val pathValidator = CertPathValidator.getInstance("PKIX")
        val certFactory = CertificateFactory.getInstance("X509")

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
        val certFactory = CertificateFactory.getInstance("X509")
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
