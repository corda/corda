package net.corda.node.services.events

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.SchedulableState
import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.node.services.VaultService
import net.corda.node.services.api.SchedulerService

/**
 * This observes the vault and schedules and unschedules activities appropriately based on state production and
 * consumption.
 */
class ScheduledActivityObserver private constructor(private val schedulerService: SchedulerService, private val FlowLogicRefFactory: FlowLogicRefFactory) {
    companion object {
        @JvmStatic
        fun install(vaultService: VaultService, schedulerService: SchedulerService, flowLogicRefFactory: FlowLogicRefFactory) {
            val observer = ScheduledActivityObserver(schedulerService, flowLogicRefFactory)
            vaultService.rawUpdates.subscribe { (consumed, produced) ->
                consumed.forEach { if (it.state.data is SchedulableState) schedulerService.unscheduleStateActivity(it.ref) }
                produced.forEach { observer.scheduleStateActivity(it) }
            }
        }

        // TODO: Beware we are calling dynamically loaded contract code inside here.
        private inline fun <T : Any> sandbox(code: () -> T?): T? {
            return code()
        }
    }

    private fun scheduleStateActivity(produced: StateAndRef<ContractState>) {
        val producedState = produced.state.data
        if (producedState is SchedulableState) {
            val scheduledAt = sandbox { producedState.nextScheduledActivity(produced.ref, FlowLogicRefFactory)?.scheduledAt } ?: return
            schedulerService.scheduleStateActivity(ScheduledStateRef(produced.ref, scheduledAt))
        }
    }
}
