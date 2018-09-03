package net.corda.nodeapi.internal.protonwrapper.messages.impl

import io.netty.buffer.ByteBuf
import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.messages.SendableMessage

/**
 * An internal packet management class that allows handling of the encoded buffers and
 * allows registration of an acknowledgement handler when the remote receiver confirms durable storage.
 */
internal class SendableMessageImpl(override val payload: ByteArray,
                                   override val topic: String,
                                   override val destinationLegalName: String,
                                   override val destinationLink: NetworkHostAndPort,
                                   override val applicationProperties: Map<String, Any?>) : SendableMessage {
    var buf: ByteBuf? = null
    @Volatile
    var status: MessageStatus = MessageStatus.Unsent

    private val _onComplete = openFuture<MessageStatus>()
    override val onComplete: CordaFuture<MessageStatus> get() = _onComplete

    fun release() {
        buf?.release()
        buf = null
    }

    fun doComplete(status: MessageStatus) {
        this.status = status
        _onComplete.set(status)
    }

    override fun toString(): String = "Sendable ${String(payload)} $topic $status"
}