package com.r3corda.node.services.monitor

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.node.utilities.AddOrRemove
import java.security.PublicKey
import java.time.Instant
import java.util.*

/**
 * Events triggered by changes in the node, and sent to monitoring client(s).
 */
sealed class ServiceToClientEvent(val time: Instant) {
    class Transaction(time: Instant, val transaction: SignedTransaction) : ServiceToClientEvent(time)
    class OutputState(time: Instant, val consumed: Set<StateRef>, val produced: Set<StateAndRef<ContractState>>) : ServiceToClientEvent(time)
    class StateMachine(time: Instant, val fiberId: Long, val label: String, val addOrRemove: AddOrRemove) : ServiceToClientEvent(time)
    class Progress(time: Instant, val fiberId: Long, val message: String) : ServiceToClientEvent(time)
    class TransactionBuild(time: Instant, val id: UUID, val state: TransactionBuildResult) : ServiceToClientEvent(time)
}

sealed class TransactionBuildResult {
    /**
     * State indicating the action undertaken has been completed (it was not complex enough to require a
     * state machine starting).
     *
     * @param transaction the transaction created as a result.
     */
    // TODO: We should have a consistent "Transaction your request triggered has been built" event, rather than these
    // once-off results from a request. Unclear if that means all requests need to trigger a protocol state machine,
    // so the client sees a consistent process, or if some other solution can be found.
    class Complete(val transaction: SignedTransaction, val message: String?) : TransactionBuildResult()

    /**
     * State indicating that a protocol is managing this request, and that the client should track protocol state machine
     * updates for further information. The monitor will separately receive notification of the state machine having been
     * added, as it would any other state machine. This response is used solely to enable the monitor to identify
     * the state machine (and its progress) as associated with the request.
     *
     * @param transaction the transaction created as a result, in the case where the protocol has completed.
     */
    class ProtocolStarted(val fiberId: Long, val transaction: SignedTransaction?, val message: String?) : TransactionBuildResult()

    /**
     * State indicating the action undertaken failed, either directly (it is not something which requires a
     * state machine), or before a state machine was started.
     */
    class Failed(val message: String?) : TransactionBuildResult()
}
