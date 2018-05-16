package net.corda.testing.internal

import com.nhaarman.mockito_kotlin.doAnswer
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.configureDevKeyAndTrustStores
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.serialization.amqp.AMQP_ENABLED
import java.nio.file.Files
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

fun configureTestSSL(legalName: CordaX500Name): SSLConfiguration {
    return object : SSLConfiguration {
        override val certificatesDirectory = Files.createTempDirectory("certs")
        override val keyStorePassword: String get() = "cordacadevpass"
        override val trustStorePassword: String get() = "trustpass"
        override val crlCheckSoftFail: Boolean = true

        init {
            configureDevKeyAndTrustStores(legalName)
        }
    }
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

fun SSLConfiguration.useSslRpcOverrides(): Map<String, Any> {
    return mapOf(
            "rpcSettings.useSsl" to "true",
            "rpcSettings.ssl.certificatesDirectory" to certificatesDirectory.toString(),
            "rpcSettings.ssl.keyStorePassword" to keyStorePassword,
            "rpcSettings.ssl.trustStorePassword" to trustStorePassword,
            "rpcSettings.ssl.crlCheckSoftFail" to true
    )
}

fun SSLConfiguration.noSslRpcOverrides(rpcAdminAddress: NetworkHostAndPort): Map<String, Any> {
    return mapOf(
            "rpcSettings.adminAddress" to rpcAdminAddress.toString(),
            "rpcSettings.useSsl" to "false",
            "rpcSettings.ssl.certificatesDirectory" to certificatesDirectory.toString(),
            "rpcSettings.ssl.keyStorePassword" to keyStorePassword,
            "rpcSettings.ssl.trustStorePassword" to trustStorePassword,
            "rpcSettings.ssl.crlCheckSoftFail" to true
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
