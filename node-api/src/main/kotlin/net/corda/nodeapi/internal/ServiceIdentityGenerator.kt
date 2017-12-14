package net.corda.nodeapi.internal

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.cert
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.crypto.*
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.security.cert.X509Certificate

object ServiceIdentityGenerator {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Generates signing key pairs and a common distributed service identity for a set of nodes.
     * The key pairs and the group identity get serialized to disk in the corresponding node directories.
     * This method should be called *before* any of the nodes are started.
     *
     * @param dirs List of node directories to place the generated identity and key pairs in.
     * @param serviceName The legal name of the distributed service.
     * @param threshold The threshold for the generated group [CompositeKey].
     * @param customRootCert the certificate to use a Corda root CA. If not specified the one in
     *      certificates/cordadevcakeys.jks is used.
     */
    fun generateToDisk(dirs: List<Path>,
                       serviceName: CordaX500Name,
                       serviceId: String,
                       threshold: Int = 1,
                       customRootCert: X509Certificate? = null): Party {
        log.trace { "Generating a group identity \"serviceName\" for nodes: ${dirs.joinToString()}" }
        val keyPairs = (1..dirs.size).map { generateKeyPair() }
        val notaryKey = CompositeKey.Builder().addKeys(keyPairs.map { it.public }).build(threshold)

        val caKeyStore = loadKeyStore(javaClass.classLoader.getResourceAsStream("certificates/cordadevcakeys.jks"), "cordacadevpass")
        val intermediateCa = caKeyStore.getCertificateAndKeyPair(X509Utilities.CORDA_INTERMEDIATE_CA, "cordacadevkeypass")
        // TODO: We should be saving the node CA key & cert
        val nodeCaName = X500Name("O=${X509Utilities.CORDA_CLIENT_CA_CN},L=London,C=GB")
        val nodeCaKeyPair = Crypto.generateKeyPair(signatureScheme = X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val nodeCa = CertificateAndKeyPair(X509Utilities.createCertificate(CertificateType.NODE_CA, intermediateCa.certificate, intermediateCa.keyPair, nodeCaName, nodeCaKeyPair.public), nodeCaKeyPair)
        val rootCert = customRootCert ?: caKeyStore.getCertificate(X509Utilities.CORDA_ROOT_CA)

        keyPairs.zip(dirs) { keyPair, dir ->
            val serviceKeyCert = X509Utilities.createCertificate(CertificateType.SERVICE_IDENTITY, nodeCa.certificate, nodeCa.keyPair, serviceName, keyPair.public)
            val compositeKeyCert = X509Utilities.createCertificate(CertificateType.SERVICE_IDENTITY, nodeCa.certificate, nodeCa.keyPair, serviceName, notaryKey)
            val certPath = (dir / "certificates").createDirectories() / "distributedService.jks"
            val keystore = loadOrCreateKeyStore(certPath, "cordacadevpass")
            keystore.setCertificateEntry("$serviceId-composite-key", compositeKeyCert.cert)
            keystore.setKeyEntry("$serviceId-private-key", keyPair.private, "cordacadevkeypass".toCharArray(), arrayOf(serviceKeyCert.cert, nodeCa.certificate.cert, intermediateCa.certificate.cert, rootCert))
            keystore.save(certPath, "cordacadevpass")
        }
        return Party(serviceName, notaryKey)
    }
}
