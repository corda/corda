package net.corda.node.services.vault

import net.corda.core.contracts.SoftLockableState
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.node.services.VaultService
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.NonEmptySet.Companion.toNonEmptySet
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.StateMachineManager
import java.util.*

class VaultSoftLockManager(val vault: VaultService, smm: StateMachineManager) {
    private companion object {
        val log = loggerFor<VaultSoftLockManager>()
    }

    init {
        smm.changes.subscribe { change ->
            if (change is StateMachineManager.Change.Removed && FlowStateMachineImpl.currentStateMachine()?.hasSoftLockedStates == true) {
                log.trace { "Remove flow name ${change.logic.javaClass} with id $change.id" }
                unregisterSoftLocks(change.logic.runId, change.logic)
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
        vault.rawUpdates.subscribe exit@ { (_, produced, flowId) ->
            registerSoftLocks(
                    flowId ?: return@exit,
                    produced.stream().filter { (it.state.data as? SoftLockableState)?.softLockable ?: false }
                            .map { it.ref }
                            .toNonEmptySet() ?: return@exit)
        }
    }

    private fun registerSoftLocks(flowId: UUID, stateRefs: NonEmptySet<StateRef>) {
        log.trace { "Reserving soft locks for flow id $flowId and states $stateRefs" }
        vault.softLockReserve(flowId, stateRefs)
    }

    private fun unregisterSoftLocks(id: StateMachineRunId, logic: FlowLogic<*>) {
        val flowClassName = logic.javaClass.simpleName
        log.trace { "Releasing soft locks for flow $flowClassName with flow id ${id.uuid}" }
        vault.softLockRelease(id.uuid)
    }
}