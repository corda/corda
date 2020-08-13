package net.corda.node.internal

import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.uncheckedCast
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
    private lateinit var trustRoots: List<X509Certificate>

    private lateinit var certificateStore: CertificateStore

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
        trustRoots = certStores.trustStore.filter { it.first.startsWith(X509Utilities.CORDA_ROOT_CA) }.map { it.second }
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

    data class KeyAndAlias(val key: PublicKey, val alias: String)

    data class Identities(val nodeIdentity: PartyAndCertificate,
                          val notaryIdentity: PartyAndCertificate?,
                          val signingKeys: Set<KeyAndAlias>,
                          val oldNotaryKeys: Set<PublicKey>)

    private data class IdentityWithKeys(val identity: PartyAndCertificate,
                                        val signingKeys: List<KeyAndAlias>,
                                        val oldKeys: List<KeyAndAlias>)

    /**
     * Loads the node's legal identity, notary service identity (if set) and associated keys and aliases.
     */
    fun obtainIdentities(): Identities {
        certificateStore = configuration.signingCertificateStore.get()

        val nodeIdentityKeyAlias = X509Utilities.NODE_IDENTITY_KEY_ALIAS
        val nodeIdentity = loadIdentity(nodeIdentityKeyAlias, configuration.myLegalName)

        val notaryIdentity = configuration.notary?.let {
            loadNotaryIdentity(it.serviceLegalName, nodeIdentity.identity)
        }

        val signingKeys = nodeIdentity.signingKeys + (notaryIdentity?.signingKeys?: emptyList())
        val oldNotaryKeys = notaryIdentity?.oldKeys?.map { it.key } ?: emptyList()

        return Identities(nodeIdentity.identity, notaryIdentity?.identity, signingKeys.toSet(), oldNotaryKeys.toSet())
    }

    /**
     * Loads notary service identity. In the case of the experimental RAFT and BFT notary clusters, this loads the pre-generated
     * cluster identity that all worker nodes share. In the case of a simple single notary, this loads the notary service identity
     * that is generated during initial registration and is used to sign notarisation requests.
     **/
    private fun loadNotaryIdentity(serviceLegalName: CordaX500Name?, nodeIdentity: PartyAndCertificate): IdentityWithKeys {
        val notaryKeyAlias = X509Utilities.DISTRIBUTED_NOTARY_KEY_ALIAS
        val notaryCompositeKeyAlias = X509Utilities.DISTRIBUTED_NOTARY_COMPOSITE_KEY_ALIAS

        if (serviceLegalName == null) {
            // The only case where the myNotaryIdentity will be the node's legal identity is for existing single notary services running
            // an older version. Current single notary services (V4.6+) sign requests using a separate notary service identity so the
            // notary identity will be different from the node's legal identity.

            // This check is here to ensure that a user does not accidentally/intentionally remove the serviceLegalName configuration
            // parameter after a notary has been registered. If that was possible then notary would start and sign incoming requests
            // with the node's legal identity key, corrupting the data.
            check(!certificateStore.contains(notaryKeyAlias)) {
                "The notary service key exists in the key store but no notary service legal name has been configured. " +
                        "Either include the relevant 'notary.serviceLegalName' configuration or validate this key is not necessary " +
                        "and remove from the key store."
            }
            return IdentityWithKeys(nodeIdentity, signingKeys = emptyList(), oldKeys = emptyList())
        }

        // First load notary service identity that is generated during initial registration.
        val serviceIdentity = loadIdentity(notaryKeyAlias, serviceLegalName)
        // Then lookup for a composite identity.
        val compositeIdentity = loadCompositeIdentity(notaryCompositeKeyAlias, serviceLegalName, serviceIdentity)
        // If alias for composite key does not exist, we assume the notary is CFT, and each cluster member shares the same notary key pair.
        return compositeIdentity ?: serviceIdentity
    }

    /**
     * Loads the node's legal identity (or notary's service identity) certificate, public key and alias.
     *
     * If identity certificate has been renewed, the result will also contain previous public keys and aliases,
     * so they can still be used by [KeyManagementService] for signing.
     */
    private fun loadIdentity(alias: String, legalName: CordaX500Name): IdentityWithKeys {
        val aliases = certificateStore.filterAliases(alias)
        val activeAlias = checkNotNull(aliases.lastOrNull()) {
            "Alias '$alias' for node identity key is not in the keyStore file."
        }
        val keys = aliases.map { cryptoService.loadKey(it) }

        val certificate = certificateStore.query { getCertificate(activeAlias) }
        val certificates = certificateStore.query { getCertificateChain(activeAlias) }
        check(certificates.first() == certificate) {
            "Certificates from key store do not line up!"
        }
        check(certificates.last() in trustRoots) {
            "Certificate for node identity must chain to the trusted root."
        }

        val subject = CordaX500Name.build(certificates.first().subjectX500Principal)
        check(subject == legalName) {
            "The configured legalName '$legalName' doesn't match what's in the key store: $subject"
        }

        val identity = PartyAndCertificate(X509Utilities.buildCertPath(certificates))
        X509Utilities.validateCertPath(trustRoots, identity.certPath)
        return IdentityWithKeys(identity, signingKeys = keys, oldKeys = keys.dropLast(1))
    }

    /**
     * Loads composite identity certificate, public key and alias.
     * Composite identity is stored as a certificate-only entry without associated signing key.
     * Certificate chain and signing key are copied from the [baseIdentity].
     **/
    private fun loadCompositeIdentity(alias: String, legalName: CordaX500Name, baseIdentity: IdentityWithKeys): IdentityWithKeys? {
        val aliases = certificateStore.filterAliases(alias)
        val activeAlias = aliases.lastOrNull() ?: return null
        val keys = aliases.map { certificateStore.loadCompositeKey(it) }

        val certificate = certificateStore.query { getCertificate(activeAlias) }
        val subject = CordaX500Name.build(certificate.subjectX500Principal)
        check(subject == legalName) {
            "The configured legalName '$legalName' doesn't match what's in the key store: $subject"
        }

        // We have to create the certificate chain for the composite key manually, this is because we don't have a keystore
        // provider that understand compositeKey-privateKey combo. The cert chain is created using the composite key certificate +
        // the tail of the private key certificates, as they are both signed by the same certificate chain.
        val certificates: List<X509Certificate> = uncheckedCast(listOf(certificate) + baseIdentity.identity.certPath.certificates.drop(1))

        val identity = PartyAndCertificate(X509Utilities.buildCertPath(certificates))
        X509Utilities.validateCertPath(trustRoots, identity.certPath)
        return IdentityWithKeys(identity, signingKeys = baseIdentity.signingKeys, oldKeys = keys.dropLast(1))
    }

    /**
     * Find keystore aliases for the node identity linked to the [originalAlias].
     * If identity key has never been rotated, the result will contain just the [originalAlias].
     *
     * For rotated identity, the result also includes newly generated alias(es) in the format `"$originalAlias-$seqNo"`.
     * The result is sorted in chronological order (i.e. by seqNo), for example: `identity-private-key`, `identity-private-key-2`, ... ,
     * `identity-private-key-9`, `identity-private-key-10`. Alias of the actual identity is always on the last position.
     */
    private fun CertificateStore.filterAliases(originalAlias: String) = aliases()
            .filter { it.startsWith(originalAlias) }
            .sortedWith(compareBy({ it.length }, { it }))

    private fun CryptoService.loadKey(alias: String): KeyAndAlias {
        check(containsKey(alias)) {
            "Key for node identity alias '$alias' not found in CryptoService."
        }
        val key = getPublicKey(alias)!!
        log.info("Loaded node identity key ${key.toStringShort()}, alias: $alias")
        return KeyAndAlias(key, alias)
    }

    private fun CertificateStore.loadCompositeKey(alias: String): KeyAndAlias {
        val key = query { getPublicKey(alias) }
        log.info("Loaded composite identity key ${key.toStringShort()}, alias: $alias")
        return KeyAndAlias(key, alias)
    }
}