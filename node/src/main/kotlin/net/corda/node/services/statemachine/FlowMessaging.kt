package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import com.esotericsoftware.kryo.KryoException
import net.corda.core.context.InvocationOrigin
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.messaging.ReceivedMessage
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2PMessagingHeaders
import java.io.NotSerializableException

/**
 * A wrapper interface around flow messaging.
 */
interface FlowMessaging {
    /**
     * Send [message] to [party] using [deduplicationId]. Optionally [acknowledgementHandler] may be specified to
     * listen on the send acknowledgement.
     */
    @Suspendable
    fun sendSessionMessage(party: Party, message: SessionMessage, deduplicationId: DeduplicationId)

    /**
     * Start the messaging using the [onMessage] message handler.
     */
    fun start(onMessage: (ReceivedMessage, deduplicationHandler: DeduplicationHandler) -> Unit)
}

/**
 * Implementation of [FlowMessaging] using a [ServiceHubInternal] to do the messaging and routing.
 */
class FlowMessagingImpl(val serviceHub: ServiceHubInternal): FlowMessaging {

    companion object {
        val log = contextLogger()

        val sessionTopic = "platform.session"
    }

    override fun start(onMessage: (ReceivedMessage, deduplicationHandler: DeduplicationHandler) -> Unit) {
        serviceHub.networkService.addMessageHandler(sessionTopic) { receivedMessage, _, deduplicationHandler ->
            onMessage(receivedMessage, deduplicationHandler)
        }
    }

    @Suspendable
    override fun sendSessionMessage(party: Party, message: SessionMessage, deduplicationId: DeduplicationId) {
        log.trace { "Sending message $deduplicationId $message to party $party" }
        val networkMessage = serviceHub.networkService.createMessage(sessionTopic, serializeSessionMessage(message).bytes, deduplicationId, message.additionalHeaders(party))
        val partyInfo = serviceHub.networkMapCache.getPartyInfo(party) ?: throw IllegalArgumentException("Don't know about $party")
        val address = serviceHub.networkService.getAddressOfParty(partyInfo)
        val sequenceKey = when (message) {
            is InitialSessionMessage -> message.initiatorSessionId
            is ExistingSessionMessage -> message.recipientSessionId
        }
        serviceHub.networkService.send(networkMessage, address, sequenceKey = sequenceKey)
    }

    private fun SessionMessage.additionalHeaders(target: Party): Map<String, String> {

        // This prevents a "deadlock" in case an initiated flow tries to start a session against a draining node that is also the initiator.
        // It does not help in case more than 2 nodes are involved in a circle, so the kill switch via RPC should be used in that case.
        val mightDeadlockDrainingTarget = FlowStateMachineImpl.currentStateMachine()?.context?.origin.let { it is InvocationOrigin.Peer && it.party == target.name }
        return when {
            this !is InitialSessionMessage || mightDeadlockDrainingTarget -> emptyMap()
            else -> mapOf(P2PMessagingHeaders.Type.KEY to P2PMessagingHeaders.Type.SESSION_INIT_VALUE)
        }
    }

    private fun serializeSessionMessage(message: SessionMessage): SerializedBytes<SessionMessage> {
        return try {
            message.serialize()
        } catch (exception: Exception) {
            // Handling Kryo and AMQP serialization problems. Unfortunately the two exception types do not share much of a common exception interface.
            if ((exception is KryoException || exception is NotSerializableException)
                    && message is ExistingSessionMessage && message.payload is ErrorSessionMessage) {
                val error = message.payload.flowException
                val rewrappedError = FlowException(error?.message)
                message.copy(payload = message.payload.copy(flowException = rewrappedError)).serialize()
            } else {
                throw exception
            }
        }
    }
}
