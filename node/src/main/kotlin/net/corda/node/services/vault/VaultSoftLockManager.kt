package net.corda.node.services.vault

import net.corda.core.contracts.FungibleAsset
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.VaultService
import net.corda.core.utilities.*
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.nodeapi.internal.persistence.contextDatabase
import java.util.*

class VaultSoftLockManager private constructor(private val vault: VaultService) {
    companion object {
        private val log = contextLogger()
        @JvmStatic
        fun install(vault: VaultService, smm: StateMachineManager) {
            val manager = VaultSoftLockManager(vault)
            smm.changes.subscribe { change ->
                if (change is StateMachineManager.Change.Removed) {
                    val logic = change.logic
                    // Don't run potentially expensive query if the flow didn't lock any states:
                    if ((logic.stateMachine as FlowStateMachineImpl<*>).hasSoftLockedStates) {
                        manager.unregisterSoftLocks(logic.runId.uuid, logic)
                    }
                }
            }
            // Discussion
            //
            // The intent of the following approach is to support what might be a common pattern in a flow:
            //      1. Create state
            //      2. Do something with state
            //  without possibility of another flow intercepting the state between 1 and 2,
            //  since we cannot lock the state before it exists. e.g. Issue and then Move some Cash.
            //
            //  The downside is we could have a long running flow that holds a lock for a long period of time.
            //  However, the lock can be programmatically released, like any other soft lock,
            //  should we want a long running flow that creates a visible state mid way through.
            vault.rawUpdates.subscribe { (_, produced, flowId) ->
                if (flowId != null) {
                    val fungible = produced.filter { it.state.data is FungibleAsset<*> }
                    if (fungible.isNotEmpty()) {
                        manager.registerSoftLocks(flowId, fungible.map { it.ref }.toNonEmptySet())
                    }
                }
            }
        }
    }

    private fun registerSoftLocks(flowId: UUID, stateRefs: NonEmptySet<StateRef>) {
        log.trace { "Reserving soft locks for flow id $flowId and states $stateRefs" }
        contextDatabase.transaction {
            vault.softLockReserve(flowId, stateRefs)
        }
    }

    private fun unregisterSoftLocks(flowId: UUID, logic: FlowLogic<*>) {
        log.trace { "Releasing soft locks for flow ${logic.javaClass.simpleName} with flow id $flowId" }
        contextDatabase.transaction {
            vault.softLockRelease(flowId)
        }
    }
}