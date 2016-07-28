package com.r3corda.node.services.messaging

import com.r3corda.core.messaging.*
import java.util.concurrent.Executor

/**
 * Created by exfalso on 7/28/16.
 */
class ArtemisClientService : MessagingService {
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
