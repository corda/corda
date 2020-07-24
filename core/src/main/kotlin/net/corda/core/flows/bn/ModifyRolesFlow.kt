package net.corda.core.flows.bn

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.bn.BNRole
import net.corda.core.node.services.bn.BusinessNetworksService

/**
 * Public RPC exposed core flow responsible for Business Network Membership's roles modification.
 *
 * @property membershipId ID of the Business Network Membership to be modified.
 * @property roles Set of roles to be assigned to Business Network Membership.
 * @property notary Optional notary to be used in case of Corda transaction specific implementation.
 */
@StartableByRPC
class ModifyRolesFlow(private val membershipId: UniqueIdentifier, private val roles: Set<BNRole>, private val notary: Party? = null) : MembershipManagementCoreFlow() {

    @Suspendable
    override fun getConcreteImplementationFlow(service: BusinessNetworksService): MembershipManagementFlow<*> = service.modifyRolesFlow(membershipId, roles, notary)
}