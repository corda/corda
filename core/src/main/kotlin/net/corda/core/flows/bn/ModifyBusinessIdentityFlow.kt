package net.corda.core.flows.bn

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.bn.BNIdentity
import net.corda.core.node.services.bn.BusinessNetworksService

/**
 * Public RPC exposed core flow responsible for Business Network Membership's business identity modification.
 *
 * @property membershipId ID of the Business Network Membership to be modified.
 * @property businessIdentity Custom business identity to be given to Business Network Membership.
 * @property notary Optional notary to be used in case of Corda transaction specific implementation.
 */
@StartableByRPC
class ModifyBusinessIdentityFlow(
        private val membershipId: UniqueIdentifier,
        private val businessIdentity: BNIdentity,
        private val notary: Party? = null
) : MembershipManagementCoreFlow() {

    @Suspendable
    override fun getConcreteImplementationFlow(service: BusinessNetworksService): MembershipManagementFlow<*> = service.modifyBusinessIdentityFlow(membershipId, businessIdentity, notary)
}