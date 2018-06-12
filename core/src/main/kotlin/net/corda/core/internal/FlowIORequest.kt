/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowSession
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NonEmptySet
import java.time.Instant

/**
 * A [FlowIORequest] represents an IO request of a flow when it suspends. It is persisted in checkpoints.
 */
@DeleteForDJVM
sealed class FlowIORequest<out R : Any> {
    /**
     * Send messages to sessions.
     *
     * @property sessionToMessage a map from session to message-to-be-sent.
     * @property shouldRetrySend specifies whether the send should be retried.
     */
    data class Send(
            val sessionToMessage: Map<FlowSession, SerializedBytes<Any>>
    ) : FlowIORequest<Unit>() {
        override fun toString() = "Send(sessionToMessage=${sessionToMessage.mapValues { it.value.hash }})"
    }

    /**
     * Receive messages from sessions.
     *
     * @property sessions the sessions to receive messages from.
     * @return a map from session to received message.
     */
    data class Receive(
            val sessions: NonEmptySet<FlowSession>
    ) : FlowIORequest<Map<FlowSession, SerializedBytes<Any>>>()

    /**
     * Send and receive messages from the specified sessions.
     *
     * @property sessionToMessage a map from session to message-to-be-sent. The keys also specify which sessions to
     *     receive from.
     * @property shouldRetrySend specifies whether the send should be retried.
     * @return a map from session to received message.
     */
    data class SendAndReceive(
            val sessionToMessage: Map<FlowSession, SerializedBytes<Any>>,
            val shouldRetrySend: Boolean
    ) : FlowIORequest<Map<FlowSession, SerializedBytes<Any>>>() {
        override fun toString() = "SendAndReceive(${sessionToMessage.mapValues { (key, value) ->
            "$key=${value.hash}"
        }}, shouldRetrySend=$shouldRetrySend)"
    }

    /**
     * Wait for a transaction to be committed to the database.
     *
     * @property hash the hash of the transaction.
     * @return the committed transaction.
     */
    data class WaitForLedgerCommit(val hash: SecureHash) : FlowIORequest<SignedTransaction>()

    /**
     * Get the FlowInfo of the specified sessions.
     *
     * @property sessions the sessions to get the FlowInfo of.
     * @return a map from session to FlowInfo.
     */
    data class GetFlowInfo(val sessions: NonEmptySet<FlowSession>) : FlowIORequest<Map<FlowSession, FlowInfo>>()

    /**
     * Suspend the flow until the specified time.
     *
     * @property wakeUpAfter the time to sleep until.
     */
    data class Sleep(val wakeUpAfter: Instant) : FlowIORequest<Unit>()

    /**
     * Suspend the flow until all Initiating sessions are confirmed.
     */
    object WaitForSessionConfirmations : FlowIORequest<Unit>()

    /**
     * Execute the specified [operation], suspend the flow until completion.
     */
    data class ExecuteAsyncOperation<T : Any>(val operation: FlowAsyncOperation<T>) : FlowIORequest<T>()
}
