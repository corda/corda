package com.r3corda.contracts.clause

import com.r3corda.contracts.asset.FungibleAsset
import com.r3corda.contracts.asset.InsufficientBalanceException
import com.r3corda.contracts.asset.sumFungibleOrNull
import com.r3corda.contracts.asset.sumFungibleOrZero
import com.r3corda.core.contracts.*
import com.r3corda.core.contracts.clauses.GroupClause
import com.r3corda.core.contracts.clauses.MatchBehaviour
import com.r3corda.core.crypto.Party
import java.security.PublicKey
import java.util.*

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
     * @param changeKey the key to send any change to. This needs to be explicitly stated as the input states are not
     * necessarily owned by us.
     * @param assetStates the asset states to take funds from. No checks are done about ownership of these states, it is
     * the responsibility of the caller to check that they do not exit funds held by others.
     * @return the public key of the assets issuer, who must sign the transaction for it to be valid.
     */
    fun generateExit(tx: TransactionBuilder, amountIssued: Amount<Issued<T>>,
                     changeKey: PublicKey, assetStates: List<StateAndRef<S>>,
                     deriveState: (TransactionState<S>, Amount<Issued<T>>, PublicKey) -> TransactionState<S>,
                     generateExitCommand: (Amount<Issued<T>>) -> CommandData): PublicKey {
        val currency = amountIssued.token.product
        val amount = Amount(amountIssued.quantity, currency)
        var acceptableCoins = assetStates.filter { ref -> ref.state.data.amount.token == amountIssued.token }
        val notary = acceptableCoins.firstOrNull()?.state?.notary
        // TODO: We should be prepared to produce multiple transactions exiting inputs from
        // different notaries, or at least group states by notary and take the set with the
        // highest total value
        acceptableCoins = acceptableCoins.filter { it.state.notary == notary }

        val (gathered, gatheredAmount) = gatherCoins(acceptableCoins, Amount(amount.quantity, currency))
        val takeChangeFrom = gathered.lastOrNull()
        val change = if (takeChangeFrom != null && gatheredAmount > amount) {
            Amount<Issued<T>>(gatheredAmount.quantity - amount.quantity, takeChangeFrom.state.data.issuanceDef)
        } else {
            null
        }

        val outputs = if (change != null) {
            // Add a change output and adjust the last output downwards.
            listOf(deriveState(gathered.last().state, change, changeKey))
        } else emptyList()

        for (state in gathered) tx.addInputState(state)
        for (state in outputs) tx.addOutputState(state)
        tx.addCommand(generateExitCommand(amountIssued), amountIssued.token.issuer.party.owningKey)
        return amountIssued.token.issuer.party.owningKey
    }

    /**
     * Generate a transaction that consumes one or more of the given input states to move assets to the given pubkey.
     * Note that the wallet list is not updated: it's up to you to do that.
     *
     * @param onlyFromParties if non-null, the wallet will be filtered to only include asset states issued by the set
     *                        of given parties. This can be useful if the party you're trying to pay has expectations
     *                        about which type of asset claims they are willing to accept.
     */
    @Throws(InsufficientBalanceException::class)
    fun generateSpend(tx: TransactionBuilder,
                      amount: Amount<T>,
                      to: PublicKey,
                      assetsStates: List<StateAndRef<S>>,
                      onlyFromParties: Set<Party>? = null,
                      deriveState: (TransactionState<S>, Amount<Issued<T>>, PublicKey) -> TransactionState<S>,
                      generateMoveCommand: () -> CommandData): List<PublicKey> {
        // Discussion
        //
        // This code is analogous to the Wallet.send() set of methods in bitcoinj, and has the same general outline.
        //
        // First we must select a set of asset states (which for convenience we will call 'coins' here, as in bitcoinj).
        // The input states can be considered our "wallet", and may consist of different products, and with different
        // issuers and deposits.
        //
        // Coin selection is a complex problem all by itself and many different approaches can be used. It is easily
        // possible for different actors to use different algorithms and approaches that, for example, compete on
        // privacy vs efficiency (number of states created). Some spends may be artificial just for the purposes of
        // obfuscation and so on.
        //
        // Having selected input states of the correct asset, we must craft output states for the amount we're sending and
        // the "change", which goes back to us. The change is required to make the amounts balance. We may need more
        // than one change output in order to avoid merging assets from different deposits. The point of this design
        // is to ensure that ledger entries are immutable and globally identifiable.
        //
        // Finally, we add the states to the provided partial transaction.

        val currency = amount.token
        val acceptableCoins = run {
            val ofCurrency = assetsStates.filter { it.state.data.amount.token.product == currency }
            if (onlyFromParties != null)
                ofCurrency.filter { it.state.data.deposit.party in onlyFromParties }
            else
                ofCurrency
        }

        val (gathered, gatheredAmount) = gatherCoins(acceptableCoins, amount)
        val takeChangeFrom = gathered.firstOrNull()
        val change = if (takeChangeFrom != null && gatheredAmount > amount) {
            Amount<Issued<T>>(gatheredAmount.quantity - amount.quantity, takeChangeFrom.state.data.issuanceDef)
        } else {
            null
        }
        val keysUsed = gathered.map { it.state.data.owner }.toSet()

        val states = gathered.groupBy { it.state.data.deposit }.map {
            val coins = it.value
            val totalAmount = coins.map { it.state.data.amount }.sumOrThrow()
            deriveState(coins.first().state, totalAmount, to)
        }

        val outputs = if (change != null) {
            // Just copy a key across as the change key. In real life of course, this works but leaks private data.
            // In bitcoinj we derive a fresh key here and then shuffle the outputs to ensure it's hard to follow
            // value flows through the transaction graph.
            val changeKey = gathered.first().state.data.owner
            // Add a change output and adjust the last output downwards.
            states.subList(0, states.lastIndex) +
                    states.last().let { deriveState(it, it.data.amount - change, it.data.owner) } +
                    deriveState(gathered.last().state, change, changeKey)
        } else states

        for (state in gathered) tx.addInputState(state)
        for (state in outputs) tx.addOutputState(state)
        // What if we already have a move command with the right keys? Filter it out here or in platform code?
        val keysList = keysUsed.toList()
        tx.addCommand(generateMoveCommand(), keysList)
        return keysList
    }

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
