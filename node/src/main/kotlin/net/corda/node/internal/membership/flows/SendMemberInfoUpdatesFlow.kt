package net.corda.node.internal.membership.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SignedData
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import net.corda.core.node.MemberInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import net.corda.node.internal.membership.sign

@CordaSerializable
enum class MemberInfoUpdateType { ADD, REMOVE }

@CordaSerializable
data class MemberInfoUpdate(val memberInfo: MemberInfo, val type: MemberInfoUpdateType)

@InitiatingFlow
@StartableByService
class SendMemberInfoUpdatesFlow(private val member: Party, private val memberInfoUpdates: List<MemberInfoUpdate>) : MembershipGroupManagementFlow() {

    @Suspendable
    override fun call() {
        authorise()

        val signedMemberInfoUpdates = memberInfoUpdates.map {
            it.sign(
                    keyManagementService = serviceHub.keyManagementService,
                    key = ourIdentity.owningKey
            )
        }

        val session = initiateFlow(member)
        session.sendAndReceive<Unit>(signedMemberInfoUpdates)
    }
}

@InitiatedBy(SendMemberInfoUpdatesFlow::class)
class SendMemberInfoUpdatesResponderFlow(private val session: FlowSession) : MembershipGroupManagementFlow() {

    @Suspendable
    override fun call() {
        val memberInfoUpdates = session.receive<List<SignedData<MemberInfoUpdate>>>()
                .unwrap { it }
                .map { it.verified() }

        membershipGroupCache.addOrUpdateMembers(memberInfoUpdates.filter { it.type == MemberInfoUpdateType.ADD }.map { it.memberInfo })
        memberInfoUpdates.filter { it.type == MemberInfoUpdateType.REMOVE }.forEach {
            membershipGroupCache.removeMember(it.memberInfo)
        }

        session.send(Unit)
    }
}
