package net.corda.bridge.services.receiver

import net.corda.bridge.services.api.FirewallAuditService
import net.corda.bridge.services.api.RoutingDirection
import net.corda.bridge.services.api.ServiceLifecycleSupport
import net.corda.bridge.services.api.ServiceStateSupport
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.serialize
import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.sequence
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPServer
import org.slf4j.Logger
import rx.Subscription
import rx.subjects.PublishSubject
import rx.subjects.Subject
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * General helper class which facilitates request/response mechanism of data exchange between Float and Bridge.
 */
internal class RequestResponseServiceHelper<in REQ : RequestIdContainer, out RESP : RequestIdContainer>(private val amqpControl: AMQPServer,
                                                                                                        private val floatClientName: CordaX500Name,
                                                                                                        private val sourceLink: NetworkHostAndPort,
                                                                                                        private val sourceLegalName: String,
                                                                                                        private val auditService: FirewallAuditService,
                                                                                                        private val log: Logger,
                                                                                                        private val stateHelper: ServiceStateHelper,
                                                                                                        private val topic: String,
                                                                                                        private val responseClass: KClass<RESP>,
                                                                                                        private val responseTimeOut: Duration) : ServiceLifecycleSupport, ServiceStateSupport by stateHelper {

    private lateinit var responseSubject: Subject<RESP, RESP>
    private lateinit var receiveSubscriber: Subscription

    override fun start() {
        receiveSubscriber = amqpControl.onReceive
                .filter { it.topic == topic }
                .subscribe(::onResponse) { log.error("Received error event", it) }
        responseSubject = PublishSubject.create<RESP>().toSerialized()
        stateHelper.active = true
    }

    override fun stop() {
        stateHelper.active = false
        receiveSubscriber.unsubscribe()
        responseSubject.onCompleted()
    }

    private fun getResponseFuture(requestId: Long) = responseSubject.filter { it.requestId == requestId }.toFuture()

    private fun onResponse(receivedMessage: ReceivedMessage) {
        val response = try {
            if (CordaX500Name.parse(receivedMessage.sourceLegalName) != floatClientName) {
                auditService.packetDropEvent(receivedMessage, "Invalid control source legal name!!", RoutingDirection.INBOUND)
                receivedMessage.complete(true)
                return
            }
            //receivedMessage.payload.deserialize<RESP>() // - unfortunately cannot do that
            SerializationFactory.defaultFactory.deserialize(receivedMessage.payload.sequence(), responseClass.java, SerializationFactory.defaultFactory.defaultContext)
        } catch (ex: Exception) {
            log.error("Unexpected exception when processing response", ex)
            receivedMessage.complete(true)
            return
        }

        log.info("Received response: ${response.requestId}, relaying to consumer.")
        responseSubject.onNext(response)
        receivedMessage.complete(true)
    }

    internal fun enquire(request: REQ): RESP {
        val amqpMessage = amqpControl.createMessage(request.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes,
                topic,
                sourceLegalName,
                sourceLink,
                emptyMap())
        log.info("Sending request with id: ${request.requestId}, Digest: (${request.digest})")
        val resultFuture = getResponseFuture(request.requestId)
        amqpControl.write(amqpMessage)
        // Block until response
        val result = resultFuture.get(responseTimeOut.toMillis(), TimeUnit.MILLISECONDS)
        log.info("Returned response for id: ${result.requestId}, Digest: (${result.digest})")
        return result
    }
}