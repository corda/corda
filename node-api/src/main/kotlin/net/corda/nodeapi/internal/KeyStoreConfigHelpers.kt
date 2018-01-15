package net.corda.nodeapi.internal

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.x500Name
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.*
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

// TODO Merge this file and DevIdentityGenerator

/**
 * Create the node and SSL key stores needed by a node. The node key store will be populated with a node CA cert (using
 * the given legal name), and the SSL key store will store the TLS cert which is a sub-cert of the node CA.
 */
fun SSLConfiguration.createDevKeyStores(legalName: CordaX500Name,
                                        rootCert: X509Certificate = DEV_ROOT_CA.certificate,
                                        intermediateCa: CertificateAndKeyPair = DEV_INTERMEDIATE_CA) {
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
    val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, nodeCaCert, nodeCaKeyPair, legalName.x500Principal, tlsKeyPair.public)

    loadOrCreateKeyStore(sslKeystore, keyStorePassword).apply {
        addOrReplaceKey(
                X509Utilities.CORDA_CLIENT_TLS,
                tlsKeyPair.private,
                keyStorePassword.toCharArray(),
                arrayOf(tlsCert, nodeCaCert, intermediateCa.certificate, rootCert))
        save(sslKeystore, keyStorePassword)
    }
}

fun createDevNetworkMapCa(rootCa: CertificateAndKeyPair = DEV_ROOT_CA): CertificateAndKeyPair {
    val keyPair = Crypto.generateKeyPair()
    val cert = X509Utilities.createCertificate(
            CertificateType.NETWORK_MAP,
            rootCa.certificate,
            rootCa.keyPair,
            X500Principal("CN=Network Map,O=R3 Ltd,L=London,C=GB"),
            keyPair.public)
    return CertificateAndKeyPair(cert, keyPair)
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
            legalName.x500Principal,
            keyPair.public,
            nameConstraints = nameConstraints)
    return CertificateAndKeyPair(cert, keyPair)
}

val DEV_INTERMEDIATE_CA: CertificateAndKeyPair get() = DevCaHelper.loadDevCa(X509Utilities.CORDA_INTERMEDIATE_CA)

val DEV_ROOT_CA: CertificateAndKeyPair get() = DevCaHelper.loadDevCa(X509Utilities.CORDA_ROOT_CA)

// We need a class so that we can get hold of the class loader
internal object DevCaHelper {
    fun loadDevCa(alias: String): CertificateAndKeyPair {
        // TODO: Should be identity scheme
        val caKeyStore = loadKeyStore(javaClass.classLoader.getResourceAsStream("certificates/cordadevcakeys.jks"), "cordacadevpass")
        return caKeyStore.getCertificateAndKeyPair(alias, "cordacadevkeypass")
    }
}
