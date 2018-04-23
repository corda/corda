/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.node.internal

import net.corda.core.utilities.ByteSequence
import net.corda.node.services.messaging.Message
import net.corda.node.services.statemachine.DeduplicationId
import java.time.Instant

/**
 * An implementation of [Message] for in memory messaging by the test [InMemoryMessagingNetwork].
 */
data class InMemoryMessage(override val topic: String,
                           override val data: ByteSequence,
                           override val uniqueMessageId: DeduplicationId,
                           override val debugTimestamp: Instant = Instant.now(),
                           override val senderUUID: String? = null) : Message {

    override val additionalHeaders: Map<String, String> = emptyMap()

    override fun toString() = "$topic#${String(data.bytes)}"
}