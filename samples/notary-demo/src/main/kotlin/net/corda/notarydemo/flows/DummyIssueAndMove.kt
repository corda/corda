/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.notarydemo.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
class DummyIssueAndMove(private val notary: Party, private val counterpartyNode: Party, private val discriminator: Int) : FlowLogic<SignedTransaction>() {
    companion object {
        private const val DO_NOTHING_PROGRAM_ID = "net.corda.notarydemo.flows.DummyIssueAndMove\$DoNothingContract"
    }

    class DoNothingContract : Contract {
        override fun verify(tx: LedgerTransaction) {}
    }

    data class DummyCommand(val dummy: Int = 0) : CommandData

    data class State(override val participants: List<AbstractParty>, val discriminator: Int) : ContractState

    @Suspendable
    override fun call(): SignedTransaction {
        // Self issue an asset
        val state = State(listOf(ourIdentity), discriminator)
        val issueTx = serviceHub.signInitialTransaction(TransactionBuilder(notary).apply {
            addOutputState(state, DO_NOTHING_PROGRAM_ID)
            addCommand(DummyCommand(), listOf(ourIdentity.owningKey))
        })
        serviceHub.recordTransactions(issueTx)
        // Move ownership of the asset to the counterparty
        // We don't check signatures because we know that the notary's signature is missing
        return serviceHub.signInitialTransaction(TransactionBuilder(notary).apply {
            addInputState(issueTx.tx.outRef<ContractState>(0))
            addOutputState(state.copy(participants = listOf(counterpartyNode)), DO_NOTHING_PROGRAM_ID)
            addCommand(DummyCommand(), listOf(ourIdentity.owningKey))
        })
    }
}
