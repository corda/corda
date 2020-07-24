package net.corda.core.flows.bn

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.bn.BusinessNetworksService

/**
 * Abstract flow which each core Business Network management flow must extend. Instantiates concrete flow storage implementation specific
 * Business Network management flow and calls it.
 */
abstract class MembershipManagementCoreFlow : FlowLogic<Unit>() {

    /**
     * Instantiates concrete flow storage implementation specific Business Network management flow.
     *
     * @param service Business Network Service used to instantiate the flow.
     */
    protected abstract fun getConcreteImplementationFlow(service: BusinessNetworksService): MembershipManagementFlow<*>

    @Suspendable
    override fun call() {
        val service = serviceHub.businessNetworksService ?: throw FlowException("Business Network Service not initialised")
        subFlow(getConcreteImplementationFlow(service))
    }
}