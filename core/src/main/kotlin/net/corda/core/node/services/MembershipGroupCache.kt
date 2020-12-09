package net.corda.core.node.services

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.MemberInfo
import net.corda.core.serialization.CordaSerializable
import rx.Observable
import java.security.PublicKey

interface MembershipGroupCache {
    @CordaSerializable
    sealed class GroupChange {
        abstract val memberInfo: MemberInfo

        data class Added(override val memberInfo: MemberInfo) : GroupChange()
        data class Removed(override val memberInfo: MemberInfo) : GroupChange()
        data class Modified(override val memberInfo: MemberInfo, val previousInfo: MemberInfo) : GroupChange()
    }

    val changed: Observable<GroupChange>

    val managerInfo: MemberInfo

    val allMembers: List<MemberInfo>

    fun getPartyByName(name: CordaX500Name): Party?

    fun getPartyByKey(key: PublicKey): Party?

    fun getMemberInfo(party: AbstractParty): MemberInfo?

    fun getMemberInfo(memberId: String): MemberInfo?
}
