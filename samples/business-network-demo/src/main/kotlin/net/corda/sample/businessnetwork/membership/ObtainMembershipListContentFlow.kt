package net.corda.sample.businessnetwork.membership

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name

/**
 * Flow to obtain content of the membership lists this node belongs to.
 */
@StartableByRPC
class ObtainMembershipListContentFlow(private val membershipListName: CordaX500Name) : FlowLogic<Set<AbstractParty>>(), MembershipAware {
    @Suspendable
    override fun call(): Set<AbstractParty> = getMembershipList(membershipListName, serviceHub).content()
}