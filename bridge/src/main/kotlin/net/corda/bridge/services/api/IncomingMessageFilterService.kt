package net.corda.bridge.services.api

import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage

/**
 * This service is responsible for security checking the incoming packets to ensure they are for a legitimate node inbox and
 * potentially for any other security related aspects. If the message is badly formed then it will be dropped and an audit event logged.
 * Otherwise the message is forwarded to the appropriate node inbox on the local Artemis Broker.
 * The service will not be active until the underlying [BridgeArtemisConnectionService] is active.
 */
interface IncomingMessageFilterService : ServiceLifecycleSupport {
    fun sendMessageToLocalBroker(inboundMessage: ReceivedMessage)
}