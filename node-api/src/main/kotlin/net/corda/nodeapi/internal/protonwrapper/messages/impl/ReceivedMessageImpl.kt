package net.corda.nodeapi.internal.protonwrapper.messages.impl

import io.netty.channel.Channel
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import org.apache.qpid.proton.engine.Delivery

/**
 *  An internal packet management class that allows tracking of asynchronous acknowledgements
 *  that in turn send Delivery messages back to the originator.
 */
internal class ReceivedMessageImpl(override val payload: ByteArray,
                                   override val topic: String,
                                   override val sourceLegalName: String,
                                   override val sourceLink: NetworkHostAndPort,
                                   override val destinationLegalName: String,
                                   override val destinationLink: NetworkHostAndPort,
                                   override val applicationProperties: Map<String, Any?>,
                                   private val channel: Channel,
                                   private val delivery: Delivery) : ReceivedMessage {
    data class MessageCompleter(val status: MessageStatus, val delivery: Delivery)

    override fun complete(accepted: Boolean) {
        val status = if (accepted) MessageStatus.Acknowledged else MessageStatus.Rejected
        channel.writeAndFlush(MessageCompleter(status, delivery))
    }

    override fun toString(): String = "Received ${String(payload)} $topic"
}