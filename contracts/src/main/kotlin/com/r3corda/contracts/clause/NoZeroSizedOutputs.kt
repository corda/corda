package com.r3corda.contracts.clause

import com.r3corda.contracts.asset.FungibleAsset
import com.r3corda.core.contracts.*
import com.r3corda.core.contracts.clauses.GroupClause
import com.r3corda.core.contracts.clauses.MatchBehaviour

/**
 * Clause for fungible asset contracts, which enforces that no output state should have
 * a balance of zero.
 */
open class NoZeroSizedOutputs<in S: FungibleAsset<T>, T: Any> : GroupClause<S, Issued<T>> {
    override val ifMatched: MatchBehaviour
        get() = MatchBehaviour.CONTINUE
    override val ifNotMatched: MatchBehaviour
        get() = MatchBehaviour.ERROR
    override val requiredCommands: Set<Class<CommandData>>
        get() = emptySet()

    override fun verify(tx: TransactionForContract,
                        inputs: List<S>,
                        outputs: List<S>,
                        commands: Collection<AuthenticatedObject<CommandData>>,
                        token: Issued<T>): Set<CommandData> {
        requireThat {
            "there are no zero sized outputs" by outputs.none { it.amount.quantity == 0L }
        }
        return emptySet()
    }
}
