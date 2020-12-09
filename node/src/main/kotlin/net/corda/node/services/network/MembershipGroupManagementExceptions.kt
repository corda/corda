package net.corda.node.services.network

import net.corda.core.flows.FlowException
import net.corda.core.identity.Party

class DuplicateMembershipException(party: Party, groupId: String)
    : FlowException("$party is already part of Membership Group with $groupId ID")

class MembershipAuthorisationException(party: Party, groupId: String)
    : FlowException("$party is not manager of the Membership Group with $groupId ID")

class MembershipNotFoundException(party: Party, groupId: String)
    : FlowException("$party is not member of the Membership Group with $groupId ID")

class IllegalMembershipStatusException(message: String) : FlowException(message)

class UnsafeMembershipGroupOperationException(message: String) : Exception(message)
