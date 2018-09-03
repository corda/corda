package net.corda.sample.businessnetwork.membership.flow

import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.sample.businessnetwork.membership.internal.MembershipListProvider
import org.slf4j.LoggerFactory

interface MembershipAware {
    /**
     * Checks that party is included into the specified membership list.
     */
    fun <T> AbstractParty.checkMembership(membershipName: CordaX500Name, initiatorFlow: FlowLogic<T>) {
        LoggerFactory.getLogger(javaClass).debug("Checking membership of party '${this.nameOrNull()}' in membership list '$membershipName'")
        val membershipList = getMembershipList(membershipName, initiatorFlow.serviceHub)
        if (this !in membershipList) {
            val msg = "'$this' doesn't belong to membership list: ${membershipName.organisation}"
            throw MembershipViolationException(msg)
        }
    }

    fun getMembershipList(listName: CordaX500Name, serviceHub: ServiceHub): MembershipList {
        LoggerFactory.getLogger(javaClass).debug("Obtaining membership list for name '$listName'")
        return MembershipListProvider.obtainMembershipList(listName, serviceHub.networkMapCache)
    }
}

class MembershipViolationException(val msg: String) : FlowException(msg)

class InvalidMembershipListNameException(val membershipListName: CordaX500Name) : FlowException("Business Network owner node not found for: $membershipListName")