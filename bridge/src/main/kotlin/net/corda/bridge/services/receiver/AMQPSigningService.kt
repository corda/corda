package net.corda.bridge.services.receiver

import net.corda.bridge.services.api.FirewallAuditService
import net.corda.bridge.services.api.RoutingDirection
import net.corda.bridge.services.api.ServiceStateSupport
import net.corda.bridge.services.api.TLSSigningService
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPServer
import rx.Subscription
import rx.subjects.PublishSubject
import rx.subjects.Subject
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger

class AMQPSigningService(private val amqpControl: AMQPServer,
                         private val floatClientName: CordaX500Name,
                         private val sourceLink: NetworkHostAndPort,
                         private val sourceLegalName: String,
                         private val certificates: Map<String, List<X509Certificate>>,
                         private val truststore: CertificateStore,
                         private val auditService: FirewallAuditService,
                         private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : TLSSigningService, ServiceStateSupport by stateHelper {
    companion object {
        private val log = contextLogger()
    }

    private lateinit var signingResponse: Subject<SigningResponse, SigningResponse>
    private lateinit var receiveSubscriber: Subscription

    override fun sign(alias: String, signatureAlgorithm: String, data: ByteArray): ByteArray? {
        val request = SigningRequest(alias = alias, sigAlgo = signatureAlgorithm, data = data)
        val amqpSigningMessage = amqpControl.createMessage(request.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes,
                FloatControlTopics.FLOAT_SIGNING_TOPIC,
                sourceLegalName,
                sourceLink,
                emptyMap())
        log.info("Sending signing request: ${request.requestId}")
        amqpControl.write(amqpSigningMessage)
        // Block until response
        val result = waitForResponse(request.requestId)

        log.info("returned signature: $result")
        return result.signature
    }

    override fun certificates() = certificates

    override fun truststore(): CertificateStore = truststore

    override fun start() {
        receiveSubscriber = amqpControl.onReceive
                .filter { it.topic == FloatControlTopics.FLOAT_SIGNING_TOPIC }
                .subscribe(::onSigningResponse) { log.error("Receive event error", it) }
        signingResponse = PublishSubject.create<SigningResponse>().toSerialized()
        stateHelper.active = true
    }

    override fun stop() {
        receiveSubscriber.unsubscribe()
        signingResponse.onCompleted()
        stateHelper.active = false
    }

    private fun waitForResponse(requestId: Long) = signingResponse.filter { it.requestId == requestId }.toFuture().get()

    private fun onSigningResponse(receivedMessage: ReceivedMessage) {
        val response = try {
            if (CordaX500Name.parse(receivedMessage.sourceLegalName) != floatClientName) {
                auditService.packetDropEvent(receivedMessage, "Invalid control source legal name!!", RoutingDirection.INBOUND)
                receivedMessage.complete(true)
                return
            }
            receivedMessage.payload.deserialize<SigningResponse>()
        } catch (ex: Exception) {
            receivedMessage.complete(true)
            return
        }

        log.info("Received signing response: ${response.requestId}, adding to list.")
        signingResponse.onNext(response)
        receivedMessage.complete(true)
    }
}