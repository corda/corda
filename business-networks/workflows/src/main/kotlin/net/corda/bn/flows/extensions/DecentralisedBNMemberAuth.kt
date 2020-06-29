package net.corda.bn.flows.extensions

import net.corda.bn.states.MembershipState

/**
 * Concrete authorisation object implementation for decentralised Business Networks governance model.
 */
class DecentralisedBNMemberAuth : BNMemberAuth {

    override fun canRequestMembership(membership: MembershipState) = true

    override fun canActivateMembership(membership: MembershipState) = true

    override fun canSuspendMembership(membership: MembershipState) = true

    override fun canRevokeMembership(membership: MembershipState) = true
}