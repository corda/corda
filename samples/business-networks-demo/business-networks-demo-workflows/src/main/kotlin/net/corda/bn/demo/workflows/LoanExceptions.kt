package net.corda.bn.demo.workflows

import net.corda.core.flows.FlowException

/**
 * Exception thrown when membership's business identity is in illegal state.
 */
class IllegalMembershipBusinessIdentityException(message: String) : FlowException(message)

/**
 * Exception thrown whenever non authorised node start flow.
 */
class IllegalFlowInitiatorException(message: String) : FlowException(message)

/**
 * Exception thrown whenever loan state with given linear ID is not found.
 */
class LoanNotFoundException(message: String) : FlowException(message)