package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.states.MembershipState
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic

/**
 * This abstract class is extended by any flow which will use common membership management helper methods.
 */
abstract class MembershipManagementFlow<T> : FlowLogic<T>() {

    /**
     * Performs authorisation checks of the flow initiator using provided authorisation methods.
     *
     * @param networkId ID of the Business Network in which we perform authorisation.
     * @param databaseService Service used to query vault for memberships.
     * @param authorisationMethod Method which does actual authorisation check over membership.
     */
    @Suppress("ThrowsCount")
    @Suspendable
    protected fun authorise(networkId: String, databaseService: DatabaseService, authorisationMethod: (MembershipState) -> Boolean) {
        val ourMembership = databaseService.getMembership(networkId, ourIdentity)?.state?.data
                ?: throw FlowException("Receiver is not member of a business network")
        if (!ourMembership.isActive()) {
            throw FlowException("Receiver's membership is not active")
        }
        if (!authorisationMethod(ourMembership)) {
            throw FlowException("Receiver is not authorised to activate membership")
        }
    }
}