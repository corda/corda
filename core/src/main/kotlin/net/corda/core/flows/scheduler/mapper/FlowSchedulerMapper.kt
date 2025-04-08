package net.corda.core.flows.scheduler.mapper

import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name

/**
 * This interface should be implemented by classes that contain the logic
 * for determining which scheduler to use for a given flow. This interface is only relevant for Enterprise users.
 */
interface FlowSchedulerMapper {
    fun getScheduler(
            invocationContext: InvocationContext,
            flowLogic: Class<out FlowLogic<*>>,
            ourIdentity: CordaX500Name
    ): String
}
