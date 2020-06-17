package net.corda.bn.flows.extensions

import net.corda.bn.states.MembershipState

/**
 * Interface which holds authorisation methods. All the membership based flow initiation authorisation must be done via this interface.
 */
interface BNMemberAuth {

    /**
     * Returns whether Business Network member with [membership] state can request membership for other members.
     */
    fun canRequestMembership(membership: MembershipState): Boolean

    /**
     * Returns whether Business Network member with [membership] state can activate memberships.
     */
    fun canActivateMembership(membership: MembershipState): Boolean

    /**
     * Returns whether Business Network member with [membership] state can suspend memberships.
     */
    fun canSuspendMembership(membership: MembershipState): Boolean

    /**
     * Returns whether Business Network member with [membership] state can revoke memberships.
     */
    fun canRevokeMembership(membership: MembershipState): Boolean
}