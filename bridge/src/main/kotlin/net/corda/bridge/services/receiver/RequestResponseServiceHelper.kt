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
import kotlin.reflect.KClass

internal class RequestResponseServiceHelper<in REQ : TunnelControlMessageWithId, out RESP : TunnelControlMessageWithId>(private val amqpControl: AMQPServer,
                                                                                                                                 private val floatClientName: CordaX500Name,
                                                                                                                                 private val sourceLink: NetworkHostAndPort,
                                                                                                                                 private val sourceLegalName: String,
                                                                                                                                 private val auditService: FirewallAuditService,
                                                                                                                                 private val log: Logger,
                                                                                                                                 private val stateHelper: ServiceStateHelper,
                                                                                                                                 private val topic: String,
                                                                                                                                 private val responseClass: KClass<RESP>) : ServiceLifecycleSupport, ServiceStateSupport by stateHelper {

    private lateinit var response: Subject<RESP, RESP>
    private lateinit var receiveSubscriber: Subscription

    override fun start() {
        receiveSubscriber = amqpControl.onReceive
                .filter { it.topic == topic }
                .subscribe(::onResponse) { log.error("Received error event", it) }
        response = PublishSubject.create<RESP>().toSerialized()
        stateHelper.active = true
    }

    override fun stop() {
        stateHelper.active = false
        receiveSubscriber.unsubscribe()
        response.onCompleted()
    }

    private fun waitForResponse(requestId: Long) = response.filter { it.requestId == requestId }.toFuture().get()

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
            receivedMessage.complete(true)
            return
        }

        log.info("Received response: ${response.requestId}, adding to list.")
        this.response.onNext(response)
        receivedMessage.complete(true)
    }

    internal fun enquire(request: REQ): RESP {
        val amqpMessage = amqpControl.createMessage(request.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes,
                topic,
                sourceLegalName,
                sourceLink,
                emptyMap())
        log.info("Sending request: ${request.requestId}")
        amqpControl.write(amqpMessage)
        // Block until response
        val result = waitForResponse(request.requestId)

        log.info("returned response: $result")
        return result
    }
}