@file:Suppress("MemberVisibilityCanBePrivate")

package net.corda.docs.kotlin.tutorial.helloworld

import net.corda.core.contracts.ContractState

// DOCSTART 01
// Add this import:
import net.corda.core.identity.Party

// Replace TemplateState's definition with:
class IOUState(val value: Int,
               val lender: Party,
               val borrower: Party) : ContractState {
    override val participants get() = listOf(lender, borrower)
}
// DOCEND 01
