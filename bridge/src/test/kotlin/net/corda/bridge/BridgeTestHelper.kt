package net.corda.bridge

import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.loadDevCaTrustStore
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import org.mockito.Mockito
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.withSettings
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.X509Certificate
import java.time.Instant

fun createNetworkParams(baseDirectory: Path, maxMessageSize: Int = 10485760): Int {
    val dummyNotaryParty = TestIdentity(DUMMY_NOTARY_NAME)
    val notaryInfo = NotaryInfo(dummyNotaryParty.party, false)
    val networkParameters = NetworkParameters(
            minimumPlatformVersion = 1,
            notaries = listOf(notaryInfo),
            modifiedTime = Instant.now(),
            maxMessageSize = maxMessageSize,
            maxTransactionSize = 40000,
            epoch = 1,
            whitelistedContractImplementations = emptyMap()
    )
    val copier = NetworkParametersCopier(networkParameters, overwriteFile = true)
    copier.install(baseDirectory)
    return networkParameters.maxMessageSize
}

fun createAndLoadConfigFromResource(baseDirectory: Path, configResource: String): FirewallConfiguration {
    val workspaceFolder = baseDirectory.normalize().toAbsolutePath()
    workspaceFolder.createDirectories()
    ConfigTest::class.java.getResourceAsStream(configResource).use {
        Files.copy(it, baseDirectory / "firewall.conf")
    }

    val cmdLineOptions = FirewallCmdLineOptions()
    cmdLineOptions.baseDirectory = workspaceFolder
    val config = cmdLineOptions.loadConfig()
    return config
}

fun FirewallConfiguration.createBridgeKeyStores(legalName: CordaX500Name,
                                                rootCert: X509Certificate = DEV_ROOT_CA.certificate,
                                                intermediateCa: CertificateAndKeyPair = DEV_INTERMEDIATE_CA) = publicSSLConfiguration.createBridgeKeyStores(legalName, rootCert, intermediateCa)

fun MutualSslConfiguration.createBridgeKeyStores(legalName: CordaX500Name,
                                                 rootCert: X509Certificate = DEV_ROOT_CA.certificate,
                                                 intermediateCa: CertificateAndKeyPair = DEV_INTERMEDIATE_CA) {

    if (!trustStore.path.exists()) {
        val trustStore = trustStore.get(true)
        loadDevCaTrustStore().copyTo(trustStore)
    }

    val (nodeCaCert, nodeCaKeyPair) = createDevNodeCa(intermediateCa, legalName)

    val sslKeyStore = keyStore.get(createNew = true)
    sslKeyStore.update {
        val tlsKeyPair = generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, nodeCaCert, nodeCaKeyPair, legalName.x500Principal, tlsKeyPair.public)
        setPrivateKey(
                X509Utilities.CORDA_CLIENT_TLS,
                tlsKeyPair.private,
                listOf(tlsCert, nodeCaCert, intermediateCa.certificate, rootCert),
                sslKeyStore.entryPassword)
    }
}

fun serverListening(host: String, port: Int): Boolean {
    var s: Socket? = null
    try {
        s = Socket(host, port)
        return true
    } catch (e: Exception) {
        return false
    } finally {
        try {
            s?.close()
        } catch (e: Exception) {
        }
    }
}

inline fun <reified T> createPartialMock() = Mockito.mock(T::class.java, withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS))