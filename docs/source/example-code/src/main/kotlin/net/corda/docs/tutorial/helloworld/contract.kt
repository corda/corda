package net.corda.docs.tutorial.helloworld

// DOCSTART 01
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class IOUContract : Contract {
    // Our Create command.
    class Create : CommandData

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Create>()

        requireThat {
            // Constraints on the shape of the transaction.
            "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
            "There should be one output state of type IOUState." using (tx.outputs.size == 1)

            // IOU-specific constraints.
            val out = tx.outputsOfType<IOUState>().single()
            "The IOU's value must be non-negative." using (out.value > 0)
            "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)

            // Constraints on the signers.
            "There must only be one signer." using (command.signers.toSet().size == 1)
            "The signer must be the lender." using (command.signers.contains(out.lender.owningKey))
        }
    }
}
// DOCEND 01