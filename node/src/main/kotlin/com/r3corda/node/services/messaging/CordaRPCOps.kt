package com.r3corda.node.services.messaging

import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.crypto.SecureHash
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

sealed class StateMachineUpdate {
    class Added(val stateMachineInfo: StateMachineInfo) : StateMachineUpdate()
    class Removed(val stateMachineRunId: StateMachineRunId) : StateMachineUpdate()

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
    @RPCReturnsObservables
    fun stateMachineRecordedTransactionMapping(): Pair<List<StateMachineTransactionMapping>, Observable<StateMachineTransactionMapping>>
}
