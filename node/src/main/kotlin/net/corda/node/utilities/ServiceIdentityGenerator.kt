package net.corda.node.utilities

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.cert
import net.corda.core.internal.div
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import java.nio.file.Files
import java.nio.file.Path

object ServiceIdentityGenerator {
    private val log = loggerFor<ServiceIdentityGenerator>()

    /**
     * Generates signing key pairs and a common distributed service identity for a set of nodes.
     * The key pairs and the group identity get serialized to disk in the corresponding node directories.
     * This method should be called *before* any of the nodes are started.
     *
     * @param dirs List of node directories to place the generated identity and key pairs in.
     * @param serviceId The service id of the distributed service.
     * @param serviceName The legal name of the distributed service.
     * @param threshold The threshold for the generated group [CompositeKey].
     */
    // TODO: This needs to write out to the key store, not just files on disk
    fun generateToDisk(dirs: List<Path>,
                       serviceId: String,
                       serviceName: CordaX500Name,
                       threshold: Int = 1): Party {
        log.trace { "Generating a group identity \"serviceName\" for nodes: ${dirs.joinToString()}" }
        val keyPairs = (1..dirs.size).map { generateKeyPair() }
        val notaryKey = CompositeKey.Builder().addKeys(keyPairs.map { it.public }).build(threshold)

        val caKeyStore = loadKeyStore(javaClass.classLoader.getResourceAsStream("net/corda/node/internal/certificates/cordadevcakeys.jks"), "cordacadevpass")
        val issuer = caKeyStore.getCertificateAndKeyPair(X509Utilities.CORDA_INTERMEDIATE_CA, "cordacadevkeypass")
        val rootCert = caKeyStore.getCertificate(X509Utilities.CORDA_ROOT_CA)

        keyPairs.zip(dirs) { keyPair, dir ->
            val serviceKeyCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, issuer.certificate, issuer.keyPair, serviceName, keyPair.public)
            val compositeKeyCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, issuer.certificate, issuer.keyPair, serviceName, notaryKey)
            val certPath = Files.createDirectories(dir / "certificates") / "distributedService.jks"

            val keystore = loadOrCreateKeyStore(certPath, "cordacadevpass")
            keystore.setCertificateEntry("$serviceId-composite-key", compositeKeyCert.cert)
            keystore.setKeyEntry("$serviceId-private-key", keyPair.private, "cordacadevkeypass".toCharArray(), arrayOf(serviceKeyCert.cert, issuer.certificate.cert, rootCert))
            keystore.save(certPath, "cordacadevpass")
        }
        return Party(serviceName, notaryKey)
    }
}
