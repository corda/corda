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
                                   override val applicationProperties: Map<Any?, Any?>,
                                   private val channel: Channel,
                                   private val delivery: Delivery) : ReceivedMessage {
    data class MessageCompleter(val status: MessageStatus, val delivery: Delivery)

    override fun complete(accepted: Boolean) {
        val status = if (accepted) MessageStatus.Acknowledged else MessageStatus.Rejected
        channel.writeAndFlush(MessageCompleter(status, delivery))
    }

    override fun toString(): String = "Received ${String(payload)} $topic"
}