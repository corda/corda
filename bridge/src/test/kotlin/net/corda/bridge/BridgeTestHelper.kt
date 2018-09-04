package net.corda.bridge

import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.createDirectories
import net.corda.core.internal.exists
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.nodeapi.internal.*
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.crypto.*
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

fun createNetworkParams(baseDirectory: Path): Int {
    val dummyNotaryParty = TestIdentity(DUMMY_NOTARY_NAME)
    val notaryInfo = NotaryInfo(dummyNotaryParty.party, false)
    val networkParameters = NetworkParameters(
            minimumPlatformVersion = 1,
            notaries = listOf(notaryInfo),
            modifiedTime = Instant.now(),
            maxMessageSize = 10485760,
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
    val args = arrayOf("--base-directory", workspaceFolder.toString())
    val argsParser = ArgsParser()
    val cmdlineOptions = argsParser.parse(*args)
    val configFile = cmdlineOptions.configFile
    configFile.normalize().parent?.createDirectories()
    ConfigTest::class.java.getResourceAsStream(configResource).use {
        Files.copy(it, configFile)
    }
    val config = cmdlineOptions.loadConfig()
    return config
}

fun FirewallConfiguration.createBridgeKeyStores(legalName: CordaX500Name,
                                                rootCert: X509Certificate = DEV_ROOT_CA.certificate,
                                                intermediateCa: CertificateAndKeyPair = DEV_INTERMEDIATE_CA) = p2pSslOptions.createBridgeKeyStores(legalName, rootCert, intermediateCa)

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
                listOf(tlsCert, nodeCaCert, intermediateCa.certificate, rootCert))
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