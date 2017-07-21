package net.corda.contracts.clause

import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.Clause
import net.corda.core.transactions.LedgerTransaction

/**
 * Clause for fungible asset contracts, which enforces that no output state should have
 * a balance of zero.
 */
open class NoZeroSizedOutputs<in S : FungibleAsset<T>, C : CommandData, T : Any> : Clause<S, C, Issued<T>>() {
    override fun verify(tx: LedgerTransaction,
                        inputs: List<S>,
                        outputs: List<S>,
                        commands: List<AuthenticatedObject<C>>,
                        groupingKey: Issued<T>?): Set<C> {
        requireThat {
            "there are no zero sized outputs" using outputs.none { it.amount.quantity == 0L }
        }
        return emptySet()
    }

    override fun toString(): String = "No zero sized outputs"
}
