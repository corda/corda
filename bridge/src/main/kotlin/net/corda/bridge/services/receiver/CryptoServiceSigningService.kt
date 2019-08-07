package net.corda.bridge.services.receiver

import net.corda.bridge.services.api.*
import net.corda.bridge.services.config.BridgeConfigHelper
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.ThreadBox
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.provider.extractCertificates
import java.io.IOException
import java.lang.Math.min
import java.util.function.Consumer

class CryptoServiceSigningService(private val csConfig: CryptoServiceConfig?,
                                  private val legalName: CordaX500Name,
                                  sslConfig: MutualSslConfiguration,
                                  private val sslHandshakeTimeout: Long? = null,
                                  private val auditService: FirewallAuditService,
                                  private val name: String,
                                  private val sleep: (Long) -> Unit = Thread::sleep,
                                  private val makeCryptoService: () -> CryptoService = { BridgeConfigHelper.makeCryptoService(csConfig, legalName, sslConfig.keyStore) },
                                  private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : TLSSigningService, ServiceStateSupport by stateHelper {
    companion object {
        private val log = contextLogger()
        private const val HEARTBEAT_INTERVAL_SECONDS = 60
        private const val RECONNECT_INTERVAL_MIN_SECONDS = 3
        private const val RECONNECT_INTERVAL_MAX_SECONDS = 60
        private const val POLITE_SHUTDOWN_INTERVAL_MILLIS = 3000L
        private const val SSL_HANDSHAKE_TIMEOUT_EXTRA_MILLIS = 5000L
    }

    private val certificates = sslConfig.keyStore.get().extractCertificates()
    private val truststore = sslConfig.trustStore.get()
    private val cryptoServiceName = csConfig?.name ?: SupportedCryptoServices.BC_SIMPLE

    private class InnerState {
        var running = false
        var connectThread: Thread? = null
        var cryptoService: CryptoService? = null
    }

    private val state = ThreadBox(InnerState())

    override fun sign(alias: String, signatureAlgorithm: String, data: ByteArray): ByteArray? {
        val cs = state.locked { cryptoService }
        if (cs != null) {
            try {
                return cs.sign(alias, data, signatureAlgorithm)
            } catch (e: Exception) {
                log.error("Error encountered while signing", e)
            }
        } else {
            log.warn("Crypto service is offline while signing")
        }
        sslHandshakeTimeout?.let {
            // Make TLS handshake timeout when error instead of throwing exception, this will allow TLS to retry connection.
            // Throwing exception here will cause netty to terminate the connection and the client will receive a fatal alert which will block the connection.
            sleep(sslHandshakeTimeout + SSL_HANDSHAKE_TIMEOUT_EXTRA_MILLIS)
        }
        return null
    }

    override fun certificates() = certificates

    override fun truststore(): CertificateStore = truststore

    override fun start() {
        state.locked {
            if (!running) {
                // Don't start if there is an exception other than IOException
                startCryptoService().doOnFailure(Consumer {
                    if (it !is IOException) throw it
                })
                running = true
                connectThread = Thread({ loop() }, "$name-$cryptoServiceName-crypto-signing-thread").apply {
                    isDaemon = true
                }
                connectThread!!.start()
            }
        }
    }

    override fun stop() {
        stateHelper.active = false
        val connectThread = state.locked {
            if (running) {
                log.info("Stopping CryptoServiceSigningService")
                running = false
                cryptoService = null
                val thread = connectThread
                connectThread = null
                thread
            } else null
        }
        connectThread?.interrupt()
        connectThread?.join(POLITE_SHUTDOWN_INTERVAL_MILLIS)
    }

    private fun startCryptoService(): Try<CryptoService> = Try.on {
        makeCryptoService().checkCanSignAllAliases()
    }.doOnSuccess(Consumer {
        log.info("Starting CryptoServiceSigningService with ${it.javaClass.simpleName}")
        state.locked { cryptoService = it }
    }).doOnFailure(Consumer {
        log.warn("$cryptoServiceName is not operational", it)
    })

    private fun loop() {
        var reconnectInterval = RECONNECT_INTERVAL_MIN_SECONDS
        while (state.locked { running }) {
            val cs = state.locked { cryptoService }
            if (cs != null) {
                try {
                    // we don't care the return boolean result, just want to make sure HSM is connected.
                    cs.containsKey("heartbeat")
                    log.trace { "Crypto service online." }
                    stateHelper.active = true
                    reconnectInterval = HEARTBEAT_INTERVAL_SECONDS
                } catch (e: Exception) {
                    log.warn("Crypto service offline.", e)
                    state.locked { cryptoService = null }
                    stateHelper.active = false
                    reconnectInterval = RECONNECT_INTERVAL_MIN_SECONDS
                }
            }
            try {
                // Sleep before attempting reconnect or before next heartbeat
                sleep(reconnectInterval * 1000L)
            } catch (ex: InterruptedException) {
                // ignore
            }
            if (state.locked { running && cryptoService == null }) {
                startCryptoService()
                reconnectInterval = min(2 * reconnectInterval, RECONNECT_INTERVAL_MAX_SECONDS)
            }
        }
        log.info("Ended CryptoServiceSigningService Thread")
    }

    private fun CryptoService.checkCanSignAllAliases() : CryptoService {
        val testPhraseClearText = "testingSigning123".toByteArray()
        aliases().forEach { alias ->
            val cert = requireNotNull(certificate(alias))
            val msgSuffixFn = { "for '$name' for alias: '$alias' and certificate: $cert . " +
                    "Please check configuration file paying special attention at CryptoServiceConfigs." }
            val signedTestPhrase = requireNotNull(sign(alias, testPhraseClearText, defaultTLSSignatureScheme().signatureName)) { "Failed to sign " + msgSuffixFn() }
            require(Crypto.isValid(cert.publicKey, signedTestPhrase, testPhraseClearText)) {
                "Failed to validate signature " + msgSuffixFn()
            }
        }
        return this
    }
}