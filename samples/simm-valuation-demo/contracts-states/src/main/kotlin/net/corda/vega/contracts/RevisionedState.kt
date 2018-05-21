package net.corda.vega.contracts

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder

/**
 * Deals implementing this interface will be usable with the StateRevisionFlow that allows arbitrary updates
 * to a state. This is not really an "amendable" state (recall the Corda model - all states are immutable in the
 * functional sense) however it can be amended and then re-written as another state into the ledger.
 */
interface RevisionedState<in T> : ContractState {
    fun generateRevision(notary: Party, oldState: StateAndRef<*>, updatedValue: T): TransactionBuilder
}
