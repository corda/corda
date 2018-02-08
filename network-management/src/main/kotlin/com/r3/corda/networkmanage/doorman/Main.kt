package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.persistence.CertificationRequestStorage
import com.r3.corda.networkmanage.common.persistence.CertificationRequestStorage.Companion.DOORMAN_SIGNATURE
import com.r3.corda.networkmanage.common.persistence.configureDatabase
import com.r3.corda.networkmanage.common.utils.*
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.network.NetworkParameters
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.time.Instant
import kotlin.concurrent.thread

data class NetworkMapStartParams(val signer: LocalSigner?, val updateNetworkParameters: NetworkParameters?, val config: NetworkMapConfig)

data class NetworkManagementServerStatus(var serverStartTime: Instant = Instant.now(), var lastRequestCheckTime: Instant? = null)

private fun processKeyStore(parameters: NetworkManagementServerParameters): Pair<CertPathAndKey, LocalSigner>? {
    if (parameters.keystorePath == null) return null

    // Get password from console if not in config.
    val keyStorePassword = parameters.keystorePassword ?: readPassword("Key store password: ")
    val privateKeyPassword = parameters.caPrivateKeyPassword ?: readPassword("Private key password: ")
    val keyStore = X509KeyStore.fromFile(parameters.keystorePath, keyStorePassword)
    val csrCertPathAndKey = keyStore.getCertPathAndKey(X509Utilities.CORDA_INTERMEDIATE_CA, privateKeyPassword)
    val networkMapSigner = LocalSigner(keyStore.getCertificateAndKeyPair(CORDA_NETWORK_MAP, privateKeyPassword))
    return Pair(csrCertPathAndKey, networkMapSigner)
}

/**
 * This storage automatically approves all created requests.
 */
class ApproveAllCertificateRequestStorage(private val delegate: CertificationRequestStorage) : CertificationRequestStorage by delegate {
    override fun saveRequest(request: PKCS10CertificationRequest): String {
        val requestId = delegate.saveRequest(request)
        delegate.markRequestTicketCreated(requestId)
        approveRequest(requestId, DOORMAN_SIGNATURE)
        return requestId
    }
}

fun main(args: Array<String>) {
    try {
        parseParameters(*args).run {
            println("Starting in $mode mode")
            when (mode) {
                Mode.ROOT_KEYGEN -> generateRootKeyPair(
                        rootStorePath ?: throw IllegalArgumentException("The 'rootStorePath' parameter must be specified when generating keys!"),
                        rootKeystorePassword,
                        rootPrivateKeyPassword,
                        trustStorePassword)
                Mode.CA_KEYGEN -> generateSigningKeyPairs(
                        keystorePath ?: throw IllegalArgumentException("The 'keystorePath' parameter must be specified when generating keys!"),
                        rootStorePath ?: throw IllegalArgumentException("The 'rootStorePath' parameter must be specified when generating keys!"),
                        rootKeystorePassword,
                        rootPrivateKeyPassword,
                        keystorePassword,
                        caPrivateKeyPassword)
                Mode.DOORMAN -> {
                    initialiseSerialization()
                    val persistence = configureDatabase(dataSourceProperties, database)
                    // TODO: move signing to signing server.
                    val csrAndNetworkMap = processKeyStore(this)

                    if (csrAndNetworkMap != null) {
                        println("Starting network management services with local signing")
                    }

                    val networkManagementServer = NetworkManagementServer()
                    val networkParameters = updateNetworkParameters?.let {
                        // TODO This check shouldn't be needed. Fix up the config design.
                        requireNotNull(networkMap) { "'networkMapConfig' config is required for applying network parameters" }
                        println("Parsing network parameters from '${it.toAbsolutePath()}'...")
                        parseNetworkParametersFrom(it)
                    }
                    val networkMapStartParams = networkMap?.let {
                        NetworkMapStartParams(csrAndNetworkMap?.second, networkParameters, it)
                    }

                    networkManagementServer.start(NetworkHostAndPort(host, port), persistence, csrAndNetworkMap?.first, doorman, networkMapStartParams)

                    Runtime.getRuntime().addShutdownHook(thread(start = false) {
                        networkManagementServer.close()
                    })
                }
            }
        }
    } catch (e: ShowHelpException) {
        e.errorMessage?.let(::println)
        e.parser.printHelpOn(System.out)
    }
}
