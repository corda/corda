package net.corda.nodeapi.internal.protonwrapper.messages.impl

import io.netty.channel.Channel
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import org.apache.qpid.proton.engine.Delivery

/**
 *  An internal packet management class that allows tracking of asynchronous acknowledgements
 *  that in turn send Delivery messages back to the originator.
 */
internal class ReceivedMessageImpl(override var payload: ByteArray,
                                   override val topic: String,
                                   override val sourceLegalName: String,
                                   override val sourceLink: NetworkHostAndPort,
                                   override val destinationLegalName: String,
                                   override val destinationLink: NetworkHostAndPort,
                                   override val applicationProperties: Map<String, Any?>,
                                   private val channel: Channel,
                                   private val delivery: Delivery) : ReceivedMessage {
    companion object {
        private val emptyPayload = ByteArray(0)
        private val logger = contextLogger()
    }

    data class MessageCompleter(val status: MessageStatus, val delivery: Delivery)

    override fun release() {
        payload = emptyPayload
    }

    override fun complete(accepted: Boolean) {
        release()
        val status = if (accepted) MessageStatus.Acknowledged else MessageStatus.Rejected
        if (channel.isActive) {
            channel.writeAndFlush(MessageCompleter(status, delivery))
        } else {
            logger.info("Not writing $status as $channel is not active")
        }
    }

    override fun toString(): String = "Received ${String(payload)} $topic"
}