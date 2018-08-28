package net.corda.nodeapi.internal

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.toX500Name
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.*
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import java.security.KeyPair
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

// TODO Merge this file and DevIdentityGenerator

/**
 * Create the node and SSL key stores needed by a node. The node key store will be populated with a node CA cert (using
 * the given legal name), and the SSL key store will store the TLS cert which is a sub-cert of the node CA.
 */
// TODO sollecitom refactor
fun Pair<CertificateStoreSupplier, SSLConfiguration>.createDevKeyStores(legalName: CordaX500Name,
                                        rootCert: X509Certificate = DEV_ROOT_CA.certificate,
                                        intermediateCa: CertificateAndKeyPair = DEV_INTERMEDIATE_CA): Pair<CertificateStore, X509KeyStore> {
    val (nodeCaCert, nodeCaKeyPair) = createDevNodeCa(intermediateCa, legalName)

    val nodeKeyStore = first.get(createNew = true)
    nodeKeyStore.update {
        setPrivateKey(
                X509Utilities.CORDA_CLIENT_CA,
                nodeCaKeyPair.private,
                listOf(nodeCaCert, intermediateCa.certificate, rootCert))
    }

    val sslKeyStore = second.loadSslKeyStore(createNew = true)
    sslKeyStore.update {
        val tlsKeyPair = generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, nodeCaCert, nodeCaKeyPair, legalName.x500Principal, tlsKeyPair.public)
        setPrivateKey(
                X509Utilities.CORDA_CLIENT_TLS,
                tlsKeyPair.private,
                listOf(tlsCert, nodeCaCert, intermediateCa.certificate, rootCert))
    }

    return Pair(nodeKeyStore, sslKeyStore)
}

// TODO sollecitom re-use in the function above if not removed
fun SSLConfiguration.createDevKeyStores(legalName: CordaX500Name,
                                        rootCert: X509Certificate = DEV_ROOT_CA.certificate,
                                        intermediateCa: CertificateAndKeyPair = DEV_INTERMEDIATE_CA): X509KeyStore {
    val (nodeCaCert, nodeCaKeyPair) = createDevNodeCa(intermediateCa, legalName)

    val sslKeyStore = loadSslKeyStore(createNew = true)
    sslKeyStore.update {
        val tlsKeyPair = generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, nodeCaCert, nodeCaKeyPair, legalName.x500Principal, tlsKeyPair.public)
        setPrivateKey(
                X509Utilities.CORDA_CLIENT_TLS,
                tlsKeyPair.private,
                listOf(tlsCert, nodeCaCert, intermediateCa.certificate, rootCert))
    }

    return sslKeyStore
}

fun CertificateStore.storeLegalIdentity(alias: String, keyPair: KeyPair = Crypto.generateKeyPair()): PartyAndCertificate {
    // TODO sollecitom see if you can delegate functions instead
    return with(value) {
        val nodeCaCertPath = getCertificateChain(X509Utilities.CORDA_CLIENT_CA)
        // Assume key password = store password.
        val nodeCaCertAndKeyPair = getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA)
        // Create new keys and store in keystore.
        val identityCert = X509Utilities.createCertificate(CertificateType.LEGAL_IDENTITY, nodeCaCertAndKeyPair.certificate, nodeCaCertAndKeyPair.keyPair, nodeCaCertAndKeyPair.certificate.subjectX500Principal, keyPair.public)
        // TODO: X509Utilities.validateCertificateChain()
        // Assume key password = store password.
        val identityCertPath = listOf(identityCert) + nodeCaCertPath
        setPrivateKey(alias, keyPair.private, identityCertPath)
        save()
        PartyAndCertificate(X509Utilities.buildCertPath(identityCertPath))
    }
}

fun createDevNetworkMapCa(rootCa: CertificateAndKeyPair = DEV_ROOT_CA): CertificateAndKeyPair {
    val keyPair = generateKeyPair()
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
fun createDevNodeCa(intermediateCa: CertificateAndKeyPair,
                    legalName: CordaX500Name,
                    nodeKeyPair: KeyPair = generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)): CertificateAndKeyPair {
    val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, legalName.toX500Name()))), arrayOf())
    val cert = X509Utilities.createCertificate(
            CertificateType.NODE_CA,
            intermediateCa.certificate,
            intermediateCa.keyPair,
            legalName.x500Principal,
            nodeKeyPair.public,
            nameConstraints = nameConstraints)
    return CertificateAndKeyPair(cert, nodeKeyPair)
}

val DEV_INTERMEDIATE_CA: CertificateAndKeyPair get() = DevCaHelper.loadDevCa(X509Utilities.CORDA_INTERMEDIATE_CA)
val DEV_ROOT_CA: CertificateAndKeyPair get() = DevCaHelper.loadDevCa(X509Utilities.CORDA_ROOT_CA)
const val DEV_CA_PRIVATE_KEY_PASS: String = "cordacadevkeypass"
const val DEV_CA_KEY_STORE_FILE: String = "cordadevcakeys.jks"
const val DEV_CA_KEY_STORE_PASS: String = "cordacadevpass"
const val DEV_CA_TRUST_STORE_FILE: String = "cordatruststore.jks"
const val DEV_CA_TRUST_STORE_PASS: String = "trustpass"

// We need a class so that we can get hold of the class loader
internal object DevCaHelper {
    fun loadDevCa(alias: String): CertificateAndKeyPair {
        // TODO: Should be identity scheme
        val caKeyStore = loadKeyStore(javaClass.classLoader.getResourceAsStream("certificates/$DEV_CA_KEY_STORE_FILE"), DEV_CA_KEY_STORE_PASS)
        return caKeyStore.getCertificateAndKeyPair(alias, DEV_CA_PRIVATE_KEY_PASS)
    }
}
