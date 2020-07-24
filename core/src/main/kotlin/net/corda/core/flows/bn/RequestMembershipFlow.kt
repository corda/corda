package net.corda.core.flows.bn

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.bn.BNIdentity
import net.corda.core.node.services.bn.BusinessNetworksService

/**
 * Public RPC exposed core flow responsible for Business Network Membership request.
 *
 * @property authorisedParty Identity of authorised member from whom the membership activation is requested.
 * @property networkId ID of the Business Network that potential new member wants to join.
 * @property businessIdentity Custom business identity to be given to membership.
 * @property notary Optional notary to be used in case of Corda transaction specific implementation.
 */
@StartableByRPC
class RequestMembershipFlow(
        private val authorisedParty: Party,
        private val networkId: String,
        private val businessIdentity: BNIdentity? = null,
        private val notary: Party? = null
) : MembershipManagementCoreFlow() {

    @Suspendable
    override fun getConcreteImplementationFlow(service: BusinessNetworksService): MembershipManagementFlow<*> = service.requestMembershipFlow(authorisedParty, networkId, businessIdentity, notary)
}