package net.corda.contracts.clause

import net.corda.contracts.asset.OnLedgerAsset
import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.Clause
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.loggerFor
import java.security.PublicKey

/**
 * Standardised clause for checking input/output balances of fungible assets. Requires that a
 * Move command is provided, and errors if absent. Must be the last clause under a grouping clause;
 * errors on no-match, ends on match.
 */
abstract class AbstractConserveAmount<S : FungibleAsset<T>, C : CommandData, T : Any> : Clause<S, C, Issued<T>>() {

    private companion object {
        val log = loggerFor<AbstractConserveAmount<*, *, *>>()
    }

    /**
     * Generate an transaction exiting fungible assets from the ledger.
     *
     * @param tx transaction builder to add states and commands to.
     * @param amountIssued the amount to be exited, represented as a quantity of issued currency.
     * @param assetStates the asset states to take funds from. No checks are done about ownership of these states, it is
     * the responsibility of the caller to check that they do not attempt to exit funds held by others.
     * @return the public keys who must sign the transaction for it to be valid.
     */
    @Deprecated("This function will be removed in a future milestone", ReplaceWith("OnLedgerAsset.generateExit()"))
    @Throws(InsufficientBalanceException::class)
    fun generateExit(tx: TransactionBuilder, amountIssued: Amount<Issued<T>>,
                     assetStates: List<StateAndRef<S>>,
                     deriveState: (TransactionState<S>, Amount<Issued<T>>, AbstractParty) -> TransactionState<S>,
                     generateMoveCommand: () -> CommandData,
                     generateExitCommand: (Amount<Issued<T>>) -> CommandData): Set<PublicKey>
    = OnLedgerAsset.generateExit(tx, amountIssued, assetStates, deriveState, generateMoveCommand, generateExitCommand)

    override fun verify(tx: TransactionForContract,
                        inputs: List<S>,
                        outputs: List<S>,
                        commands: List<AuthenticatedObject<C>>,
                        groupingKey: Issued<T>?): Set<C> {
        require(groupingKey != null) { "Conserve amount clause can only be used on grouped states" }
        val matchedCommands = commands.filter { command -> command.value is FungibleAsset.Commands.Move || command.value is FungibleAsset.Commands.Exit<*> }
        val inputAmount: Amount<Issued<T>> = inputs.sumFungibleOrNull<T>() ?: throw IllegalArgumentException("there is at least one asset input for group $groupingKey")
        val deposit = groupingKey!!.issuer
        val outputAmount: Amount<Issued<T>> = outputs.sumFungibleOrZero(groupingKey)

        // If we want to remove assets from the ledger, that must be signed for by the issuer and owner.
        val exitKeys: Set<PublicKey> = inputs.flatMap { it.exitKeys }.toSet()
        val exitCommand = matchedCommands.select<FungibleAsset.Commands.Exit<T>>(parties = null, signers = exitKeys).filter { it.value.amount.token == groupingKey }.singleOrNull()
        val amountExitingLedger: Amount<Issued<T>> = exitCommand?.value?.amount ?: Amount(0, groupingKey)

        requireThat {
            "there are no zero sized inputs" using inputs.none { it.amount.quantity == 0L }
            "for reference ${deposit.reference} at issuer ${deposit.party} the amounts balance: ${inputAmount.quantity} - ${amountExitingLedger.quantity} != ${outputAmount.quantity}" using
                    (inputAmount == outputAmount + amountExitingLedger)
        }

        verifyMoveCommand<FungibleAsset.Commands.Move>(inputs, commands)

        // This is safe because we've taken the commands from a collection of C objects at the start
        @Suppress("UNCHECKED_CAST")
        return matchedCommands.map { it.value }.toSet()
    }

    override fun toString(): String = "Conserve amount between inputs and outputs"
}
