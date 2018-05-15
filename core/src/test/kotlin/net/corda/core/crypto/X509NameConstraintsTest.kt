/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.crypto

import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.internal.createDevIntermediateCaCertPath
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Test
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.PKIXParameters
import javax.security.auth.x500.X500Principal
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class X509NameConstraintsTest {

    private fun makeKeyStores(subjectName: X500Name, nameConstraints: NameConstraints): Pair<X509KeyStore, X509KeyStore> {
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()

        val trustStore = X509KeyStore("password").apply {
            setCertificate(X509Utilities.CORDA_ROOT_CA, rootCa.certificate)
        }

        val keyStore = X509KeyStore("password").apply {
            val nodeCaKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val nodeCaCert = X509Utilities.createCertificate(
                    CertificateType.NODE_CA,
                    intermediateCa.certificate,
                    intermediateCa.keyPair,
                    CordaX500Name("Corda Client CA", "R3 Ltd", "London", "GB").x500Principal,
                    nodeCaKeyPair.public,
                    nameConstraints = nameConstraints)
            val tlsKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val tlsCert = X509Utilities.createCertificate(
                    CertificateType.TLS,
                    nodeCaCert,
                    nodeCaKeyPair,
                    X500Principal(subjectName.encoded),
                    tlsKeyPair.public)
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKeyPair.private, listOf(tlsCert, nodeCaCert, intermediateCa.certificate, rootCa.certificate))
        }

        return Pair(keyStore, trustStore)
    }

    @Test
    fun `illegal common name`() {
        val acceptableNames = listOf("CN=Bank A TLS, O=Bank A", "CN=Bank A")
                .map { GeneralSubtree(GeneralName(X500Name(it))) }.toTypedArray()

        val nameConstraints = NameConstraints(acceptableNames, arrayOf())
        val pathValidator = CertPathValidator.getInstance("PKIX")

        assertFailsWith(CertPathValidatorException::class) {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank B"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A TLS, O=Bank A"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
            true
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
            true
        }
    }

    @Test
    fun `x500 name with correct cn and extra attribute`() {
        val acceptableNames = listOf("CN=Bank A TLS, UID=", "O=Bank A")
                .map { GeneralSubtree(GeneralName(X500Name(it))) }.toTypedArray()

        val nameConstraints = NameConstraints(acceptableNames, arrayOf())
        Crypto.ECDSA_SECP256R1_SHA256
        val pathValidator = CertPathValidator.getInstance("PKIX", BouncyCastleProvider.PROVIDER_NAME)

        assertFailsWith(CertPathValidatorException::class) {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
        }

        assertFailsWith(CertPathValidatorException::class) {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A, UID=12345"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A TLS, UID=, E=me@email.com, C=GB"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
            true
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("O=Bank A, UID=, E=me@email.com, C=GB"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
            true
        }
    }
}
