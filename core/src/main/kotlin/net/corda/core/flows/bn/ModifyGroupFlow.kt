package net.corda.core.flows.bn

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.bn.BusinessNetworksService

/**
 * Public RPC exposed core flow responsible for Business Network Group modification.
 *
 * @property groupId ID of the group to be modified.
 * @property name New name of modified group.
 * @property participants New participants of modified group.
 * @property notary Optional notary to be used in case of Corda transaction specific implementation.
 */
@StartableByRPC
class ModifyGroupFlow(
        private val groupId: UniqueIdentifier,
        private val name: String? = null,
        private val participants: Set<UniqueIdentifier>? = null,
        private val notary: Party? = null
) : MembershipManagementCoreFlow() {

    @Suspendable
    override fun getConcreteImplementationFlow(service: BusinessNetworksService): MembershipManagementFlow<*> = service.modifyGroupFlow(groupId, name, participants, notary)
}