package net.corda.node.services.events

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.SchedulableState
import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.node.services.api.ServiceHubInternal

/**
 * This observes the vault and schedules and unschedules activities appropriately based on state production and
 * consumption.
 */
class ScheduledActivityObserver(val services: ServiceHubInternal) {
    init {
        services.vaultService.rawUpdates.subscribe { update ->
            update.consumed.forEach { services.schedulerService.unscheduleStateActivity(it.ref) }
            update.produced.forEach { scheduleStateActivity(it, services.flowLogicRefFactory) }
        }
    }

    private fun scheduleStateActivity(produced: StateAndRef<ContractState>, flowLogicRefFactory: FlowLogicRefFactory) {
        val producedState = produced.state.data
        if (producedState is SchedulableState) {
            val scheduledAt = sandbox { producedState.nextScheduledActivity(produced.ref, flowLogicRefFactory)?.scheduledAt } ?: return
            services.schedulerService.scheduleStateActivity(ScheduledStateRef(produced.ref, scheduledAt))
        }
    }

    // TODO: Beware we are calling dynamically loaded contract code inside here.
    private inline fun <T : Any> sandbox(code: () -> T?): T? {
        return code()
    }
}
