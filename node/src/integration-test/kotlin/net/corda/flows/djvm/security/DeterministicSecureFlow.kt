package net.corda.flows.djvm.security

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.djvm.security.DeterministicSecureContract.Operate
import net.corda.contracts.djvm.security.DeterministicSecureContract.State
import net.corda.core.contracts.Command
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
class DeterministicSecureFlow(private val data: SecureHash) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val stx = serviceHub.signInitialTransaction(
            TransactionBuilder(notary)
                .addOutputState(State(ourIdentity, data))
                .addCommand(Command(Operate(), ourIdentity.owningKey))
        )
        stx.verify(serviceHub, checkSufficientSignatures = false)
        return stx
    }
}