package net.corda.node.services.network

import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.MemberInfo
import net.corda.core.node.services.MembershipGroupCache
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.internal.schemas.MemberInfoSchemaV1.PersistentMemberInfo
import net.corda.node.services.api.MembershipGroupCacheInternal
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.bufferUntilDatabaseCommit
import net.corda.nodeapi.internal.persistence.wrapWithDatabaseTransaction
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey

class PersistentMembershipGroupCache(
        private val database: CordaPersistence,
        override val managerInfo: MemberInfo
) : MembershipGroupCacheInternal, SingletonSerializeAsToken() {

    private val _changed = PublishSubject.create<MembershipGroupCache.GroupChange>()
    override val changed: Observable<MembershipGroupCache.GroupChange> = _changed.wrapWithDatabaseTransaction()
    private val changePublisher: rx.Observer<MembershipGroupCache.GroupChange> get() = _changed.bufferUntilDatabaseCommit()

    override val allMembers: List<MemberInfo> get() = queryAllPersistentInfo().map { it.toMemberInfo() }

    override fun addOrUpdateMember(memberInfo: MemberInfo) {
        synchronized(_changed) {
            database.transaction {
                val oldMemberInfo = queryPersistentInfoById(memberInfo.memberId)?.toMemberInfo()
                val persistentMemberInfo = PersistentMemberInfo.fromMemberInfo(memberInfo)
                session.merge(persistentMemberInfo)

                val change = oldMemberInfo?.let {
                    MembershipGroupCache.GroupChange.Modified(memberInfo, oldMemberInfo)
                } ?: MembershipGroupCache.GroupChange.Added(memberInfo)
                changePublisher.onNext(change)
            }
        }
    }

    override fun addOrUpdateMembers(memberInfoList: List<MemberInfo>) {
        memberInfoList.forEach {
            addOrUpdateMember(it)
        }
    }

    override fun removeMember(memberInfo: MemberInfo) {
        synchronized(_changed) {
            database.transaction {
                val dbEntry = queryPersistentInfoById(memberInfo.memberId)
                dbEntry?.let {
                    session.remove(it)
                    changePublisher.onNext(MembershipGroupCache.GroupChange.Removed(it.toMemberInfo()))
                }
            }
        }
    }

    /* TODO[MGM]: Should be optimized - no need to query the whole MemberInfo structure */
    override fun getPartyByName(name: CordaX500Name): Party? {
        return queryPersistentInfoByName(name)?.toMemberInfo()?.party
    }

    /* TODO[MGM]: Should use lookup by key table instead */
    override fun getPartyByKey(key: PublicKey): Party? {
        return queryPersistentInfoById(key.toStringShort())?.toMemberInfo()?.party
    }

    /* TODO[MGM]: Should use lookup by key table instead */
    override fun getMemberInfo(party: AbstractParty): MemberInfo? {
        if (party is Party) {
            val memberInfo = queryPersistentInfoByName(party.name)?.toMemberInfo()
            if (memberInfo != null && party.owningKey in memberInfo.keys) {
                return memberInfo
            }
            return null
        } else {
            return queryPersistentInfoById(party.owningKey.toStringShort())?.toMemberInfo()
        }
    }

    override fun getMemberInfo(memberId: String): MemberInfo? {
        return queryPersistentInfoById(memberId)?.toMemberInfo()
    }

    private fun queryPersistentInfoById(memberId: String): PersistentMemberInfo? = database.transaction {
        session.createQuery(
                "SELECT m FROM ${PersistentMemberInfo::class.java.name} m WHERE member_id = :memberId",
                PersistentMemberInfo::class.java
        ).setParameter("memberId", memberId)
                .resultList
                .singleOrNull()
    }

    private fun queryPersistentInfoByName(name: CordaX500Name): PersistentMemberInfo? = database.transaction {
        session.createQuery(
                "SELECT m FROM ${PersistentMemberInfo::class.java.name} m WHERE party_name = :name",
                PersistentMemberInfo::class.java
        ).setParameter("name", name.toString())
                .resultList
                .singleOrNull()
    }

    private fun queryAllPersistentInfo(): List<PersistentMemberInfo> = database.transaction {
        session.createQuery(
                "SELECT m FROM ${PersistentMemberInfo::class.java.name} m",
                PersistentMemberInfo::class.java
        ).resultList
    }
}
