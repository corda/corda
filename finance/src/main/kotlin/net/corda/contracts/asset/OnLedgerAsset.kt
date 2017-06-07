package net.corda.contracts.asset

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import java.security.PublicKey
import java.util.*

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Generic contract for assets on a ledger
//

/**
 * An asset transaction may split and merge assets represented by a set of (issuer, depositRef) pairs, across multiple
 * input and output states. Imagine a Bitcoin transaction but in which all UTXOs had a colour (a blend of
 * issuer+depositRef) and you couldn't merge outputs of two colours together, but you COULD put them in the same
 * transaction.
 *
 * The goal of this design is to ensure that assets can be withdrawn from the ledger easily: if you receive some asset
 * via this contract, you always know where to go in order to extract it from the R3 ledger, no matter how many hands
 * it has passed through in the intervening time.
 *
 * At the same time, other contracts that just want assets and don't care much who is currently holding it can ignore
 * the issuer/depositRefs and just examine the amount fields.
 */
abstract class OnLedgerAsset<T : Any, C : CommandData, S : FungibleAsset<T>> : Contract {
    companion object {
        val log = loggerFor<OnLedgerAsset<*, *, *>>()

        /**
         * Generate a transaction that moves an amount of currency to the given pubkey.
         *
         * Note: an [Amount] of [Currency] is only fungible for a given Issuer Party within a [FungibleAsset]
         *
         * @param tx A builder, which may contain inputs, outputs and commands already. The relevant components needed
         *           to move the cash will be added on top.
         * @param amount How much currency to send.
         * @param to a key of the recipient.
         * @param acceptableStates a list of acceptable input states to use.
         * @param deriveState a function to derive an output state based on an input state, amount for the output
         * and public key to pay to.
         * @return A [Pair] of the same transaction builder passed in as [tx], and the list of keys that need to sign
         *         the resulting transaction for it to be valid.
         * @throws InsufficientBalanceException when a cash spending transaction fails because
         *         there is insufficient quantity for a given currency (and optionally set of Issuer Parties).
         */
        @Throws(InsufficientBalanceException::class)
        @JvmStatic
        fun <S : FungibleAsset<T>, T: Any> generateSpend(tx: TransactionBuilder,
                                                         amount: Amount<T>,
                                                         to: AbstractParty,
                                                         acceptableStates: List<StateAndRef<S>>,
                                                         deriveState: (TransactionState<S>, Amount<Issued<T>>, AbstractParty) -> TransactionState<S>,
                                                         generateMoveCommand: () -> CommandData): Pair<TransactionBuilder, List<PublicKey>> {
            // Discussion
            //
            // This code is analogous to the Wallet.send() set of methods in bitcoinj, and has the same general outline.
            //
            // First we must select a set of asset states (which for convenience we will call 'coins' here, as in bitcoinj).
            // The input states can be considered our "vault", and may consist of different products, and with different
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

            // TODO: We should be prepared to produce multiple transactions spending inputs from
            // different notaries, or at least group states by notary and take the set with the
            // highest total value.

            // notary may be associated with locked state only
            tx.notary = acceptableStates.firstOrNull()?.state?.notary

            val (gathered, gatheredAmount) = gatherCoins(acceptableStates, amount)

            val takeChangeFrom = gathered.firstOrNull()
            val change = if (takeChangeFrom != null && gatheredAmount > amount) {
                Amount(gatheredAmount.quantity - amount.quantity, takeChangeFrom.state.data.amount.token)
            } else {
                null
            }
            val keysUsed = gathered.map { it.state.data.owner.owningKey }

            val states = gathered.groupBy { it.state.data.amount.token.issuer }.map {
                val coins = it.value
                val totalAmount = coins.map { it.state.data.amount }.sumOrThrow()
                deriveState(coins.first().state, totalAmount, to)
            }.sortedBy { it.data.amount.quantity }

            val outputs = if (change != null) {
                // Just copy a key across as the change key. In real life of course, this works but leaks private data.
                // In bitcoinj we derive a fresh key here and then shuffle the outputs to ensure it's hard to follow
                // value flows through the transaction graph.
                val existingOwner = gathered.first().state.data.owner
                // Add a change output and adjust the last output downwards.
                states.subList(0, states.lastIndex) +
                        states.last().let {
                            val spent = it.data.amount.withoutIssuer() - change.withoutIssuer()
                            deriveState(it, Amount(spent.quantity, it.data.amount.token), it.data.owner)
                        } +
                        states.last().let {
                            deriveState(it, Amount(change.quantity, it.data.amount.token), existingOwner)
                        }
            } else states

            for (state in gathered) tx.addInputState(state)
            for (state in outputs) tx.addOutputState(state)

            // What if we already have a move command with the right keys? Filter it out here or in platform code?
            tx.addCommand(generateMoveCommand(), keysUsed)

            return Pair(tx, keysUsed)
        }

        /**
         * Gather assets from the given list of states, sufficient to match or exceed the given amount.
         *
         * @param acceptableCoins list of states to use as inputs.
         * @param amount the amount to gather states up to.
         * @throws InsufficientBalanceException if there isn't enough value in the states to cover the requested amount.
         */
        @Throws(InsufficientBalanceException::class)
        private fun <S : FungibleAsset<T>, T : Any> gatherCoins(acceptableCoins: Collection<StateAndRef<S>>,
                                                                amount: Amount<T>): Pair<ArrayList<StateAndRef<S>>, Amount<T>> {
            require(amount.quantity > 0) { "Cannot gather zero coins" }
            val gathered = arrayListOf<StateAndRef<S>>()
            var gatheredAmount = Amount(0, amount.token)
            for (c in acceptableCoins) {
                if (gatheredAmount >= amount) break
                gathered.add(c)
                gatheredAmount += Amount(c.state.data.amount.quantity, amount.token)
            }

            if (gatheredAmount < amount) {
                log.trace { "Insufficient balance: requested $amount, available $gatheredAmount" }
                throw InsufficientBalanceException(amount - gatheredAmount)
            }

            log.trace { "Gathered coins: requested $amount, available $gatheredAmount, change: ${gatheredAmount - amount}" }

            return Pair(gathered, gatheredAmount)
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
        @Throws(InsufficientBalanceException::class)
        @JvmStatic
        fun <S : FungibleAsset<T>, T: Any> generateExit(tx: TransactionBuilder, amountIssued: Amount<Issued<T>>,
                                                        assetStates: List<StateAndRef<S>>,
                                                        deriveState: (TransactionState<S>, Amount<Issued<T>>, AbstractParty) -> TransactionState<S>,
                                                        generateMoveCommand: () -> CommandData,
                                                        generateExitCommand: (Amount<Issued<T>>) -> CommandData): Set<PublicKey> {
            val owner = assetStates.map { it.state.data.owner }.toSet().singleOrNull() ?: throw InsufficientBalanceException(amountIssued)
            val currency = amountIssued.token.product
            val amount = Amount(amountIssued.quantity, currency)
            var acceptableCoins = assetStates.filter { ref -> ref.state.data.amount.token == amountIssued.token }
            tx.notary = acceptableCoins.firstOrNull()?.state?.notary
            // TODO: We should be prepared to produce multiple transactions exiting inputs from
            // different notaries, or at least group states by notary and take the set with the
            // highest total value
            acceptableCoins = acceptableCoins.filter { it.state.notary == tx.notary }

            val (gathered, gatheredAmount) = gatherCoins(acceptableCoins, amount)
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
            val moveKeys = gathered.map { it.state.data.owner.owningKey }
            val exitKeys = gathered.flatMap { it.state.data.exitKeys }
            tx.addCommand(generateMoveCommand(), moveKeys)
            tx.addCommand(generateExitCommand(amountIssued), exitKeys)
            return (moveKeys + exitKeys).toSet()
        }

        /**
         * Puts together an issuance transaction for the specified state. Normally contracts will provide convenient
         * wrappers around this function, which build the state for you, and those should be used in preference.
         */
        @JvmStatic
        fun <S : FungibleAsset<T>, T: Any> generateIssue(tx: TransactionBuilder,
                                                         transactionState: TransactionState<S>,
                                                         issueCommand: CommandData) {
            check(tx.inputStates().isEmpty())
            check(tx.outputStates().map { it.data }.filterIsInstance(transactionState.javaClass).isEmpty())
            require(transactionState.data.amount.quantity > 0)
            val at = transactionState.data.amount.token.issuer
            tx.addOutputState(transactionState)
            tx.addCommand(issueCommand, at.party.owningKey)
        }
    }

    abstract fun extractCommands(commands: Collection<AuthenticatedObject<CommandData>>): Collection<AuthenticatedObject<C>>

    /**
     * Generate an transaction exiting assets from the ledger.
     *
     * @param tx transaction builder to add states and commands to.
     * @param amountIssued the amount to be exited, represented as a quantity of issued currency.
     * @param changeKey the key to send any change to. This needs to be explicitly stated as the input states are not
     * necessarily owned by us.
     * @param assetStates the asset states to take funds from. No checks are done about ownership of these states, it is
     * the responsibility of the caller to check that they do not exit funds held by others.
     * @return the public keys who must sign the transaction for it to be valid.
     */
    @Throws(InsufficientBalanceException::class)
    fun generateExit(tx: TransactionBuilder, amountIssued: Amount<Issued<T>>,
                     assetStates: List<StateAndRef<S>>): Set<PublicKey> {
        return generateExit(
                tx,
                amountIssued,
                assetStates,
                deriveState = { state, amount, owner -> deriveState(state, amount, owner) },
                generateMoveCommand = { -> generateMoveCommand() },
                generateExitCommand = { amount -> generateExitCommand(amount) }
        )
    }

    abstract fun generateExitCommand(amount: Amount<Issued<T>>): FungibleAsset.Commands.Exit<T>
    abstract fun generateIssueCommand(): FungibleAsset.Commands.Issue
    abstract fun generateMoveCommand(): FungibleAsset.Commands.Move

    /**
     * Derive a new transaction state based on the given example, with amount and owner modified. This allows concrete
     * implementations to have fields in their state which we don't know about here, and we simply leave them untouched
     * when sending out "change" from spending/exiting.
     */
    abstract fun deriveState(txState: TransactionState<S>, amount: Amount<Issued<T>>, owner: AbstractParty): TransactionState<S>
}
