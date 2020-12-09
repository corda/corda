package net.corda.node.internal.membership.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SignedData
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import net.corda.core.node.MemberInfo
import net.corda.core.node.MemberStatus
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import net.corda.node.internal.membership.sign
import net.corda.node.services.network.IllegalMembershipStatusException

@CordaSerializable
data class MemberInfoSyncData(val updatedMembers: List<MemberInfo>)

@InitiatingFlow
@StartableByService
class SyncMembershipGroupCacheFlow(private val party: Party) : MembershipGroupManagementFlow() {

    @Suspendable
    override fun call() {
        authorise()

        val allMembers = getAllMembers()
        val session = initiateFlow(party)
        val signedMemberInfos = MemberInfoSyncData(allMembers).sign(
                keyManagementService = serviceHub.keyManagementService,
                key = ourIdentity.owningKey
        )

        session.sendAndReceive<Unit>(signedMemberInfos)
    }

    @Suspendable
    private fun getAllMembers(): List<MemberInfo> {
        when (membershipGroupCache.getMemberInfo(party)?.status) {
            MemberStatus.PENDING ->
                throw IllegalMembershipStatusException("$party cannot sync membership group data since it is in pending status")
            null -> logger.warn("$party is not member of the Membership Group ID $groupId")
            else -> {}
        }
        return membershipGroupCache.allMembers.filterNot { it.status == MemberStatus.PENDING }
    }
}

@InitiatedBy(SyncMembershipGroupCacheFlow::class)
class SyncMembershipGroupCacheResponderFlow(private val session: FlowSession) : MembershipGroupManagementFlow() {

    @Suspendable
    override fun call() {
        val updatedMembers = session.receive<SignedData<MemberInfoSyncData>>().unwrap { it.verified() }.updatedMembers

        val removedMembers = membershipGroupCache.allMembers.filter { memberInfo ->
            updatedMembers.find { it.party == memberInfo.party } == null
        }

        membershipGroupCache.addOrUpdateMembers(updatedMembers)
        removedMembers.forEach { membershipGroupCache.removeMember(it) }

        session.send(Unit)
    }
}