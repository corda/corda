package com.r3corda.node.services.messaging

import com.r3corda.core.contracts.ClientToServiceCommand
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.NetworkMapCache
import com.r3corda.core.node.services.StateMachineTransactionMapping
import com.r3corda.core.node.services.Vault
import com.r3corda.core.protocols.StateMachineRunId
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.node.services.statemachine.ProtocolStateMachineImpl
import com.r3corda.node.services.statemachine.StateMachineManager
import com.r3corda.node.utilities.AddOrRemove
import rx.Observable

data class StateMachineInfo(
        val id: StateMachineRunId,
        val protocolLogicClassName: String,
        val progressTrackerStepAndUpdates: Pair<String, Observable<String>>?
) {
    companion object {
        fun fromProtocolStateMachineImpl(psm: ProtocolStateMachineImpl<*>): StateMachineInfo {
            return StateMachineInfo(
                    id = psm.id,
                    protocolLogicClassName = psm.logic.javaClass.simpleName,
                    progressTrackerStepAndUpdates = psm.logic.track()
            )
        }
    }
}

sealed class StateMachineUpdate(val id: StateMachineRunId) {
    class Added(val stateMachineInfo: StateMachineInfo) : StateMachineUpdate(stateMachineInfo.id)
    class Removed(id: StateMachineRunId) : StateMachineUpdate(id)

    companion object {
        fun fromStateMachineChange(change: StateMachineManager.Change): StateMachineUpdate {
            return when (change.addOrRemove) {
                AddOrRemove.ADD -> {
                    val stateMachineInfo = StateMachineInfo(
                            id = change.id,
                            protocolLogicClassName = change.logic.javaClass.simpleName,
                            progressTrackerStepAndUpdates = change.logic.track()
                    )
                    StateMachineUpdate.Added(stateMachineInfo)
                }
                AddOrRemove.REMOVE -> {
                    StateMachineUpdate.Removed(change.id)
                }
            }
        }
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
    class ProtocolStarted(val id: StateMachineRunId, val transaction: SignedTransaction?, val message: String?) : TransactionBuildResult() {
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

/**
 * RPC operations that the node exposes to clients using the Java client library. These can be called from
 * client apps and are implemented by the node in the [ServerRPCOps] class.
 */
interface CordaRPCOps : RPCOps {
    /**
     * Returns a pair of currently in-progress state machine infos and an observable of future state machine adds/removes.
     */
    @RPCReturnsObservables
    fun stateMachinesAndUpdates(): Pair<List<StateMachineInfo>, Observable<StateMachineUpdate>>

    /**
     * Returns a pair of head states in the vault and an observable of future updates to the vault.
     */
    @RPCReturnsObservables
    fun vaultAndUpdates(): Pair<List<StateAndRef<ContractState>>, Observable<Vault.Update>>

    /**
     * Returns a pair of all recorded transactions and an observable of future recorded ones.
     */
    @RPCReturnsObservables
    fun verifiedTransactions(): Pair<List<SignedTransaction>, Observable<SignedTransaction>>

    /**
     * Returns a snapshot list of existing state machine id - recorded transaction hash mappings, and a stream of future
     * such mappings as well.
     */
    @RPCReturnsObservables
    fun stateMachineRecordedTransactionMapping(): Pair<List<StateMachineTransactionMapping>, Observable<StateMachineTransactionMapping>>

    /**
     * Returns all parties currently visible on the network with their advertised services and an observable of future updates to the network.
     */
    @RPCReturnsObservables
    fun networkMapUpdates(): Pair<List<NodeInfo>, Observable<NetworkMapCache.MapChange>>

    /**
     * Executes the given command, possibly triggering cash creation etc.
     * TODO: The signature of this is weird because it's the remains of an old service call, we should have a call for each command instead.
     */
    fun executeCommand(command: ClientToServiceCommand): TransactionBuildResult
}