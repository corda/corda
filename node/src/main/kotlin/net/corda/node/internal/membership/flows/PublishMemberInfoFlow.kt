package net.corda.node.internal.membership.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SignedData
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByService
import net.corda.core.node.MemberInfo
import net.corda.core.utilities.unwrap
import net.corda.node.internal.membership.sign
import net.corda.node.services.network.MembershipNotFoundException

@InitiatingFlow
@StartableByService
class PublishMemberInfoFlow(private val memberInfo: MemberInfo) : MembershipGroupManagementFlow() {

    @Suspendable
    override fun call() {
        if (memberInfo.party != ourIdentity) {
            throw FlowException("Initiator can only publish its own membership info")
        }

        if (membershipGroupCache.getMemberInfo(ourIdentity) == null) {
            throw MembershipNotFoundException(ourIdentity, groupId)
        }

        val session = initiateFlow(membershipGroupCache.managerInfo.party)
        session.sendAndReceive<Unit>(memberInfo.sign(serviceHub.keyManagementService, ourIdentity.owningKey))
    }
}

@InitiatedBy(PublishMemberInfoFlow::class)
class PublishMemberInfoResponderFlow(private val session: FlowSession) : MembershipGroupManagementFlow() {

    @Suspendable
    override fun call() {
        authorise()

        val memberInfo = session.receive<SignedData<MemberInfo>>().unwrap { it.verified() }

        membershipGroupCache.addOrUpdateMember(memberInfo)

        session.send(Unit)
    }
}