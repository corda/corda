package net.corda.flows.djvm

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.djvm.NonDeterministicContract
import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByRPC
class NonDeterministicFlow(private val otherSide: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val stx = serviceHub.signInitialTransaction(
                TransactionBuilder(notary)
                        .addOutputState(NonDeterministicContract.State())
                        .addCommand(Command(NonDeterministicContract.Cmd, ourIdentity.owningKey))
        )
        stx.verify(serviceHub, checkSufficientSignatures = false)
        val session = initiateFlow(otherSide)
        subFlow(FinalityFlow(stx, session))
        // It's important we wait on this dummy receive, as otherwise it's possible we miss any errors the other side throws
        session.receive<String>().unwrap { require(it == "OK") { "Not OK: $it"} }
    }
}
