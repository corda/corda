package net.corda.groups.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.node.StatesToRecord
import net.corda.core.utilities.unwrap
import java.security.PublicKey

@InitiatedBy(SendDataToGroup::class)
class ReceiveDataFromGroup(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Receive the group key and transaction.
        val groupKey = otherSession.receive<PublicKey>().unwrap { it }
        val tx = subFlow(ReceiveTransactionFlow(otherSession, true, StatesToRecord.ALL_VISIBLE))

        // Verify the group signature.
        val groupKeyAndSignature = tx.sigs.single { it.by == groupKey }
        groupKeyAndSignature.verify(tx.id)

        // Send to group neighbours.
        subFlow(SendDataToGroup(groupKey, tx))
    }
}