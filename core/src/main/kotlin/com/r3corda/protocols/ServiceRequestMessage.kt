package com.r3corda.protocols

import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.MessageRecipients
import com.r3corda.core.node.services.NetworkMapCache

/**
 * Abstract superclass for request messages sent to services, which includes common
 * fields such as replyTo and sessionID.
 */
interface ServiceRequestMessage {
    val sessionID: Long
    fun getReplyTo(networkMapCache: NetworkMapCache): MessageRecipients
}

interface PartyRequestMessage : ServiceRequestMessage {

    val replyToParty: Party

    override fun getReplyTo(networkMapCache: NetworkMapCache): MessageRecipients {
        return networkMapCache.partyNodes.single { it.identity == replyToParty }.address
    }
}