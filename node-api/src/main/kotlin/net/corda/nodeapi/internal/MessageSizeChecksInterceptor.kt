package net.corda.nodeapi.internal

import net.corda.core.utilities.contextLogger
import org.apache.activemq.artemis.api.core.BaseInterceptor
import org.apache.activemq.artemis.api.core.Interceptor
import org.apache.activemq.artemis.core.protocol.core.Packet
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.MessagePacket
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage
import org.apache.activemq.artemis.protocol.amqp.broker.AmqpInterceptor
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection

class ArtemisMessageSizeChecksInterceptor(maxMessageSize: Int) : MessageSizeChecksInterceptor<Packet>(maxMessageSize), Interceptor {
    override fun getMessageSize(packet: Packet?): Int? {
        return when (packet) {
        // This is an estimate of how much memory a Message body takes up.
        // Note, it is only an estimate
            is MessagePacket -> (packet.message.persistentSize - packet.message.headersAndPropertiesEncodeSize - 4).toInt()
        // Skip all artemis control messages.
            else -> null
        }
    }
}

class AmqpMessageSizeChecksInterceptor(maxMessageSize: Int) : MessageSizeChecksInterceptor<AMQPMessage>(maxMessageSize), AmqpInterceptor {
    override fun getMessageSize(packet: AMQPMessage?): Int? = packet?.encodeSize
}

/**
 *  Artemis message interceptor to enforce maxMessageSize on incoming messages.
 */
sealed class MessageSizeChecksInterceptor<T : Any>(private val maxMessageSize: Int) : BaseInterceptor<T> {
    companion object {
        private val logger = contextLogger()
    }

    override fun intercept(packet: T, connection: RemotingConnection?): Boolean {
        val messageSize = getMessageSize(packet) ?: return true
        return if (messageSize > maxMessageSize) {
            logger.warn("Message size exceeds maxMessageSize network parameter, maxMessageSize: [$maxMessageSize], message size: [$messageSize], " +
                    "dropping message, client id :${connection?.clientID}")
            false
        } else {
            true
        }
    }

    // get size of the message in byte, returns null if the message is null or size don't need to be checked.
    abstract fun getMessageSize(packet: T?): Int?
}

