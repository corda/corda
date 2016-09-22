package com.r3corda.node.internal

import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.node.services.Vault
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.services.messaging.CordaRPCOps
import com.r3corda.node.services.messaging.StateMachineInfo
import com.r3corda.node.services.messaging.StateMachineUpdate
import com.r3corda.node.services.statemachine.StateMachineManager
import com.r3corda.node.utilities.databaseTransaction
import org.jetbrains.exposed.sql.Database
import rx.Observable

/**
 * Server side implementations of RPCs available to MQ based client tools. Execution takes place on the server
 * thread (i.e. serially). Arguments are serialised and deserialised automatically.
 */
class ServerRPCOps(
        val services: ServiceHubInternal,
        val stateMachineManager: StateMachineManager,
        val database: Database
) : CordaRPCOps {
    override val protocolVersion: Int = 0

    override fun vaultAndUpdates(): Pair<List<StateAndRef<ContractState>>, Observable<Vault.Update>> {
        return databaseTransaction(database) {
            val (vault, updates) = services.vaultService.track()
            Pair(vault.states.toList(), updates)
        }
    }
    override fun verifiedTransactions() = services.storageService.validatedTransactions.track()
    override fun stateMachinesAndUpdates(): Pair<List<StateMachineInfo>, Observable<StateMachineUpdate>> {
        val (allStateMachines, changes) = stateMachineManager.track()
        return Pair(
                allStateMachines.map { StateMachineInfo.fromProtocolStateMachineImpl(it) },
                changes.map { StateMachineUpdate.fromStateMachineChange(it) }
        )
    }
}
