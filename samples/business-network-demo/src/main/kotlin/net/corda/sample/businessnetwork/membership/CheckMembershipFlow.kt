package net.corda.sample.businessnetwork.membership

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap

@CordaSerializable
enum class CheckMembershipResult {
    PASS,
    FAIL
}

@InitiatingFlow
class CheckMembershipFlow(private val otherParty: Party, private val membershipName: CordaX500Name) : FlowLogic<CheckMembershipResult>(), MembershipAware {
    @Suspendable
    override fun call(): CheckMembershipResult {
        otherParty.checkMembership(membershipName, this)
        // This will trigger CounterpartyCheckMembershipFlow
        val untrustworthyData = initiateFlow(otherParty).sendAndReceive<CheckMembershipResult>(membershipName)
        return untrustworthyData.unwrap { it }
    }
}

@InitiatedBy(CheckMembershipFlow::class)
class CounterpartyCheckMembershipFlow(private val otherPartySession: FlowSession) : FlowLogic<Unit>(), MembershipAware {
    @Suspendable
    override fun call() {
        val membershipName = otherPartySession.receive<CordaX500Name>().unwrap { it }
        otherPartySession.counterparty.checkMembership(membershipName, this)
        otherPartySession.send(CheckMembershipResult.PASS)
    }
}