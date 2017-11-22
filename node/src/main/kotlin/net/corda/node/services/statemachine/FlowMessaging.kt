package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import com.esotericsoftware.kryo.KryoException
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.AcknowledgeHandle
import net.corda.node.services.messaging.ReceivedMessage
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
    fun start(onMessage: (ReceivedMessage, acknowledgeHandle: AcknowledgeHandle) -> Unit)
}

/**
 * Implementation of [FlowMessaging] using a [ServiceHubInternal] to do the messaging and routing.
 */
class FlowMessagingImpl(val serviceHub: ServiceHubInternal): FlowMessaging {

    companion object {
        val log = contextLogger()

        val sessionTopic = "platform.session"
    }

    override fun start(onMessage: (ReceivedMessage, acknowledgeHandle: AcknowledgeHandle) -> Unit) {
        serviceHub.networkService.addMessageHandler(sessionTopic) { receivedMessage, _, acknowledgeHandle ->
            onMessage(receivedMessage, acknowledgeHandle)
        }
    }

    @Suspendable
    override fun sendSessionMessage(party: Party, message: SessionMessage, deduplicationId: DeduplicationId) {
        log.trace { "Sending message $deduplicationId $message to party $party" }
        val networkMessage = serviceHub.networkService.createMessage(sessionTopic, serializeSessionMessage(message).bytes, deduplicationId)
        val partyInfo = serviceHub.networkMapCache.getPartyInfo(party) ?: throw IllegalArgumentException("Don't know about $party")
        val address = serviceHub.networkService.getAddressOfParty(partyInfo)
        val sequenceKey = when (message) {
            is InitialSessionMessage -> message.initiatorSessionId
            is ExistingSessionMessage -> message.recipientSessionId
        }
        serviceHub.networkService.send(networkMessage, address, sequenceKey = sequenceKey)
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
