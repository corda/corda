package net.corda.node.services.network

import net.corda.core.identity.Party
import net.corda.core.messaging.FlowHandle
import net.corda.core.node.AppServiceHub
import net.corda.core.node.MemberInfo
import net.corda.core.node.MemberStatus
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.MembershipGroupService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.internal.membership.flows.MemberInfoUpdate
import net.corda.node.internal.membership.flows.MemberInfoUpdateType
import net.corda.node.internal.membership.flows.PublishMemberInfoFlow
import net.corda.node.internal.membership.flows.RequestMembershipFlow
import net.corda.node.internal.membership.flows.SendMemberInfoUpdatesFlow
import net.corda.node.internal.membership.flows.SyncMembershipGroupCacheFlow
import net.corda.node.services.api.ServiceHubInternal

@CordaService
class FlowMembershipGroupService(private val appServiceHub: AppServiceHub) : MembershipGroupService, SingletonSerializeAsToken() {

    private val membershipGroupCache = (appServiceHub as ServiceHubInternal).membershipGroupCache

    private val groupId: String get() = membershipGroupCache.managerInfo.memberId

    override fun requestMembership() = appServiceHub.startFlow(RequestMembershipFlow())

    override fun activateMembership(party: Party): List<FlowHandle<Unit>> {
        authorise()
        if (membershipGroupCache.isManager(party)) {
            throw UnsafeMembershipGroupOperationException("Membership Group Manager can't activate itself")
        }

        val memberInfo = membershipGroupCache.getMemberInfo(party) ?: throw MembershipNotFoundException(party, groupId)

        return updateMemberInfo(party, memberInfo, memberInfo.copy(status = MemberStatus.ACTIVE))
    }

    override fun revokeMembership(party: Party): List<FlowHandle<Unit>> {
        authorise()
        if (membershipGroupCache.isManager(party)) {
            throw UnsafeMembershipGroupOperationException("Membership Group Manager can't revoke itself")
        }

        val memberInfo = membershipGroupCache.getMemberInfo(party) ?: throw MembershipNotFoundException(party, groupId)

        membershipGroupCache.removeMember(memberInfo)
        return sendMemberInfoUpdates(memberInfo, MemberInfoUpdateType.REMOVE) + listOf(syncMembershipGroupCache(party))
    }

    override fun suspendMembership(party: Party): List<FlowHandle<Unit>> {
        authorise()
        if (membershipGroupCache.isManager(party)) {
            throw UnsafeMembershipGroupOperationException("Membership Group Manager can't suspend itself")
        }

        val memberInfo = membershipGroupCache.getMemberInfo(party) ?: throw MembershipNotFoundException(party, groupId)

        return updateMemberInfo(party, memberInfo, memberInfo.copy(status = MemberStatus.SUSPENDED))
    }

    override fun updateMemberProperties(party: Party, properties: Map<String, String>): List<FlowHandle<Unit>> {
        authorise()
        val memberInfo = membershipGroupCache.getMemberInfo(party)?.apply {
            if (status == MemberStatus.PENDING) {
                throw IllegalMembershipStatusException("Member $party should not be in pending status")
            }
        } ?: throw MembershipNotFoundException(party, groupId)

        return updateMemberInfo(party, memberInfo, memberInfo.copy(properties = properties))
    }

    override fun publishMemberInfo(memberInfo: MemberInfo) = appServiceHub.startFlow(PublishMemberInfoFlow(memberInfo))

    override fun syncMembershipGroupCache(party: Party): FlowHandle<Unit> {
        authorise()
        return appServiceHub.startFlow(SyncMembershipGroupCacheFlow(party))
    }

    private fun updateMemberInfo(party: Party, memberInfo: MemberInfo, updatedMemberInfo: MemberInfo): List<FlowHandle<Unit>> {
        membershipGroupCache.addOrUpdateMember(updatedMemberInfo)

        return sendMemberInfoUpdates(updatedMemberInfo, MemberInfoUpdateType.ADD) + if (memberInfo.status == MemberStatus.PENDING) {
            listOf(syncMembershipGroupCache(party))
        } else emptyList()
    }

    private fun sendMemberInfoUpdates(updatedMemberInfo: MemberInfo, updateType: MemberInfoUpdateType): List<FlowHandle<Unit>> {
        val memberInfoUpdates = listOf(MemberInfoUpdate(updatedMemberInfo, updateType))
        return membershipGroupCache.allMembers.filterNot { it.status == MemberStatus.PENDING }.map {
            appServiceHub.startFlow(SendMemberInfoUpdatesFlow(it.party, memberInfoUpdates))
        }
    }

    private fun authorise() {
        if (membershipGroupCache.isManager(appServiceHub.myMemberInfo.party)) {
            throw MembershipAuthorisationException(appServiceHub.myMemberInfo.party, groupId)
        }
    }
}
