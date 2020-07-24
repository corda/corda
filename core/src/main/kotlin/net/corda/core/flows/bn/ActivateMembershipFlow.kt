package net.corda.core.flows.bn

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.bn.BusinessNetworksService

/**
 * Public RPC exposed core flow responsible for Business Network Membership activation.
 *
 * @property membershipId ID of the membership to be activated.
 * @property notary Optional notary to be used in case of Corda transaction specific implementation.
 */
@StartableByRPC
class ActivateMembershipFlow(private val membershipId: UniqueIdentifier, private val notary: Party? = null) : MembershipManagementCoreFlow() {

    @Suspendable
    override fun getConcreteImplementationFlow(service: BusinessNetworksService): MembershipManagementFlow<*> = service.activateMembershipFlow(membershipId, notary)
}