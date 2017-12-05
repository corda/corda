package net.corda.sample.businessnetwork.membership

import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.sample.businessnetwork.membership.internal.MembershipListProvider

interface MembershipAware {
    /**
     * Checks that party has at least one common membership list with current node.
     * TODO: This functionality ought to be moved into a dedicated CordaService.
     */
    fun <T> AbstractParty.checkMembership(membershipName: CordaX500Name, initiatorFlow: FlowLogic<T>) {
        val membershipList = getMembershipList(membershipName, initiatorFlow.serviceHub)
        if (this !in membershipList) {
            val msg = "'$this' doesn't belong to membership list: ${membershipName.commonName}"
            throw MembershipViolationException(msg)
        }
    }

    fun getMembershipList(listName: CordaX500Name, serviceHub: ServiceHub): MembershipList = MembershipListProvider.obtainMembershipList(listName, serviceHub.networkMapCache)
}

class MembershipViolationException(msg: String) : FlowException(msg)