package net.corda.contracts.clause

import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.Clause
import net.corda.core.crypto.CompositeKey
import net.corda.core.transactions.TransactionBuilder
import java.util.*

/**
 * Standardised clause for checking input/output balances of fungible assets. Requires that a
 * Move command is provided, and errors if absent. Must be the last clause under a grouping clause;
 * errors on no-match, ends on match.
 */
abstract class AbstractConserveAmount<S : FungibleAsset<T>, C : CommandData, T : Any> : Clause<S, C, Issued<T>>() {
    /**
     * Gather assets from the given list of states, sufficient to match or exceed the given amount.
     *
     * @param acceptableCoins list of states to use as inputs.
     * @param amount the amount to gather states up to.
     * @throws InsufficientBalanceException if there isn't enough value in the states to cover the requested amount.
     */
    @Throws(InsufficientBalanceException::class)
    private fun gatherCoins(acceptableCoins: Collection<StateAndRef<S>>,
                            amount: Amount<T>): Pair<ArrayList<StateAndRef<S>>, Amount<T>> {
        val gathered = arrayListOf<StateAndRef<S>>()
        var gatheredAmount = Amount(0, amount.token)
        for (c in acceptableCoins) {
            if (gatheredAmount >= amount) break
            gathered.add(c)
            gatheredAmount += Amount(c.state.data.amount.quantity, amount.token)
        }

        if (gatheredAmount < amount)
            throw InsufficientBalanceException(amount - gatheredAmount)

        return Pair(gathered, gatheredAmount)
    }

    /**
     * Generate an transaction exiting fungible assets from the ledger.
     *
     * @param tx transaction builder to add states and commands to.
     * @param amountIssued the amount to be exited, represented as a quantity of issued currency.
     * @param assetStates the asset states to take funds from. No checks are done about ownership of these states, it is
     * the responsibility of the caller to check that they do not attempt to exit funds held by others.
     * @return the public key of the assets issuer, who must sign the transaction for it to be valid.
     */
    @Throws(InsufficientBalanceException::class)
    fun generateExit(tx: TransactionBuilder, amountIssued: Amount<Issued<T>>,
                     assetStates: List<StateAndRef<S>>,
                     deriveState: (TransactionState<S>, Amount<Issued<T>>, CompositeKey) -> TransactionState<S>,
                     generateMoveCommand: () -> CommandData,
                     generateExitCommand: (Amount<Issued<T>>) -> CommandData): CompositeKey {
        val owner = assetStates.map { it.state.data.owner }.toSet().singleOrNull() ?: throw InsufficientBalanceException(amountIssued)
        val currency = amountIssued.token.product
        val amount = Amount(amountIssued.quantity, currency)
        var acceptableCoins = assetStates.filter { ref -> ref.state.data.amount.token == amountIssued.token }
        tx.notary = acceptableCoins.firstOrNull()?.state?.notary
        // TODO: We should be prepared to produce multiple transactions exiting inputs from
        // different notaries, or at least group states by notary and take the set with the
        // highest total value
        acceptableCoins = acceptableCoins.filter { it.state.notary == tx.notary }

        val (gathered, gatheredAmount) = gatherCoins(acceptableCoins, Amount(amount.quantity, currency))
        val takeChangeFrom = gathered.lastOrNull()
        val change = if (takeChangeFrom != null && gatheredAmount > amount) {
            Amount(gatheredAmount.quantity - amount.quantity, takeChangeFrom.state.data.amount.token)
        } else {
            null
        }

        val outputs = if (change != null) {
            // Add a change output and adjust the last output downwards.
            listOf(deriveState(gathered.last().state, change, owner))
        } else emptyList()

        for (state in gathered) tx.addInputState(state)
        for (state in outputs) tx.addOutputState(state)
        tx.addCommand(generateMoveCommand(), gathered.map { it.state.data.owner })
        tx.addCommand(generateExitCommand(amountIssued), gathered.flatMap { it.state.data.exitKeys })
        return amountIssued.token.issuer.party.owningKey
    }

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
        val exitKeys: Set<CompositeKey> = inputs.flatMap { it.exitKeys }.toSet()
        val exitCommand = matchedCommands.select<FungibleAsset.Commands.Exit<T>>(parties = null, signers = exitKeys).filter { it.value.amount.token == groupingKey }.singleOrNull()
        val amountExitingLedger: Amount<Issued<T>> = exitCommand?.value?.amount ?: Amount(0, groupingKey)

        requireThat {
            "there are no zero sized inputs" by inputs.none { it.amount.quantity == 0L }
            "for reference ${deposit.reference} at issuer ${deposit.party.name} the amounts balance: ${inputAmount.quantity} - ${amountExitingLedger.quantity} != ${outputAmount.quantity}" by
                    (inputAmount == outputAmount + amountExitingLedger)
        }

        verifyMoveCommand<FungibleAsset.Commands.Move>(inputs, commands)

        // This is safe because we've taken the commands from a collection of C objects at the start
        @Suppress("UNCHECKED_CAST")
        return matchedCommands.map { it.value }.toSet()
    }

    override fun toString(): String = "Conserve amount between inputs and outputs"
}
