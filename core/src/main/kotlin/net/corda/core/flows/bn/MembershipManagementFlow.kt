package net.corda.core.flows.bn

import net.corda.core.flows.FlowLogic

/**
 * Abstract flow which each storage implementation specific Business Network management flow must extend.
 */
abstract class MembershipManagementFlow<T> : FlowLogic<T>()