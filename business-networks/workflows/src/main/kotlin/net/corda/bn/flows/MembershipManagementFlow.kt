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
        if (!databaseService.businessNetworkExists(networkId)) {
            throw BusinessNetworkNotFoundException("Business Network with $networkId doesn't exist")
        }
        val ourMembership = databaseService.getMembership(networkId, ourIdentity)?.state?.data
                ?: throw MembershipNotFoundException("$ourIdentity is not member of a business network")
        if (!ourMembership.isActive()) {
            throw IllegalMembershipStatusException("Membership owned by $ourIdentity is not active")
        }
        if (!authorisationMethod(ourMembership)) {
            throw MembershipAuthorisationException("$ourIdentity is not authorised to activate membership")
        }
    }
}

/**
 * Exception thrown by any [MembershipManagementFlow] whenever Business Network with provided [MembershipState.networkId] doesn't exist.
 */
class BusinessNetworkNotFoundException(message: String) : FlowException(message)

/**
 * Exception thrown by any [MembershipManagementFlow] whenever provided parties membership doesn't exist.
 */
class MembershipNotFoundException(message: String) : FlowException(message)

/**
 * Exception thrown by any [MembershipManagementFlow] whenever provided member's state is not appropriate for the context.
 */
class IllegalMembershipStatusException(message: String) : FlowException(message)

/**
 * Exception thrown by any [MembershipManagementFlow] whenever membership fails role based authorisation.
 */
class MembershipAuthorisationException(message: String) : FlowException(message)
