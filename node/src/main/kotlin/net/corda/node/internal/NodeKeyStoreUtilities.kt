package net.corda.node.internal

import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import java.io.IOException
import java.security.KeyStoreException
import java.security.cert.X509Certificate

private data class AllCertificateStores(val trustStore: CertificateStore, val sslKeyStore: CertificateStore, val identitiesKeyStore: CertificateStore)


internal fun NodeConfiguration.initKeyStores(cryptoService: CryptoService): X509Certificate {
    if (devMode) {
        configureWithDevSSLCertificate(cryptoService)
        // configureWithDevSSLCertificate is a devMode process that writes directly to keystore files, so
        // we should re-synchronise BCCryptoService with the updated keystore file.
        if (cryptoService is BCCryptoService) {
            cryptoService.resyncKeystore()
        }
    }
    return validateKeyStores()
}

private fun NodeConfiguration.validateKeyStores(): X509Certificate {
    // Step 1. Check trustStore, sslKeyStore and identitiesKeyStore exist.
    val certStores = try {
        requireNotNull(getCertificateStores()) {
            "One or more keyStores (identity or TLS) or trustStore not found. " +
                    "Please either copy your existing keys and certificates from another node, " +
                    "or if you don't have one yet, fill out the config file and run corda.jar initial-registration."
        }
    } catch (e: KeyStoreException) {
        throw IllegalArgumentException("At least one of the keystores or truststore passwords does not match configuration.")
    }
    // Step 2. Check that trustStore contains the correct key-alias entry.
    require(X509Utilities.CORDA_ROOT_CA in certStores.trustStore) {
        "Alias for trustRoot key not found. Please ensure you have an updated trustStore file."
    }
    // Step 3. Check that tls keyStore contains the correct key-alias entry.
    require(X509Utilities.CORDA_CLIENT_TLS in certStores.sslKeyStore) {
        "Alias for TLS key not found. Please ensure you have an updated TLS keyStore file."
    }

    // Step 4. Check tls cert paths chain to the trusted root.
    val trustRoot = certStores.trustStore[X509Utilities.CORDA_ROOT_CA]
    val sslCertChainRoot = certStores.sslKeyStore.query { getCertificateChain(X509Utilities.CORDA_CLIENT_TLS) }.last()

    require(sslCertChainRoot == trustRoot) { "TLS certificate must chain to the trusted root." }

    return trustRoot
}

private fun NodeConfiguration.getCertificateStores(): AllCertificateStores? {
    return try {
        // The following will throw IOException if key file not found or KeyStoreException if keystore password is incorrect.
        val sslKeyStore = p2pSslOptions.keyStore.get()
        val signingCertificateStore = signingCertificateStore.get()
        val trustStore = p2pSslOptions.trustStore.get()
        AllCertificateStores(trustStore, sslKeyStore, signingCertificateStore)
    } catch (e: IOException) {
        loggerFor<NodeConfiguration>().error("IO exception while trying to validate keystores and truststore", e)
        null
    }
}
