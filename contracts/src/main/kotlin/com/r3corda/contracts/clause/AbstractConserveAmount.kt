package com.r3corda.contracts.clause

import com.r3corda.contracts.asset.FungibleAsset
import com.r3corda.contracts.asset.sumFungibleOrNull
import com.r3corda.contracts.asset.sumFungibleOrZero
import com.r3corda.core.contracts.*
import com.r3corda.core.contracts.clauses.GroupClause
import com.r3corda.core.contracts.clauses.MatchBehaviour
import java.security.PublicKey

/**
 * Standardised clause for checking input/output balances of fungible assets. Requires that a
 * Move command is provided, and errors if absent. Must be the last clause under a grouping clause;
 * errors on no-match, ends on match.
 */
abstract class AbstractConserveAmount<S: FungibleAsset<T>, T: Any> : GroupClause<S, Issued<T>> {
    override val ifMatched: MatchBehaviour
        get() = MatchBehaviour.END
    override val ifNotMatched: MatchBehaviour
        get() = MatchBehaviour.ERROR
    override val requiredCommands: Set<Class<out CommandData>>
        get() = emptySet()

    override fun verify(tx: TransactionForContract,
                        inputs: List<S>,
                        outputs: List<S>,
                        commands: Collection<AuthenticatedObject<CommandData>>,
                        token: Issued<T>): Set<CommandData> {
        val inputAmount: Amount<Issued<T>> = inputs.sumFungibleOrNull<T>() ?: throw IllegalArgumentException("there is at least one asset input for group ${token}")
        val deposit = token.issuer
        val outputAmount: Amount<Issued<T>> = outputs.sumFungibleOrZero(token)

        // If we want to remove assets from the ledger, that must be signed for by the issuer.
        // A mis-signed or duplicated exit command will just be ignored here and result in the exit amount being zero.
        val exitKeys: Set<PublicKey> = inputs.flatMap { it.exitKeys }.toSet()
        val exitCommand = tx.commands.select<FungibleAsset.Commands.Exit<T>>(parties = null, signers = exitKeys).filter {it.value.amount.token == token}.singleOrNull()
        val amountExitingLedger: Amount<Issued<T>> = exitCommand?.value?.amount ?: Amount(0, token)

        requireThat {
            "there are no zero sized inputs" by inputs.none { it.amount.quantity == 0L }
            "for reference ${deposit.reference} at issuer ${deposit.party.name} the amounts balance" by
                    (inputAmount == outputAmount + amountExitingLedger)
        }

        return listOf(exitCommand?.value, verifyMoveCommand<FungibleAsset.Commands.Move>(inputs, tx))
                .filter { it != null }
                .requireNoNulls().toSet()
    }
}
