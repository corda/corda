package net.corda.node.internal.membership.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.node.services.api.MembershipGroupCacheInternal
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.network.MembershipAuthorisationException

abstract class MembershipGroupManagementFlow : FlowLogic<Unit>() {

    protected val membershipGroupCache: MembershipGroupCacheInternal
        get() = (serviceHub as ServiceHubInternal).membershipGroupCache

    protected val groupId: String get() = serviceHub.membershipGroupCache.managerInfo.memberId

    @Suspendable
    protected fun authorise() {
        if (!membershipGroupCache.isManager(ourIdentity)) {
            throw MembershipAuthorisationException(ourIdentity, groupId)
        }
    }
}
