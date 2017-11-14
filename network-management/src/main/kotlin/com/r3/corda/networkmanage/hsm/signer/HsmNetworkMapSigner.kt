package com.r3.corda.networkmanage.hsm.signer

import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.NodeInfoStorage
import com.r3.corda.networkmanage.common.signer.NetworkMapSigner
import com.r3.corda.networkmanage.common.signer.SignatureAndCertPath
import com.r3.corda.networkmanage.common.signer.Signer
import com.r3.corda.networkmanage.common.utils.buildCertPath
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.utils.X509Utilities
import com.r3.corda.networkmanage.hsm.utils.X509Utilities.signData
import com.r3.corda.networkmanage.hsm.utils.X509Utilities.verify
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.minutes
import java.security.KeyPair
import java.security.PrivateKey
import java.time.Duration
import java.util.*
import kotlin.concurrent.fixedRateTimer

/**
 * Encapsulates logic for periodic network map signing execution.
 * It uses HSM as the signing entity with keys and certificates specified at the construction time.
 */
class HsmNetworkMapSigner(networkMapStorage: NetworkMapStorage,
                          private val nodeInfoStorage: NodeInfoStorage,
                          private val caCertificateKeyName: String,
                          private val caPrivateKeyPass: String,
                          private val keyStorePassword: String?,
                          private val authenticator: Authenticator,
                          private val signingPeriod: Duration = DEFAULT_SIGNING_PERIOD_MS) : Signer {

    companion object {
        val log = loggerFor<HsmNetworkMapSigner>()
        val DEFAULT_SIGNING_PERIOD_MS = 10.minutes
    }

    private val networkMapSigner = NetworkMapSigner(networkMapStorage, this)
    private var fixedRateTimer: Timer? = null

    fun start(): HsmNetworkMapSigner {
        stop()
        fixedRateTimer = fixedRateTimer(
                name = "Network Map Signing Thread",
                period = signingPeriod.toMillis(),
                action = {
                    try {
                        signNodeInfo()
                        networkMapSigner.signNetworkMap()
                    } catch (exception: Exception) {
                        log.warn("Exception thrown while signing network map", exception)
                    }
                })
        return this
    }

    fun stop() {
        fixedRateTimer?.cancel()
    }

    private fun signNodeInfo() {
        // Retrieve data
        val nodeInfoBytes = nodeInfoStorage.getUnsignedNodeInfoBytes()
        // Authenticate and sign
        authenticator.connectAndAuthenticate { provider, _ ->
            val keyStore = X509Utilities.getAndInitializeKeyStore(provider, keyStorePassword)
            val caCertificateChain = keyStore.getCertificateChain(caCertificateKeyName)
            val caKey = keyStore.getKey(caCertificateKeyName, caPrivateKeyPass.toCharArray()) as PrivateKey
            for ((nodeInfoHash, bytes) in nodeInfoBytes) {
                val signature = signData(bytes, KeyPair(caCertificateChain.first().publicKey, caKey), provider)
                verify(bytes, signature, caCertificateChain.first().publicKey)
                nodeInfoStorage.signNodeInfo(nodeInfoHash, signature)
            }
        }
    }

    /**
     * Signs given data using [CryptoServerJCE.CryptoServerProvider], which connects to the underlying HSM.
     */
    override fun sign(data: ByteArray): SignatureAndCertPath? {
        var result: SignatureAndCertPath? = null
        authenticator.connectAndAuthenticate { provider, _ ->
            val keyStore = X509Utilities.getAndInitializeKeyStore(provider, keyStorePassword)
            val caCertificateChain = keyStore.getCertificateChain(caCertificateKeyName)
            val caKey = keyStore.getKey(caCertificateKeyName, caPrivateKeyPass.toCharArray()) as PrivateKey
            val signature = signData(data, KeyPair(caCertificateChain.first().publicKey, caKey), provider)
            verify(data, signature, caCertificateChain.first().publicKey)
            result = SignatureAndCertPath(signature, buildCertPath(*caCertificateChain))
        }
        return result
    }
}