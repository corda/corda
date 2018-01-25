package net.corda.notarytest.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.NotaryFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
open class HealthCheckFlow(val notaryParty: Party) : FlowLogic<Unit>() {

    class DoNothingContract : Contract {
        override fun verify(tx: LedgerTransaction) {}
    }

    data class DummyCommand(val dummy: Int = 0) : CommandData

    @Suspendable
    override fun call(): Unit {

        data class State(override val participants: List<AbstractParty>) : ContractState

        val state = State(listOf(ourIdentity))

         val stx = serviceHub.signInitialTransaction(TransactionBuilder(notaryParty).apply {
            addOutputState(state, "net.corda.notarytest.flows.HealthCheckFlow\$DoNothingContract")
            addCommand(DummyCommand(), listOf(ourIdentity.owningKey))
        })

        subFlow(NotaryFlow.Client(stx))
    }
}