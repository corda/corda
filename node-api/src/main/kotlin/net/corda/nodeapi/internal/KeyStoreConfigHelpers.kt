package net.corda.nodeapi.internal

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.x500Name
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.*
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.cert.X509CertificateHolder

/**
 * Create the node and SSL key stores needed by a node. The node key store will be populated with a node CA cert (using
 * the given legal name), and the SSL key store will store the TLS cert which is a sub-cert of the node CA.
 */
fun SSLConfiguration.createDevKeyStores(rootCert: X509CertificateHolder, intermediateCa: CertificateAndKeyPair, legalName: CordaX500Name) {
    val (nodeCaCert, nodeCaKeyPair) = createDevNodeCa(intermediateCa, legalName)

    loadOrCreateKeyStore(nodeKeystore, keyStorePassword).apply {
        addOrReplaceKey(
                X509Utilities.CORDA_CLIENT_CA,
                nodeCaKeyPair.private,
                keyStorePassword.toCharArray(),
                arrayOf(nodeCaCert, intermediateCa.certificate, rootCert))
        save(nodeKeystore, keyStorePassword)
    }

    val tlsKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, nodeCaCert, nodeCaKeyPair, legalName, tlsKeyPair.public)

    loadOrCreateKeyStore(sslKeystore, keyStorePassword).apply {
        addOrReplaceKey(
                X509Utilities.CORDA_CLIENT_TLS,
                tlsKeyPair.private,
                keyStorePassword.toCharArray(),
                arrayOf(tlsCert, nodeCaCert, intermediateCa.certificate, rootCert))
        save(sslKeystore, keyStorePassword)
    }
}

/**
 * Create a dev node CA cert, as a sub-cert of the given [intermediateCa], and matching key pair using the given
 * [CordaX500Name] as the cert subject.
 */
fun createDevNodeCa(intermediateCa: CertificateAndKeyPair, legalName: CordaX500Name): CertificateAndKeyPair {
    val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, legalName.x500Name))), arrayOf())
    val cert = X509Utilities.createCertificate(
            CertificateType.NODE_CA,
            intermediateCa.certificate,
            intermediateCa.keyPair,
            legalName,
            keyPair.public,
            nameConstraints = nameConstraints)
    return CertificateAndKeyPair(cert, keyPair)
}
