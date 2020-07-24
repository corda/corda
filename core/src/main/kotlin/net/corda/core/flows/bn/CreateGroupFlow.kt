package net.corda.core.flows.bn

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.bn.BusinessNetworksService

/**
 * Public RPC exposed core flow responsible for new Business Network Group creation.
 *
 * @property networkId ID of the Business Network that Business Network Group will relate to.
 * @property groupId Custom ID to be given to the initial Business Network group. If not specified, randomly selected one will be used.
 * @property groupName Optional name to be given to Business Network group.
 * @property additionalParticipants Set of participants to be added to created Business Network Group alongside initiator's identity.
 * @property notary Optional notary to be used in case of Corda transaction specific implementation.
 */
@StartableByRPC
class CreateGroupFlow(
        private val networkId: String,
        private val groupId: UniqueIdentifier = UniqueIdentifier(),
        private val groupName: String? = null,
        private val additionalParticipants: Set<UniqueIdentifier> = emptySet(),
        private val notary: Party? = null
) : MembershipManagementCoreFlow() {

    @Suspendable
    override fun getConcreteImplementationFlow(service: BusinessNetworksService): MembershipManagementFlow<*> = service.createGroupFlow(networkId, groupId, groupName, additionalParticipants, notary)
}