package net.corda.nodeapi.internal

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.SslConfiguration
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.DISTRIBUTED_NOTARY_ALIAS_PREFIX
import net.corda.nodeapi.internal.crypto.X509Utilities.NODE_IDENTITY_ALIAS_PREFIX
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.security.KeyPair
import java.security.PublicKey
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

/**
 * Contains utility methods for generating identities for a node.
 *
 * WARNING: This is not application for production use.
 */
object DevIdentityGenerator {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Install a node key store for the given node directory using the given legal name. */
    fun installKeyStoreWithNodeIdentity(nodeDir: Path, legalName: CordaX500Name): Party {
        val certificatesDirectory = nodeDir / "certificates"
        val signingCertStore = FileBasedCertificateStoreSupplier(certificatesDirectory / "nodekeystore.jks", DEV_CA_KEY_STORE_PASS, DEV_CA_KEY_STORE_PASS)
        val p2pKeyStore = FileBasedCertificateStoreSupplier(certificatesDirectory / "sslkeystore.jks", DEV_CA_KEY_STORE_PASS, DEV_CA_KEY_STORE_PASS)
        val p2pTrustStore = FileBasedCertificateStoreSupplier(certificatesDirectory / "truststore.jks", DEV_CA_TRUST_STORE_PASS, DEV_CA_TRUST_STORE_PRIVATE_KEY_PASS)
        val p2pSslConfig = SslConfiguration.mutual(p2pKeyStore, p2pTrustStore)

        certificatesDirectory.createDirectories()
        val nodeKeyStore = signingCertStore.get(true).also { it.installDevNodeCaCertPath(legalName) }
        p2pSslConfig.keyStore.get(true).also { it.registerDevP2pCertificates(legalName) }

        val identity = nodeKeyStore.storeLegalIdentity("$NODE_IDENTITY_ALIAS_PREFIX-private-key")
        return identity.party
    }

    /** Generates a CFT notary identity, where the entire cluster shares a key pair. */
    fun generateDistributedNotarySingularIdentity(dirs: List<Path>, notaryName: CordaX500Name): Party {
        require(dirs.isNotEmpty()){"At least one directory to generate identity for must be specified"}

        log.trace { "Generating singular identity \"$notaryName\" for nodes: ${dirs.joinToString()}" }

        val keyPair = generateKeyPair()
        val notaryKey = keyPair.public

        dirs.forEach { nodeDir ->
            val keyStore = getKeyStore(nodeDir)
            setPrivateKey(keyStore, keyPair, notaryName.x500Principal)
        }
        return Party(notaryName, notaryKey)
    }

    /** Generates a BFT notary identity: individual key pairs for each cluster member, and a shared composite key. */
    fun generateDistributedNotaryCompositeIdentity(dirs: List<Path>, notaryName: CordaX500Name, threshold: Int = 1): Party {
        require(dirs.isNotEmpty()){"At least one directory to generate identity for must be specified"}

        log.trace { "Generating composite identity \"$notaryName\" for nodes: ${dirs.joinToString()}" }

        val keyPairs = (1..dirs.size).map { generateKeyPair() }
        val notaryKey = CompositeKey.Builder().addKeys(keyPairs.map { it.public }).build(threshold)

        keyPairs.zip(dirs) { keyPair, nodeDir ->
            val keyStore = getKeyStore(nodeDir)
            setPrivateKey(keyStore, keyPair, notaryName.x500Principal)
            setCompositeKey(keyStore, notaryKey, notaryName.x500Principal)
        }
        return Party(notaryName, notaryKey)
    }

    private fun getKeyStore(nodeDir: Path): X509KeyStore {
        val distServKeyStoreFile = nodeDir / "certificates/distributedService.jks"
        return X509KeyStore.fromFile(distServKeyStoreFile, DEV_CA_KEY_STORE_PASS, createNew = true)
    }

    private fun setPrivateKey(keyStore: X509KeyStore, keyPair: KeyPair, notaryPrincipal: X500Principal) {
        val serviceKeyCert = createCertificate(keyPair.public, notaryPrincipal)
        keyStore.setPrivateKey(
                "$DISTRIBUTED_NOTARY_ALIAS_PREFIX-private-key",
                keyPair.private,
                listOf(serviceKeyCert, DEV_INTERMEDIATE_CA.certificate, DEV_ROOT_CA.certificate),
                DEV_CA_KEY_STORE_PASS // Unfortunately we have to use the same password for private key due to Artemis limitation, for more details please see:
                // org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport.loadKeyManagerFactory
                // where it is calling `KeyManagerFactory.init()` with store password
                /*DEV_CA_PRIVATE_KEY_PASS*/)
    }

    private fun setCompositeKey(keyStore: X509KeyStore, compositeKey: PublicKey, notaryPrincipal: X500Principal) {
        val compositeKeyCert = createCertificate(compositeKey, notaryPrincipal)
        keyStore.setCertificate("$DISTRIBUTED_NOTARY_ALIAS_PREFIX-composite-key", compositeKeyCert)
    }

    private fun createCertificate(publicKey: PublicKey, principal: X500Principal): X509Certificate {
        return X509Utilities.createCertificate(
                CertificateType.SERVICE_IDENTITY,
                DEV_INTERMEDIATE_CA.certificate,
                DEV_INTERMEDIATE_CA.keyPair,
                principal,
                publicKey)
    }
}
