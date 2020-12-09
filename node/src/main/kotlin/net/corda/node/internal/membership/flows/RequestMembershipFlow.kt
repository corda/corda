package net.corda.node.internal.membership.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SignedData
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByService
import net.corda.core.node.MemberInfo
import net.corda.core.node.MemberRole
import net.corda.core.node.MemberStatus
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import net.corda.node.internal.membership.sign
import net.corda.node.services.network.DuplicateMembershipException

@CordaSerializable
data class MembershipRequest(val myInfo: MemberInfo)

@InitiatingFlow
@StartableByService
class RequestMembershipFlow : MembershipGroupManagementFlow() {

    @Suspendable
    override fun call() {
        if (membershipGroupCache.getMemberInfo(ourIdentity) != null) {
            throw DuplicateMembershipException(ourIdentity, groupId)
        }

        val session = initiateFlow(serviceHub.membershipGroupCache.managerInfo.party)
        session.send(MembershipRequest(serviceHub.myMemberInfo).sign(
                keyManagementService = serviceHub.keyManagementService,
                key = ourIdentity.owningKey
        ))

        val memberInfo = session.receive<SignedData<MemberInfo>>().unwrap { it.verified() }
        membershipGroupCache.addOrUpdateMember(memberInfo)
    }
}

@InitiatedBy(RequestMembershipFlow::class)
class RequestMembershipResponderFlow(private val session: FlowSession) : MembershipGroupManagementFlow() {

    @Suspendable
    override fun call() {
        authorise()

        val myInfo = session.receive<SignedData<MembershipRequest>>().unwrap { it }.verified().myInfo

        val memberInfo = myInfo.copy(status = MemberStatus.PENDING, role = MemberRole.NODE)
        membershipGroupCache.addOrUpdateMember(memberInfo)

        val signedMemberInfo = memberInfo.sign(
                keyManagementService = serviceHub.keyManagementService,
                key = ourIdentity.owningKey
        )
        session.send(signedMemberInfo)
    }
}
