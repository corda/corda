package net.corda.bridge.services.receiver

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.bridge.services.api.FirewallAuditService
import net.corda.nodeapi.internal.lifecycle.ServiceLifecycleSupport
import net.corda.nodeapi.internal.lifecycle.ServiceStateHelper
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPServer
import net.corda.nodeapi.internal.protonwrapper.netty.ExternalCrlSource
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Implementation of [ExternalCrlSource] which uses [reqRespHelper] to perform remote communication via Bridge/Float tunnel connection
 * required to retrieve CRLs for a given certificate.
 * Also caches results to minimise amount of IO operations performed.
 */
internal class TunnelExternalCrlSourceService(amqpControl: AMQPServer,
                                              floatClientName: CordaX500Name,
                                              sourceLink: NetworkHostAndPort,
                                              sourceLegalName: String,
                                              auditService: FirewallAuditService,
                                              responseTimeout: Duration,
                                              stateHelper: ServiceStateHelper = ServiceStateHelper(log),
                                              private val reqRespHelper: RequestResponseServiceHelper<CrlRequest, CrlResponse> =
                                                      RequestResponseServiceHelper(amqpControl, floatClientName, sourceLink, sourceLegalName, auditService, log, stateHelper, FloatControlTopics.FLOAT_CRL_TOPIC, CrlResponse::class, responseTimeout))
    : ExternalCrlSource, ServiceLifecycleSupport by reqRespHelper {

    companion object {
        private val log = contextLogger()
    }

    private val cache: LoadingCache<X509Certificate, CrlResponse> = Caffeine.newBuilder()
            .maximumSize(java.lang.Long.getLong("net.corda.bridge.services.receiver.crl.cacheSize", 100))
            .expireAfterWrite(java.lang.Long.getLong("net.corda.bridge.services.receiver.crl.expireMinutes", 60), TimeUnit.MINUTES)
            .build { reqRespHelper.enquire(CrlRequest(certificate = it)) }

    override fun fetch(certificate: X509Certificate): Set<X509CRL> {
        val response = cache.get(certificate)
        return if (response == null) {
            log.warn("Null response received from cache for: ${certificate.subjectX500Principal}")
            emptySet()
        } else response.crls
    }
}