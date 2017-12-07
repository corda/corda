package com.r3.corda.networkmanage.hsm.signer

import com.google.common.util.concurrent.MoreExecutors
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.signer.NetworkMapSigner
import com.r3.corda.networkmanage.common.signer.Signer
import com.r3.corda.networkmanage.common.utils.withCert
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.utils.X509Utilities.getAndInitializeKeyStore
import com.r3.corda.networkmanage.hsm.utils.X509Utilities.signData
import com.r3.corda.networkmanage.hsm.utils.X509Utilities.verify
import net.corda.core.internal.cert
import net.corda.core.internal.toX509CertHolder
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.minutes
import net.corda.nodeapi.internal.DigitalSignatureWithCert
import java.security.KeyPair
import java.security.PrivateKey
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Encapsulates logic for periodic network map signing execution.
 * It uses HSM as the signing entity with keys and certificates specified at the construction time.
 */
class HsmNetworkMapSigner(networkMapStorage: NetworkMapStorage,
                          private val caCertificateKeyName: String,
                          private val caPrivateKeyPass: String,
                          private val authenticator: Authenticator,
                          private val signingPeriod: Duration = DEFAULT_SIGNING_PERIOD_MS) : Signer {

    companion object {
        val log = loggerFor<HsmNetworkMapSigner>()
        val DEFAULT_SIGNING_PERIOD_MS = 10.minutes

        private val TERMINATION_TIMEOUT_SEC = 2L
    }

    private val networkMapSigner = NetworkMapSigner(networkMapStorage, this)
    private lateinit var scheduledExecutor: ScheduledExecutorService

    fun start(): HsmNetworkMapSigner {
        val signingPeriodMillis = signingPeriod.toMillis()
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
        scheduledExecutor.scheduleAtFixedRate({
            try {
                networkMapSigner.signNetworkMap()
            } catch (exception: Exception) {
                log.warn("Exception thrown while signing network map", exception)
            }
        }, signingPeriodMillis, signingPeriodMillis, TimeUnit.MILLISECONDS)
        return this
    }

    fun stop() {
        MoreExecutors.shutdownAndAwaitTermination(scheduledExecutor, TERMINATION_TIMEOUT_SEC, TimeUnit.SECONDS)
    }

    /**
     * Signs given data using [CryptoServerJCE.CryptoServerProvider], which connects to the underlying HSM.
     */
    override fun sign(data: ByteArray): DigitalSignatureWithCert {
        return authenticator.connectAndAuthenticate { provider, _ ->
            val keyStore = getAndInitializeKeyStore(provider)
            val caCertificateChain = keyStore.getCertificateChain(caCertificateKeyName)
            val caKey = keyStore.getKey(caCertificateKeyName, caPrivateKeyPass.toCharArray()) as PrivateKey
            val signature = signData(data, KeyPair(caCertificateChain.first().publicKey, caKey), provider)
            verify(data, signature, caCertificateChain.first().publicKey)
            signature.withCert(caCertificateChain.first().toX509CertHolder().cert)
        }
    }
}
