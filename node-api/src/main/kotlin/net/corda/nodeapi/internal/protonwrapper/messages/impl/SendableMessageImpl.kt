/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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