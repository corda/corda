package net.corda.core.node.services

import net.corda.core.identity.Party
import net.corda.core.messaging.FlowHandle
import net.corda.core.node.MemberInfo

interface MembershipGroupService {

    fun requestMembership(): FlowHandle<Unit>

    fun activateMembership(party: Party): List<FlowHandle<Unit>>

    fun revokeMembership(party: Party): List<FlowHandle<Unit>>

    fun suspendMembership(party: Party): List<FlowHandle<Unit>>

    fun updateMemberProperties(party: Party, properties: Map<String, String>): List<FlowHandle<Unit>>

    fun publishMemberInfo(memberInfo: MemberInfo): FlowHandle<Unit>

    fun syncMembershipGroupCache(party: Party): FlowHandle<Unit>
}
