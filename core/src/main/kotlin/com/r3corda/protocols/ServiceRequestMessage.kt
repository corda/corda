package com.r3corda.protocols

import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.MessageRecipients
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.services.NetworkMapCache

/**
 * Abstract superclass for request messages sent to services, which includes common
 * fields such as replyTo and sessionID.
 */
interface ServiceRequestMessage {
    val sessionID: Long
    fun getReplyTo(networkMapCache: NetworkMapCache): MessageRecipients
}

/**
 * A message which specifies reply destination as a specific endpoint such as a monitoring client. This is of particular
 * use where we want to address a specific endpoint, not necessarily a specific user (for example if the same user logs
 * in on two machines, we want to consistently deliver messages as part of a session, to the same machine the session
 * started on).
 */
interface DirectRequestMessage: ServiceRequestMessage {
    val replyToRecipient: SingleMessageRecipient
    override fun getReplyTo(networkMapCache: NetworkMapCache): MessageRecipients = replyToRecipient
}

interface PartyRequestMessage : ServiceRequestMessage {

    val replyToParty: Party

    override fun getReplyTo(networkMapCache: NetworkMapCache): MessageRecipients {
        return networkMapCache.partyNodes.single { it.identity == replyToParty }.address
    }
}

/**
 * A Handshake message is sent to initiate communication between two protocol instances. It contains the two session IDs
 * the two protocols will need to communicate.
 * Note: This is a temperary interface and will be removed once the protocol session work is implemented.
 */
interface HandshakeMessage : PartyRequestMessage {

    val sendSessionID: Long
    val receiveSessionID: Long
    @Deprecated("sessionID functions as receiveSessionID but it's recommended to use the later for clarity",
            replaceWith = ReplaceWith("receiveSessionID"))
    override val sessionID: Long get() = receiveSessionID

}