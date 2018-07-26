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
        val counterparty = otherSession.counterparty
        val groupKey = otherSession.receive<PublicKey>().unwrap { it }
        // TODO: Re-write to use custom logic as need to check tx should be in group before storing.
        // The SendTransactionFlow and ReceiveTransactionFlow will do for the time being though. However, we might end
        // up in a situation where a transaction has been added and propagated to the group but it doesn't have the
        // required signature from a read/write member of the group.
        val tx = subFlow(ReceiveTransactionFlow(otherSession, true, StatesToRecord.ALL_VISIBLE))

        logger.info("Received transaction ${tx.id} from party $counterparty in group $groupKey.")

        // Extract the relevant signature and verify it.
        // TODO: This check should occur BEFORE storing the transaction and its dependencies.
        val groupKeyAndSignature = tx.sigs.single { it.by == groupKey }
        groupKeyAndSignature.verify(tx.id)

        // Send to group our neighbours (if there are any).
        subFlow(SendDataToGroup(groupKey, tx, otherSession.counterparty))
    }
}