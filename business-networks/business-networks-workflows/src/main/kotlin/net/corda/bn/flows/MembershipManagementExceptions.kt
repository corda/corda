package net.corda.bn.flows

import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException

/**
 * Exception thrown whenever Business Network with [MembershipState.networkId] already exists.
 *
 * @property networkId ID of the already existing Business Network.
 */
class DuplicateBusinessNetworkException(val networkId: UniqueIdentifier) : FlowException("Business Network with $networkId ID already exists")

/**
 * Exception thrown whenever Business Network Group with [GroupState.linearId] already exists.
 *
 * @property groupId ID of the already existing Business Network Group.
 */
class DuplicateBusinessNetworkGroupException(val groupId: UniqueIdentifier) : FlowException("Business Network Group with $groupId already exists.")

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

/**
 * Exception thrown by any [MembershipManagementFlow] whenever Business Network group with provided [GroupState.linearId] doesn't exist.
 */
class BusinessNetworkGroupNotFoundException(message: String) : FlowException(message)

/**
 * Exception thrown by any [MembershipManagementFlow] whenever member remains without participation in any Business Network Group.
 */
class MembershipMissingGroupParticipationException(message: String) : FlowException(message)

/**
 * [MembershipManagementFlow] version of [IllegalArgumentException]
 */
class IllegalFlowArgumentException(message: String) : FlowException(message)

/**
 * Exception thrown by any [MembershipManagementFlow] whenever group ends up in illegal state.
 */
class IllegalBusinessNetworkGroupStateException(message: String) : FlowException(message)