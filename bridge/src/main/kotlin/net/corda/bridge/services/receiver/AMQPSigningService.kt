package net.corda.bridge.services.receiver

import net.corda.bridge.services.api.FirewallAuditService
import net.corda.bridge.services.api.ServiceLifecycleSupport
import net.corda.bridge.services.api.TLSSigningService
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPServer
import java.security.cert.X509Certificate

internal class AMQPSigningService(amqpControl: AMQPServer,
                                  floatClientName: CordaX500Name,
                                  sourceLink: NetworkHostAndPort,
                                  sourceLegalName: String,
                                  private val certificates: Map<String, List<X509Certificate>>,
                                  private val truststore: CertificateStore,
                                  auditService: FirewallAuditService,
                                  stateHelper: ServiceStateHelper = ServiceStateHelper(log),
                                  private val reqRespHelper: RequestResponseServiceHelper<SigningRequest, SigningResponse> =
                                          RequestResponseServiceHelper(amqpControl, floatClientName, sourceLink, sourceLegalName, auditService, log, stateHelper, FloatControlTopics.FLOAT_SIGNING_TOPIC, SigningResponse::class))
    : TLSSigningService, ServiceLifecycleSupport by reqRespHelper {

    companion object {
        private val log = contextLogger()
    }

    override fun sign(alias: String, signatureAlgorithm: String, data: ByteArray): ByteArray? {
        val request = SigningRequest(alias = alias, sigAlgo = signatureAlgorithm, data = data)
        val response = reqRespHelper.enquire(request)
        return response.signature
    }

    override fun certificates() = certificates

    override fun truststore(): CertificateStore = truststore
}