package net.corda.core.crypto

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Test
import java.net.InetAddress
import java.security.KeyStore
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class X509NameConstraintsTest {

    private fun makeKeyStores(subjectName: X500Name, nameConstraints: NameConstraints): Pair<KeyStore, KeyStore> {
        val rootCA = X509Utilities.createSelfSignedCACert(X509Utilities.getDevX509Name("Corda Root CA"), Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))

        val intermediateCAKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val intermediateCACert = X509Utilities.createIntermediateCACert(X509Utilities.getDevX509Name("Corda Intermediate CA"), intermediateCAKeyPair.public, rootCA)
        val intermediateCA = CertificateAndKeyPair(intermediateCACert, intermediateCAKeyPair)

        val clientCAKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCACert = X509Utilities.createIntermediateCACert(X509Utilities.getDevX509Name("Corda Client CA"), clientCAKeyPair.public, intermediateCA, nameConstraints = nameConstraints)
        val clientCA = CertificateAndKeyPair(clientCACert, clientCAKeyPair)

        val keyPass = "password"
        val trustStore = KeyStore.getInstance(KeyStoreUtilities.KEYSTORE_TYPE)
        trustStore.load(null, keyPass.toCharArray())
        trustStore.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCA.certificate)

        val tlsKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val host = InetAddress.getLocalHost()
        val tlsCert = X509Utilities.createTLSCert(subjectName, tlsKey.public, clientCA, listOf(host.hostName), listOf(host.hostAddress))

        val keyStore = KeyStore.getInstance(KeyStoreUtilities.KEYSTORE_TYPE)
        keyStore.load(null, keyPass.toCharArray())
        keyStore.addOrReplaceKey(X509Utilities.CORDA_CLIENT_TLS, tlsKey.private, keyPass.toCharArray(), arrayOf(tlsCert, clientCA.certificate, intermediateCA.certificate, rootCA.certificate))
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
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A TLS, UID=, E=me@email.com, C=UK"), nameConstraints)
            val params = PKIXParameters(trustStore)
            params.isRevocationEnabled = false
            val certPath = certFactory.generateCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).asList())
            pathValidator.validate(certPath, params)
            true
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("O=Bank A, UID=, E=me@email.com, C=UK"), nameConstraints)
            val params = PKIXParameters(trustStore)
            params.isRevocationEnabled = false
            val certPath = certFactory.generateCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).asList())
            pathValidator.validate(certPath, params)
            true
        }

    }
}