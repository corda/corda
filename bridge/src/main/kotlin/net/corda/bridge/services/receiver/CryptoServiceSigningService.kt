package net.corda.bridge.services.receiver

import net.corda.bridge.services.api.FirewallAuditService
import net.corda.bridge.services.api.ServiceStateSupport
import net.corda.bridge.services.api.TLSSigningService
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.toFuture
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import rx.Subscription
import rx.subjects.PublishSubject
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

class CryptoServiceSigningService(private val cryptoService: CryptoService,
                                  private val certificates: Map<String, List<X509Certificate>>,
                                  private val truststore: CertificateStore,
                                  private val sslHandshakeTimeout: Long? = null,
                                  private val auditService: FirewallAuditService,
                                  private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : TLSSigningService, ServiceStateSupport by stateHelper {
    companion object {
        private val log = contextLogger()
    }

    private var hsmHeartbeat: Subscription? = null

    override fun sign(alias: String, signatureAlgorithm: String, data: ByteArray): ByteArray? {
        return try {
            cryptoService.sign(alias, data, signatureAlgorithm)
        } catch (e: Exception) {
            log.error("Error encountered while signing", e)
            sslHandshakeTimeout?.let {
                // Make TLS handshake timeout when error instead of throwing exception, this will allow TLS to retry connection.
                // Throwing exception here will cause netty to terminate the connection and the client will receive a fatal alert which will block the connection.
                Thread.sleep(sslHandshakeTimeout + 5000)
            }
            null
        }
    }

    override fun certificates() = certificates

    override fun truststore(): CertificateStore = truststore

    override fun start() {
        log.info("Starting CryptoServiceSigningService with ${cryptoService.javaClass.simpleName}")

        // TODO: Heartbeat interval configurable?
        val heartbeat = PublishSubject.interval(0, 1, TimeUnit.MINUTES).onBackpressureDrop().map {
            try {
                // we don't care the return boolean result, just want to make sure HSM is connected.
                cryptoService.containsKey("heartbeat")
                log.trace { "Crypto service online." }
                true
            } catch (e: Exception) {
                log.trace("Crypto service offline.", e)
                false
            }
        }

        // Block until the first heartbeat returns
        stateHelper.active = heartbeat.toFuture().get()

        // subscribe to future heartbeats
        hsmHeartbeat = heartbeat.subscribe {
            stateHelper.active = it
        }
    }

    override fun stop() {
        log.info("Stopping CryptoServiceSigningService")
        hsmHeartbeat?.unsubscribe()
        stateHelper.active = false
    }
}