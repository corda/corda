package com.r3corda.node.services.wallet

import com.r3corda.contracts.cash.Cash
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.contracts.sumOrThrow
import com.r3corda.core.node.services.Wallet
import java.util.*

/**
 * A wallet (name may be temporary) wraps a set of states that are useful for us to keep track of, for instance,
 * because we own them. This class represents an immutable, stable state of a wallet: it is guaranteed not to
 * change out from underneath you, even though the canonical currently-best-known wallet may change as we learn
 * about new transactions from our peers and generate new transactions that consume states ourselves.
 *
 * This concrete implementation references Cash contracts.
 */
class WalletImpl(override val states: List<StateAndRef<ContractState>>) : Wallet() {

    /**
     * Returns a map of how much cash we have in each currency, ignoring details like issuer. Note: currencies for
     * which we have no cash evaluate to null (not present in map), not 0.
     */
    override val cashBalances: Map<Currency, Amount> get() = states.
            // Select the states we own which are cash, ignore the rest, take the amounts.
            mapNotNull { (it.state as? Cash.State)?.amount }.
            // Turn into a Map<Currency, List<Amount>> like { GBP -> (£100, £500, etc), USD -> ($2000, $50) }
            groupBy { it.currency }.
            // Collapse to Map<Currency, Amount> by summing all the amounts of the same currency together.
            mapValues { it.value.sumOrThrow() }
}