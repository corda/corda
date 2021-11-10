package net.corda.flows.mutator

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.mutator.MutatorContract.MutateCommand
import net.corda.contracts.mutator.MutatorContract.MutateState
import net.corda.core.contracts.Command
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
class MutatorFlow : FlowLogic<SecureHash>() {
    @Suspendable
    override fun call(): SecureHash {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val stx = serviceHub.signInitialTransaction(
            TransactionBuilder(notary)
                // Create some content for the LedgerTransaction.
                .addOutputState(MutateState(ourIdentity))
                .addCommand(Command(MutateCommand(), ourIdentity.owningKey))
        )
        stx.verify(serviceHub, checkSufficientSignatures = false)
        return stx.id
    }
}
