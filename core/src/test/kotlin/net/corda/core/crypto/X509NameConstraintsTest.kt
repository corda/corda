package net.corda.core.crypto

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.toTypedArray
import net.corda.core.internal.cert
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
        val rootCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Corda Root CA", organisation = "R3 Ltd", locality = "London", country = "GB"), rootKeys)

        val intermediateCAKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootKeys, CordaX500Name(commonName = "Corda Intermediate CA", organisation = "R3 Ltd", locality = "London", country = "GB"), intermediateCAKeyPair.public)

        val clientCAKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, intermediateCACert, intermediateCAKeyPair, CordaX500Name(commonName = "Corda Client CA", organisation = "R3 Ltd", locality = "London", country = "GB"), clientCAKeyPair.public, nameConstraints = nameConstraints)

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
