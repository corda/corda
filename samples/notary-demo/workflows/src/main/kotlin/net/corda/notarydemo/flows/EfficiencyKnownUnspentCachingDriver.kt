package net.corda.notarydemo.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.notarydemo.contracts.DO_NOTHING_PROGRAM_ID
import net.corda.notarydemo.contracts.DummyCommand
import net.corda.notarydemo.contracts.DummyState


@StartableByRPC
class EfficiencyKnownUnspentCachingDriver(val stx: SignedTransaction, val notary: Party) : FlowLogic<SignedTransaction>() {
    /**
     * Basic driver to test the efficiency of known unspent states cache
     * Intended to use with DummyIssueAndMove - essentially this uses the output states
     * of DummyIssueAndMove because they are known unspent states and thus should use the "fastpath"
     * afforded by the UnspentStatesCache.
     * Default number of input states would be 1000 states as that is how many DummyIssueAndMove will generate
     * The intent is to create a scenario
     */
    @Suspendable
    override fun call(): SignedTransaction {
        val state = DummyState(listOf(ourIdentity), 100_000)

        return serviceHub.signInitialTransaction(TransactionBuilder(notary).apply {
            for (s in stx.coreTransaction.outRefsOfType<ContractState>()) {
                addInputState(s)
            }
            addOutputState(state, DO_NOTHING_PROGRAM_ID)
            addCommand(DummyCommand(), listOf(ourIdentity.owningKey))
        })
    }
}