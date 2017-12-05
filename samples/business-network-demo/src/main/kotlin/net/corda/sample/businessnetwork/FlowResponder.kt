package net.corda.sample.businessnetwork

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.sample.businessnetwork.membership.MembershipAware

@InitiatedBy(IOUFlow::class)
class IOUFlowResponder(val otherPartySession: FlowSession) : FlowLogic<Unit>(), MembershipAware {
    @Suspendable
    override fun call() {

        otherPartySession.counterparty.checkMembership(IOUFlow.allowedMembershipName, this)

        subFlow(object : SignTransactionFlow(otherPartySession, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction." using (output is IOUState)
                val iou = output as IOUState
                "The IOU's value can't be too high." using (iou.value < 100)
            }
        })
    }
}