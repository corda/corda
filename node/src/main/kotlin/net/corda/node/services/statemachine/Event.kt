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

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.node.services.messaging.DeduplicationHandler
import java.util.*

/**
 * Transitions in the flow state machine are triggered by [Event]s that may originate from the flow itself or from
 * outside (e.g. in case of message delivery or external event).
 */
sealed class Event {
    /**
     * Check the current state for pending work. For example if the flow is waiting for a message from a particular
     * session this event may cause a flow resume if we have a corresponding message. In general the state machine
     * should be idempotent in the [DoRemainingWork] event, meaning a second subsequent event shouldn't modify the state
     * or produce [Action]s.
     */
    object DoRemainingWork : Event() {
        override fun toString() = "DoRemainingWork"
    }

    /**
     * Deliver a session message.
     * @param sessionMessage the message itself.
     * @param deduplicationHandler the handle to acknowledge the message after checkpointing.
     * @param sender the sender [Party].
     */
    data class DeliverSessionMessage(
            val sessionMessage: ExistingSessionMessage,
            override val deduplicationHandler: DeduplicationHandler,
            val sender: Party
    ) : Event(), GeneratedByExternalEvent

    /**
     * Signal that an error has happened. This may be due to an uncaught exception in the flow or some external error.
     * @param exception the exception itself.
     */
    data class Error(val exception: Throwable) : Event()

    /**
     * Signal that a ledger transaction has committed. This is an event completing a [FlowIORequest.WaitForLedgerCommit]
     * suspension.
     * @param transaction the transaction that was committed.
     */
    data class TransactionCommitted(val transaction: SignedTransaction) : Event()

    /**
     * Trigger a soft shutdown, removing the flow as soon as possible. This causes the flow to be removed as soon as
     * this event is processed. Note that on restart the flow will resume as normal.
     */
    object SoftShutdown : Event() {
        override fun toString() = "SoftShutdown"
    }

    /**
     * Start error propagation on a errored flow. This may be triggered by e.g. a [FlowHospital].
     */
    object StartErrorPropagation : Event() {
        override fun toString() = "StartErrorPropagation"
    }

    /**
     *
     * Scheduled by the flow.
     *
     * Initiate a flow. This causes a new session object to be created and returned to the flow. Note that no actual
     * communication takes place at this time, only on the first send/receive operation on the session.
     * @param party the [Party] to create a session with.
     */
    data class InitiateFlow(val party: Party) : Event()

    /**
     * Signal the entering into a subflow.
     *
     * Scheduled and executed by the flow.
     *
     * @param subFlowClass the [Class] of the subflow, to be used to determine whether it's Initiating or inlined.
     */
    data class EnterSubFlow(val subFlowClass: Class<FlowLogic<*>>) : Event()

    /**
     * Signal the leaving of a subflow.
     *
     * Scheduled by the flow.
     *
     */
    object LeaveSubFlow : Event() {
        override fun toString() = "LeaveSubFlow"
    }

    /**
     * Signal a flow suspension. This causes the flow's stack and the state machine's state together with the suspending
     * IO request to be persisted into the database.
     *
     * Scheduled by the flow and executed inside the park closure.
     *
     * @param ioRequest the request triggering the suspension.
     * @param maySkipCheckpoint indicates whether the persistence may be skipped.
     * @param fiber the serialised stack of the flow.
     */
    data class Suspend(
            val ioRequest: FlowIORequest<*>,
            val maySkipCheckpoint: Boolean,
            val fiber: SerializedBytes<FlowStateMachineImpl<*>>
    ) : Event() {
        override fun toString() =
                "Suspend(" +
                        "ioRequest=$ioRequest, " +
                        "maySkipCheckpoint=$maySkipCheckpoint, " +
                        "fiber=${fiber.hash}, " +
                        ")"
    }

    /**
     * Signals clean flow finish.
     *
     * Scheduled by the flow.
     *
     * @param returnValue the return value of the flow.
     * @param softLocksId the flow ID of the flow if it is holding soft locks, else null.
     */
    data class FlowFinish(val returnValue: Any?, val softLocksId: UUID?) : Event()

    /**
     * Signals the completion of a [FlowAsyncOperation].
     *
     * Scheduling is triggered by the service that completes the future returned by the async operation.
     *
     * @param returnValue the result of the operation.
     */
    data class AsyncOperationCompletion(val returnValue: Any?) : Event()

    /**
     * Retry a flow from the last checkpoint, or if there is no checkpoint, restart the flow with the same invocation details.
     */
    object RetryFlowFromSafePoint : Event() {
        override fun toString() = "RetryFlowFromSafePoint"
    }

    /**
     * Indicates that an event was generated by an external event and that external event needs to be replayed if we retry the flow,
     * even if it has not yet been processed and placed on the pending de-duplication handlers list.
     */
    interface GeneratedByExternalEvent {
        val deduplicationHandler: DeduplicationHandler
    }
}
