package net.corda.docs.tutorial.twoparty

import net.corda.core.contracts.ContractState
import net.corda.core.identity.Party

class IOUState(val value: Int,
               val lender: Party,
               val borrower: Party) : ContractState {
    override val participants get() = listOf(lender, borrower)
}