/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.statemachine

import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import java.security.SecureRandom

/**
 * A session between two flows is identified by two session IDs, the initiating and the initiated session ID.
 * However after the session has been established the communication is symmetric. From then on we differentiate between
 * the two session IDs with "source" ID (the ID from which we receive) and "sink" ID (the ID to which we send).
 *
 *             Flow A (initiating)                   Flow B (initiated)
 *           initiatingId=sourceId=0
 *        send(Initiate(initiatingId=0)) ----->    initiatingId=sinkId=0
 *                                                 initiatedId=sourceId=1
 *            initiatedId=sinkId=1       <----- send(Confirm(initiatedId=1))
 */
@CordaSerializable
sealed class SessionMessage

@CordaSerializable
data class SessionId(val toLong: Long) {
    companion object {
        fun createRandom(secureRandom: SecureRandom) = SessionId(secureRandom.nextLong())
    }
}

/**
 * The initial message to initiate a session with.
 *
 * @param initiatorSessionId the session ID of the initiator. On the sending side this is the *source* ID, on the
 *   receiving side this is the *sink* ID.
 * @param initiationEntropy additional randomness to seed the initiated flow's deduplication ID.
 * @param initiatorFlowClassName the class name to be used to determine the initiating-initiated mapping on the receiver
 *   side.
 * @param flowVersion the version of the initiating flow.
 * @param appName the name of the cordapp defining the initiating flow, or "corda" if it's a core flow.
 * @param firstPayload the optional first payload.
 */
data class InitialSessionMessage(
        val initiatorSessionId: SessionId,
        val initiationEntropy: Long,
        val initiatorFlowClassName: String,
        val flowVersion: Int,
        val appName: String,
        val firstPayload: SerializedBytes<Any>?
) : SessionMessage() {
    override fun toString() = "InitialSessionMessage(" +
            "initiatorSessionId=$initiatorSessionId, " +
            "initiationEntropy=$initiationEntropy, " +
            "initiatorFlowClassName=$initiatorFlowClassName, " +
            "appName=$appName, " +
            "firstPayload=${firstPayload?.javaClass}" +
            ")"
}

/**
 * A message sent when a session has been established already.
 *
 * @param recipientSessionId the recipient session ID. On the sending side this is the *sink* ID, on the receiving side
 *   this is the *source* ID.
 * @param payload the rest of the message.
 */
data class ExistingSessionMessage(
        val recipientSessionId: SessionId,
        val payload: ExistingSessionMessagePayload
) : SessionMessage()

/**
 * The payload of an [ExistingSessionMessage]
 */
@CordaSerializable
sealed class ExistingSessionMessagePayload

/**
 * The confirmation message sent by the initiated side.
 * @param initiatedSessionId the initiated session ID, the other half of [InitialSessionMessage.initiatorSessionId].
 *   This is the *source* ID on the sending(initiated) side, and the *sink* ID on the receiving(initiating) side.
 */
data class ConfirmSessionMessage(
        val initiatedSessionId: SessionId,
        val initiatedFlowInfo: FlowInfo
) : ExistingSessionMessagePayload()

/**
 * A message containing flow-related data.
 *
 * @param payload the serialised payload.
 */
data class DataSessionMessage(val payload: SerializedBytes<Any>) : ExistingSessionMessagePayload() {
    override fun toString() = "DataSessionMessage(payload=${payload.javaClass})"
}

/**
 * A message indicating that an error has happened.
 *
 * @param flowException the exception that happened. This is null if the error condition wasn't revealed to the
 *   receiving side.
 * @param errorId the ID of the source error. This is always specified to allow posteriori correlation of error conditions.
 */
data class ErrorSessionMessage(val flowException: FlowException?, val errorId: Long) : ExistingSessionMessagePayload()

/**
 * A message indicating that a session initiation has failed.
 *
 * @param message a message describing the problem to the initator.
 * @param errorId an error ID identifying this error condition.
 */
data class RejectSessionMessage(val message: String, val errorId: Long) : ExistingSessionMessagePayload()

/**
 * A message indicating that the flow hosting the session has ended. Note that this message is strictly part of the
 * session protocol, the flow may be removed before all counter-flows have ended.
 *
 * The sole purpose of this message currently is to provide diagnostic in cases where the two communicating flows'
 * protocols don't match up, e.g. one is waiting for the other, but the other side has already finished.
 */
object EndSessionMessage : ExistingSessionMessagePayload()
