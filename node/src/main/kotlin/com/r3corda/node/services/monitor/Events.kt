package com.r3corda.node.services.monitor

import com.r3corda.core.contracts.*
import com.r3corda.core.transactions.LedgerTransaction
import com.r3corda.node.utilities.AddOrRemove
import java.time.Instant
import java.util.*

/**
 * Events triggered by changes in the node, and sent to monitoring client(s).
 */
sealed class ServiceToClientEvent(val time: Instant) {
    class Transaction(time: Instant, val transaction: LedgerTransaction) : ServiceToClientEvent(time) {
        override fun toString() = "Transaction(${transaction.commands})"
    }
    class OutputState(
            time: Instant,
            val consumed: Set<StateRef>,
            val produced: Set<StateAndRef<ContractState>>
    ) : ServiceToClientEvent(time) {
        override fun toString() = "OutputState(consumed=$consumed, produced=${produced.map { it.state.data.javaClass.simpleName } })"
    }
    class StateMachine(
            time: Instant,
            val fiberId: Long,
            val label: String,
            val addOrRemove: AddOrRemove
    ) : ServiceToClientEvent(time) {
        override fun toString() = "StateMachine($label, ${addOrRemove.name})"
    }
    class Progress(time: Instant, val fiberId: Long, val message: String) : ServiceToClientEvent(time) {
        override fun toString() = "Progress($message)"
    }
    class TransactionBuild(time: Instant, val id: UUID, val state: TransactionBuildResult) : ServiceToClientEvent(time) {
        override fun toString() = "TransactionBuild($state)"
    }

}

sealed class TransactionBuildResult {
    /**
     * State indicating that a protocol is managing this request, and that the client should track protocol state machine
     * updates for further information. The monitor will separately receive notification of the state machine having been
     * added, as it would any other state machine. This response is used solely to enable the monitor to identify
     * the state machine (and its progress) as associated with the request.
     *
     * @param transaction the transaction created as a result, in the case where the protocol has completed.
     */
    class ProtocolStarted(val fiberId: Long, val transaction: LedgerTransaction?, val message: String?) : TransactionBuildResult() {
        override fun toString() = "Started($message)"
    }

    /**
     * State indicating the action undertaken failed, either directly (it is not something which requires a
     * state machine), or before a state machine was started.
     */
    class Failed(val message: String?) : TransactionBuildResult() {
        override fun toString() = "Failed($message)"
    }
}
