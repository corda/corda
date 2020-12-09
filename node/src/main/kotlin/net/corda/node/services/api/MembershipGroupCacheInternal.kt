package net.corda.node.services.api

import net.corda.core.identity.Party
import net.corda.core.node.MemberInfo
import net.corda.core.node.services.MembershipGroupCache

interface MembershipGroupCacheInternal : MembershipGroupCache {

    fun addOrUpdateMember(memberInfo: MemberInfo)

    fun addOrUpdateMembers(memberInfoList: List<MemberInfo>)

    fun removeMember(memberInfo: MemberInfo)

    fun isManager(party: Party): Boolean {
        return managerInfo.party == party
    }
}