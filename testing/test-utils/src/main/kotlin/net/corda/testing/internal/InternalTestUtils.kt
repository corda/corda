package net.corda.testing.internal

import net.corda.core.contracts.*
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.nodeapi.internal.DEV_CA_TRUST_STORE_FILE
import net.corda.nodeapi.internal.DEV_CA_TRUST_STORE_PASS
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.TwoWaySslConfiguration
import net.corda.nodeapi.internal.registerDevP2pCertificates
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.nodeapi.internal.crypto.*
import net.corda.nodeapi.internal.loadDevCaTrustStore
import net.corda.serialization.internal.amqp.AMQP_ENABLED
import net.corda.testing.internal.stubs.CertificateStoreStubs
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import javax.security.auth.x500.X500Principal

@Suppress("unused")
inline fun <reified T : Any> T.kryoSpecific(reason: String, function: () -> Unit) = if (!AMQP_ENABLED) {
    function()
} else {
    loggerFor<T>().info("Ignoring Kryo specific test, reason: $reason")
}

@Suppress("unused")
inline fun <reified T : Any> T.amqpSpecific(reason: String, function: () -> Unit) = if (AMQP_ENABLED) {
    function()
} else {
    loggerFor<T>().info("Ignoring AMQP specific test, reason: $reason")
}

fun configureTestSSL(legalName: CordaX500Name): TwoWaySslConfiguration {

    val certificatesDirectory = Files.createTempDirectory("certs")
    val config = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
    if (config.trustStore.getOptional() == null) {
        loadDevCaTrustStore().copyTo(config.trustStore.get(true))
    }
    if (config.keyStore.getOptional() == null) {
        config.keyStore.get(true).registerDevP2pCertificates(legalName)
    }
    return config
}

private val defaultRootCaName = X500Principal("CN=Corda Root CA,O=R3 Ltd,L=London,C=GB")
private val defaultIntermediateCaName = X500Principal("CN=Corda Intermediate CA,O=R3 Ltd,L=London,C=GB")

/**
 * Returns a pair of [CertificateAndKeyPair]s, the first being the root CA and the second the intermediate CA.
 * @param rootCaName The subject name for the root CA cert.
 * @param intermediateCaName The subject name for the intermediate CA cert.
 */
fun createDevIntermediateCaCertPath(
        rootCaName: X500Principal = defaultRootCaName,
        intermediateCaName: X500Principal = defaultIntermediateCaName
): Pair<CertificateAndKeyPair, CertificateAndKeyPair> {
    val rootKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val rootCert = X509Utilities.createSelfSignedCACertificate(rootCaName, rootKeyPair)

    val intermediateCaKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val intermediateCaCert = X509Utilities.createCertificate(
            CertificateType.INTERMEDIATE_CA,
            rootCert,
            rootKeyPair,
            intermediateCaName,
            intermediateCaKeyPair.public)

    return Pair(
            CertificateAndKeyPair(rootCert, rootKeyPair),
            CertificateAndKeyPair(intermediateCaCert, intermediateCaKeyPair)
    )
}

/**
 * Returns a triple of [CertificateAndKeyPair]s, the first being the root CA, the second the intermediate CA and the third
 * the node CA.
 * @param legalName The subject name for the node CA cert.
 */
fun createDevNodeCaCertPath(
        legalName: CordaX500Name,
        nodeKeyPair: KeyPair = generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME),
        rootCaName: X500Principal = defaultRootCaName,
        intermediateCaName: X500Principal = defaultIntermediateCaName
): Triple<CertificateAndKeyPair, CertificateAndKeyPair, CertificateAndKeyPair> {
    val (rootCa, intermediateCa) = createDevIntermediateCaCertPath(rootCaName, intermediateCaName)
    val nodeCa = createDevNodeCa(intermediateCa, legalName, nodeKeyPair)
    return Triple(rootCa, intermediateCa, nodeCa)
}

fun BrokerRpcSslOptions.useSslRpcOverrides(): Map<String, String> {
    return mapOf(
            "rpcSettings.useSsl" to "true",
            "rpcSettings.ssl.keyStorePath" to keyStorePath.toAbsolutePath().toString(),
            "rpcSettings.ssl.keyStorePassword" to keyStorePassword
    )
}

/**
 * Until we have proper handling of multiple identities per node, for tests we use the first identity as special one.
 * TODO: Should be removed after multiple identities are introduced.
 */
fun NodeInfo.chooseIdentityAndCert(): PartyAndCertificate = legalIdentitiesAndCerts.first()

/**
 * Returns the party identity of the first identity on the node. Until we have proper handling of multiple identities per node,
 * for tests we use the first identity as special one.
 * TODO: Should be removed after multiple identities are introduced.
 */
fun NodeInfo.chooseIdentity(): Party = chooseIdentityAndCert().party

fun p2pSslOptions(path: Path, name: CordaX500Name = CordaX500Name("MegaCorp", "London", "GB")): TwoWaySslConfiguration {
    val sslConfig = CertificateStoreStubs.P2P.withCertificatesDirectory(path, keyStorePassword = "serverstorepass")
    val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()
    sslConfig.keyStore.get(true).registerDevP2pCertificates(name, rootCa.certificate, intermediateCa)
    val trustStore = sslConfig.trustStore.get(true)
    trustStore[X509Utilities.CORDA_ROOT_CA] = rootCa.certificate
    return sslConfig
}

/** This is the same as the deprecated [WireTransaction] c'tor but avoids the deprecation warning. */
fun createWireTransaction(inputs: List<StateRef>,
                          attachments: List<SecureHash>,
                          outputs: List<TransactionState<*>>,
                          commands: List<Command<*>>,
                          notary: Party?,
                          timeWindow: TimeWindow?,
                          privacySalt: PrivacySalt = PrivacySalt()): WireTransaction {
    val componentGroups = WireTransaction.createComponentGroups(inputs, outputs, commands, attachments, notary, timeWindow)
    return WireTransaction(componentGroups, privacySalt)
}
