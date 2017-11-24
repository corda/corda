package net.corda.sample.businessnetwork.membership

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name

class CheckMembershipFlow(private val membershipName: CordaX500Name, private val counterParty: AbstractParty) : FlowLogic<Unit>(), MembershipAware {
    override fun call() {
        counterParty.checkMembership(membershipName, this)
    }
}