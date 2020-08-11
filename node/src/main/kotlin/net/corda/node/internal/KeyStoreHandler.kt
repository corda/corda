package net.corda.node.internal

import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.KeyManagementService
import net.corda.core.utilities.contextLogger
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import java.io.IOException
import java.math.BigInteger
import java.nio.file.NoSuchFileException
import java.security.GeneralSecurityException
import java.security.PublicKey
import java.security.cert.X509Certificate

class KeyStoreHandler(private val configuration: NodeConfiguration, private val cryptoService: CryptoService) {
    companion object {
        private val log = contextLogger()
    }

    fun initKeyStores(devModeKeyEntropy: BigInteger? = null): List<X509Certificate> {
        if (configuration.devMode) {
            configuration.configureWithDevSSLCertificate(cryptoService, devModeKeyEntropy)
            // configureWithDevSSLCertificate is a devMode process that writes directly to keystore files, so
            // we should re-synchronise BCCryptoService with the updated keystore file.
            if (cryptoService is BCCryptoService) {
                cryptoService.resyncKeystore()
            }
        }
        return validateKeyStores()
    }

    private fun validateKeyStores(): List<X509Certificate> {
        // Step 1. Check trustStore, sslKeyStore and identitiesKeyStore exist.
        val certStores = getCertificateStores()

        // Step 2. Check that trustStore contains the correct key-alias entry.
        val trustRoots = certStores.trustStore.filter { it.first.startsWith(X509Utilities.CORDA_ROOT_CA) }.map { it.second }
        require(trustRoots.isNotEmpty()) {
            "Alias for trustRoot key not found. Please ensure you have an updated trustStore file."
        }

        // Step 3. Check that tls keyStore contains the correct key-alias entry.
        require(X509Utilities.CORDA_CLIENT_TLS in certStores.sslKeyStore) {
            "Alias for TLS key not found. Please ensure you have an updated TLS keyStore file."
        }

        // Step 4. Check tls cert paths chain to the trusted root.
        val sslCertChainRoot = certStores.sslKeyStore.query { getCertificateChain(X509Utilities.CORDA_CLIENT_TLS) }.last()
        require(sslCertChainRoot in trustRoots) { "TLS certificate must chain to the trusted root." }

        return trustRoots
    }

    private data class AllCertificateStores(val trustStore: CertificateStore,
                                            val sslKeyStore: CertificateStore,
                                            val identitiesKeyStore: CertificateStore)

    private fun getCertificateStores(): AllCertificateStores {
        return try {
            // The following will throw NoSuchFileException if key file not found
            // or IOException with GeneralSecurityException cause if keystore password is incorrect.
            val sslKeyStore = configuration.p2pSslOptions.keyStore.get()
            val signingCertificateStore = configuration.signingCertificateStore.get()
            val trustStore = configuration.p2pSslOptions.trustStore.get()
            AllCertificateStores(trustStore, sslKeyStore, signingCertificateStore)
        } catch (e: NoSuchFileException) {
            throw IllegalArgumentException("One or more keyStores (identity or TLS) or trustStore not found. " +
                    "Please either copy your existing keys and certificates from another node, " +
                    "or if you don't have one yet, fill out the config file and run corda.jar initial-registration.", e)
        } catch (e: IOException) {
            if (e.cause is GeneralSecurityException) {
                throw IllegalArgumentException("At least one of the keystores or truststore passwords does not match configuration.", e)
            } else {
                throw e
            }
        }
    }

    data class KeyAndAlias(val key: PublicKey, val alias: String, val rotated: Boolean, val notary: Boolean)

    data class NodeIdentities(val nodeIdentity: PartyAndCertificate,
                              val notaryIdentity: PartyAndCertificate?,
                              val keyPairs: Set<KeyAndAlias>)

    /**
     * Loads the node's legal identity, notary service identity (if set) and associated keys and aliases.
     */
    fun obtainIdentities(trustRoots: List<X509Certificate>): NodeIdentities {
        val (identity, identityKeyPairs) = obtainIdentity(trustRoots)
        val keyPairs = identityKeyPairs.toMutableSet()
        val myNotaryIdentity = configuration.notary?.let {
            if (it.serviceLegalName != null) {
                val (notaryIdentity, notaryIdentityKeyPairs) = loadNotaryServiceIdentity(it.serviceLegalName)
                keyPairs += notaryIdentityKeyPairs
                notaryIdentity
            } else {
                // The only case where the myNotaryIdentity will be the node's legal identity is for existing single notary services running
                // an older version. Current single notary services (V4.6+) sign requests using a separate notary service identity so the
                // notary identity will be different from the node's legal identity.

                // This check is here to ensure that a user does not accidentally/intentionally remove the serviceLegalName configuration
                // parameter after a notary has been registered. If that was possible then notary would start and sign incoming requests
                // with the node's legal identity key, corrupting the data.
                check(!cryptoService.containsKey(X509Utilities.DISTRIBUTED_NOTARY_KEY_ALIAS)) {
                    "The notary service key exists in the key store but no notary service legal name has been configured. " +
                            "Either include the relevant 'notary.serviceLegalName' configuration or validate this key is not necessary " +
                            "and remove from the key store."
                }
                identity
            }
        }
        return NodeIdentities(identity, myNotaryIdentity, keyPairs)
    }

    /**
     * Loads the node's legal identity, public key and alias.
     *
     * If legal identity certificate has been renewed, keystore may contain old entries stored with dummy self-signed certificates.
     * In this case we also return public keys and aliases for old entries, so they can be used by [KeyManagementService] for signing.
     */
    private fun obtainIdentity(trustRoots: List<X509Certificate>): Pair<PartyAndCertificate, Set<KeyAndAlias>> {
        val defaultAlias = X509Utilities.NODE_IDENTITY_KEY_ALIAS
        val legalName = configuration.myLegalName
        val signingCertificateStore = configuration.signingCertificateStore.get()

        // Filter keystore entries by legal name to distinguish them from notary identities.
        val entries = signingCertificateStore.filter { CordaX500Name.build(it.second.subjectX500Principal) == legalName }

        // Rotated identities are represented using self-signed certificates without extendedKeyUsage.
        val rotatedIdentities = entries.filter { it.second.rotatedIdentity }.map { it.first }

        // If configured legal identity alias is present and has a valid certificate, use it (backwards compatibility mode).
        // Otherwise resolve legal identity and node CA certificates automatically.
        val legalIdentityPrivateKeyAlias = if (defaultAlias in signingCertificateStore && defaultAlias !in rotatedIdentities) {
            defaultAlias
        } else {
            // Sort keystore entries (except for rotated ones) by certificate chain length, so that legal identity comes before Node CA.
            val candidates = entries.filter { it.first !in rotatedIdentities }
                    .map { it.first to signingCertificateStore.value.getCertificateChain(it.first) }
                    .sortedByDescending { it.second.size }

            val aliases = candidates.map { it.first }
            check(candidates.isNotEmpty()) {
                "Unable to find node identity certificate for $legalName in keystore"
            }
            check(candidates.size <= 2) {
                "Unable to locate node identity certificate for $legalName in keystore, too many entries found: $aliases"
            }
            // If node CA certificate is present in keystore, check that it's a parent for legal identity certificate.
            if (candidates.size == 2) {
                check(candidates[0].second[1] == candidates[1].second[0]) {
                    "Legal identity and Node CA certificates from keystore do not match: $aliases"
                }
            }
            aliases[0]
        }

        val x509Cert = signingCertificateStore.query { getCertificate(legalIdentityPrivateKeyAlias) }

        // TODO: Use configuration to indicate composite key should be used instead of public key for the identity.
        val certificates: List<X509Certificate> = signingCertificateStore.query { getCertificateChain(legalIdentityPrivateKeyAlias) }
        check(certificates.first() == x509Cert) {
            "Certificates from key store do not line up!"
        }
        check(certificates.last() in trustRoots) { "Node identity certificate must chain to the trusted root." }

        val subject = CordaX500Name.build(certificates.first().subjectX500Principal)
        if (subject != legalName) {
            throw ConfigurationException("The configured legalName '$legalName' doesn't match what's in the key store: $subject")
        }

        val identity = PartyAndCertificate(X509Utilities.buildCertPath(certificates))
        X509Utilities.validateCertPath(trustRoots, identity.certPath)
        val keyPairs = setOf(loadIdentityKey(legalIdentityPrivateKeyAlias, "node identity")) +
                rotatedIdentities.map { loadIdentityKey(it, "previous node identity", rotated = true) }
        return identity to keyPairs
    }

    /**
     * Loads notary service identity. In the case of the experimental RAFT and BFT notary clusters, this loads the pre-generated
     * cluster identity that all worker nodes share. In the case of a simple single notary, this loads the notary service identity
     * that is generated during initial registration and is used to sign notarisation requests.
     * */
    private fun loadNotaryServiceIdentity(serviceLegalName: CordaX500Name): Pair<PartyAndCertificate, Set<KeyAndAlias>> {
        val defaultPrivateKeyAlias = X509Utilities.DISTRIBUTED_NOTARY_KEY_ALIAS
        val defaultCompositeKeyAlias = X509Utilities.DISTRIBUTED_NOTARY_COMPOSITE_KEY_ALIAS
        val signingCertificateStore = configuration.signingCertificateStore.get()

        // Filter keystore entries by legal name to distinguish them from node identities.
        // Identify and separate composite key entries, which are present without private keys.
        val (entries, compositeEntries) = signingCertificateStore
                .filter { CordaX500Name.build(it.second.subjectX500Principal) == serviceLegalName }
                .partition { signingCertificateStore.value.internal.isKeyEntry(it.first) }

        // Rotated identities are represented using self-signed certificates without extendedKeyUsage.
        val rotatedIdentities = entries.filter { it.second.rotatedIdentity }.map { it.first }
        val rotatedCompositeIdentities = compositeEntries.filter { it.second.rotatedIdentity }.map { it.first }

        // If configured alias is present and has a valid certificate, use it (backwards compatibility mode).
        // Otherwise, resolve alias automatically.
        val privateKeyAlias = if (defaultPrivateKeyAlias in signingCertificateStore && defaultPrivateKeyAlias !in rotatedIdentities) {
            defaultPrivateKeyAlias
        } else {
            val candidates = entries.filter { it.first !in rotatedIdentities }.map { it.first }
            check(candidates.isNotEmpty()) {
                "Unable to find notary identity certificate for $serviceLegalName in keystore"
            }
            check(candidates.size == 1) {
                "Unable to locate notary identity certificate for $serviceLegalName in keystore, too many entries found: $candidates"
            }
            candidates.first()
        }

        // If configured alias is present and has a valid certificate, use it (backwards compatibility mode).
        // Otherwise, resolve alias automatically.
        val compositeKeyAlias = if (defaultCompositeKeyAlias in signingCertificateStore && defaultCompositeKeyAlias !in rotatedCompositeIdentities) {
            // If configured alias is present and has a valid certificate, use it (backwards compatibility mode).
            defaultCompositeKeyAlias
        } else {
            val candidates = compositeEntries.filter { it.first !in rotatedCompositeIdentities }.map { it.first }
            check(candidates.size <= 1) {
                "Unable to locate notary identity certificate for $serviceLegalName in keystore, too many entries found: $candidates"
            }
            candidates.firstOrNull()
        }

        val privateKeyAliasCertChain = signingCertificateStore.query { getCertificateChain(privateKeyAlias) }
        // A composite key is only required for BFT notaries.
        val certificates = if (compositeKeyAlias != null) {
            val certificate = signingCertificateStore[compositeKeyAlias]
            // We have to create the certificate chain for the composite key manually, this is because we don't have a keystore
            // provider that understand compositeKey-privateKey combo. The cert chain is created using the composite key certificate +
            // the tail of the private key certificates, as they are both signed by the same certificate chain.
            listOf(certificate) + privateKeyAliasCertChain.drop(1)
        } else {
            // If [compositeKeyAlias] does not exist, we assume the notary is CFT, and each cluster member shares the same notary key pair.
            privateKeyAliasCertChain
        }

        val subject = CordaX500Name.build(certificates.first().subjectX500Principal)
        if (subject != serviceLegalName) {
            throw ConfigurationException("The name of the notary service '$serviceLegalName' doesn't " +
                    "match what's in the key store: $subject. You might need to adjust the configuration of `notary.serviceLegalName`.")
        }
        val identity = PartyAndCertificate(X509Utilities.buildCertPath(certificates))
        val keyPairs = setOf(loadIdentityKey(privateKeyAlias, "notary service identity", notary = true)) +
                rotatedIdentities.map { loadIdentityKey(it, "previous notary service identity", rotated = true, notary = true) }
        return identity to keyPairs
    }

    /**
     * If this certificate is only used to store alias for rotated identity.
     */
    private val X509Certificate.rotatedIdentity: Boolean get() = (extendedKeyUsage == null || extendedKeyUsage.size == 0)

    private fun loadIdentityKey(alias: String, description: String, rotated: Boolean = false, notary: Boolean = false): KeyAndAlias {
        if (!cryptoService.containsKey(alias)) {
            throw IllegalStateException("Key alias $alias for $description was not found in CryptoService")
        }
        val key = cryptoService.getPublicKey(alias)!!
        log.info("Loaded $description key: ${key.toStringShort()}, alias: $alias")
        return KeyAndAlias(key, alias, rotated, notary)
    }
}