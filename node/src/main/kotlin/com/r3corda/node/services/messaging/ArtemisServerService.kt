package com.r3corda.node.services.messaging

import com.google.common.net.HostAndPort
import com.r3corda.core.crypto.registerWhitelistTrustManager
import com.r3corda.core.messaging.Message
import com.r3corda.core.messaging.MessageHandlerRegistration
import com.r3corda.core.messaging.MessageRecipients
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.internal.Node
import com.r3corda.node.services.api.MessagingServiceInternal
import java.util.concurrent.Executor

/**
 * Created by exfalso on 7/28/16.
 */
class ArtemisServerService : SingletonSerializeAsToken(), MessagingServiceInternal {

    companion object {
        init {
            // Until  https://issues.apache.org/jira/browse/ARTEMIS-656 is resolved gate acceptable
            // certificate hosts manually.
            registerWhitelistTrustManager()
        }


        val log = loggerFor<ArtemisMessagingService>()

        // This is a "property" attached to an Artemis MQ message object, which contains our own notion of "topic".
        // We should probably try to unify our notion of "topic" (really, just a string that identifies an endpoint
        // that will handle messages, like a URL) with the terminology used by underlying MQ libraries, to avoid
        // confusion.
        val TOPIC_PROPERTY = "platform-topic"

        /** Temp helper until network map is established. */
        fun makeRecipient(hostAndPort: HostAndPort): SingleMessageRecipient = ArtemisAddress(hostAndPort)
        fun makeRecipient(hostname: String) = makeRecipient(toHostAndPort(hostname))
        fun toHostAndPort(hostname: String) = HostAndPort.fromString(hostname).withDefaultPort(Node.DEFAULT_PORT)
    }


    override fun stop() {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun registerTrustedAddress(address: SingleMessageRecipient) {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addMessageHandler(topic: String, executor: Executor?, callback: (Message, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeMessageHandler(registration: MessageHandlerRegistration) {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun send(message: Message, target: MessageRecipients) {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createMessage(topic: String, data: ByteArray): Message {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val myAddress: SingleMessageRecipient
        get() = throw UnsupportedOperationException()

}
