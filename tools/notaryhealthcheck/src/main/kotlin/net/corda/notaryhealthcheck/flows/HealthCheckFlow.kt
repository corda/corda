/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.notaryhealthcheck.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
open class HealthCheckFlow(val notaryParty: Party, val checkEntireCluster: Boolean = false) : FlowLogic<Unit>() {
    class DoNothingContract : Contract {
        override fun verify(tx: LedgerTransaction) {}
    }

    data class DummyCommand(val dummy: Int = 0) : CommandData
    data class State(override val participants: List<AbstractParty>) : ContractState

    @Suspendable
    override fun call() {
        val state = State(listOf(ourIdentity))
        val stx = serviceHub.signInitialTransaction(TransactionBuilder(notaryParty).apply {
            addOutputState(state, "net.corda.notaryhealthcheck.flows.HealthCheckFlow\$DoNothingContract")
            addCommand(DummyCommand(), listOf(ourIdentity.owningKey))
        })
        subFlow(HealthCheckNotaryClientFlow(stx, checkEntireCluster = checkEntireCluster))
    }
}