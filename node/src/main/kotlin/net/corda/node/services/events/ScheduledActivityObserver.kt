package net.corda.node.services.events

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.SchedulableState
import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.VaultService
import net.corda.node.services.api.SchedulerService
import net.corda.node.services.statemachine.FlowLogicRefFactoryImpl

/**
 * This observes the vault and schedules and unschedules activities appropriately based on state production and
 * consumption.
 */
class ScheduledActivityObserver(vaultService: VaultService, private val schedulerService: SchedulerService) {
    init {
        vaultService.rawUpdates.subscribe { (consumed, produced) ->
            consumed.forEach { schedulerService.unscheduleStateActivity(it.ref) }
            produced.forEach { scheduleStateActivity(it) }
        }
    }

    private fun scheduleStateActivity(produced: StateAndRef<ContractState>) {
        val producedState = produced.state.data
        if (producedState is SchedulableState) {
            val scheduledAt = sandbox { producedState.nextScheduledActivity(produced.ref, FlowLogicRefFactoryImpl)?.scheduledAt } ?: return
            schedulerService.scheduleStateActivity(ScheduledStateRef(produced.ref, scheduledAt))
        }
    }

    // TODO: Beware we are calling dynamically loaded contract code inside here.
    private inline fun <T : Any> sandbox(code: () -> T?): T? {
        return code()
    }
}
