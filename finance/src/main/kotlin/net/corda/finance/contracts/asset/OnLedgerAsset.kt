/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.contracts.asset

import net.corda.core.contracts.*
import net.corda.core.contracts.Amount.Companion.sumOrThrow
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import java.security.PublicKey
import java.util.*

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Generic contract for assets on a ledger
//

/** A simple holder for a (possibly anonymous) [AbstractParty] and a quantity of tokens */
data class PartyAndAmount<T : Any>(val party: AbstractParty, val amount: Amount<T>)

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
abstract class OnLedgerAsset<T : Any, out C : CommandData, S : FungibleAsset<T>> : Contract {
    companion object {
        private val log = contextLogger()
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
         * @param payChangeTo party to pay any change to; this is normally a confidential identity of the calling
         * party.
         * @param deriveState a function to derive an output state based on an input state, amount for the output
         * and public key to pay to.
         * @return A [Pair] of the same transaction builder passed in as [tx], and the list of keys that need to sign
         *         the resulting transaction for it to be valid.
         * @throws InsufficientBalanceException when a cash spending transaction fails because
         *         there is insufficient quantity for a given currency (and optionally set of Issuer Parties).
         */
        @Throws(InsufficientBalanceException::class)
        @JvmStatic
        fun <S : FungibleAsset<T>, T : Any> generateSpend(tx: TransactionBuilder,
                                                          amount: Amount<T>,
                                                          to: AbstractParty,
                                                          acceptableStates: List<StateAndRef<S>>,
                                                          payChangeTo: AbstractParty,
                                                          deriveState: (TransactionState<S>, Amount<Issued<T>>, AbstractParty) -> TransactionState<S>,
                                                          generateMoveCommand: () -> CommandData): Pair<TransactionBuilder, List<PublicKey>> {
            return generateSpend(tx, listOf(PartyAndAmount(to, amount)), acceptableStates, payChangeTo, deriveState, generateMoveCommand)
        }

        /**
         * Adds to the given transaction states that move amounts of a fungible asset to the given parties, using only
         * the provided acceptable input states to find a solution (not all of them may be used in the end). A change
         * output will be generated if the state amounts don't exactly fit.
         *
         * The fungible assets must all be of the same type and the amounts must be summable i.e. amounts of the same
         * token.
         *
         * @param tx A builder, which may contain inputs, outputs and commands already. The relevant components needed
         *           to move the cash will be added on top.
         * @param acceptableStates a list of acceptable input states to use.
         * @param payChangeTo party to pay any change to; this is normally a confidential identity of the calling
         * party. We use a new confidential identity here so that the recipient is not identifiable.
         * @param deriveState a function to derive an output state based on an input state, amount for the output
         * and public key to pay to.
         * @param T A type representing a token
         * @param S A fungible asset state type
         * @return A [Pair] of the same transaction builder passed in as [tx], and the list of keys that need to sign
         *         the resulting transaction for it to be valid.
         * @throws InsufficientBalanceException when a cash spending transaction fails because
         *         there is insufficient quantity for a given currency (and optionally set of Issuer Parties).
         */
        @Throws(InsufficientBalanceException::class)
        @JvmStatic
        fun <S : FungibleAsset<T>, T : Any> generateSpend(tx: TransactionBuilder,
                                                          payments: List<PartyAndAmount<T>>,
                                                          acceptableStates: List<StateAndRef<S>>,
                                                          payChangeTo: AbstractParty,
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

            // TODO: Check that re-running this on the same transaction multiple times does the right thing.

            // The notary may be associated with a locked state only.
            tx.notary = acceptableStates.firstOrNull()?.state?.notary

            // Calculate the total amount we're sending (they must be all of a compatible token).
            val totalSendAmount = payments.map { it.amount }.sumOrThrow()
            // Select a subset of the available states we were given that sums up to >= totalSendAmount.
            val (gathered, gatheredAmount) = gatherCoins(acceptableStates, totalSendAmount)
            check(gatheredAmount >= totalSendAmount)
            val keysUsed = gathered.map { it.state.data.owner.owningKey }

            // Now calculate the output states. This is complicated by the fact that a single payment may require
            // multiple output states, due to the need to keep states separated by issuer. We start by figuring out
            // how much we've gathered for each issuer: this map will keep track of how much we've used from each
            // as we work our way through the payments.
            val statesGroupedByIssuer = gathered.groupBy { it.state.data.amount.token }
            val remainingFromEachIssuer = statesGroupedByIssuer
                    .mapValues {
                        it.value.map {
                            it.state.data.amount
                        }.sumOrThrow()
                    }.toList().toMutableList()
            val outputStates = mutableListOf<TransactionState<S>>()
            for ((party, paymentAmount) in payments) {
                var remainingToPay = paymentAmount.quantity
                while (remainingToPay > 0) {
                    val (token, remainingFromCurrentIssuer) = remainingFromEachIssuer.last()
                    val templateState = statesGroupedByIssuer[token]!!.first().state
                    val delta = remainingFromCurrentIssuer.quantity - remainingToPay
                    when {
                        delta > 0 -> {
                            // The states from the current issuer more than covers this payment.
                            outputStates += deriveState(templateState, Amount(remainingToPay, token), party)
                            remainingFromEachIssuer[0] = Pair(token, Amount(delta, token))
                            remainingToPay = 0
                        }
                        delta == 0L -> {
                            // The states from the current issuer exactly covers this payment.
                            outputStates += deriveState(templateState, Amount(remainingToPay, token), party)
                            remainingFromEachIssuer.removeAt(remainingFromEachIssuer.lastIndex)
                            remainingToPay = 0
                        }
                        delta < 0 -> {
                            // The states from the current issuer don't cover this payment, so we'll have to use >1 output
                            // state to cover this payment.
                            outputStates += deriveState(templateState, remainingFromCurrentIssuer, party)
                            remainingFromEachIssuer.removeAt(remainingFromEachIssuer.lastIndex)
                            remainingToPay -= remainingFromCurrentIssuer.quantity
                        }
                    }
                }
            }

            // Whatever values we have left over for each issuer must become change outputs.
            for ((token, amount) in remainingFromEachIssuer) {
                val templateState = statesGroupedByIssuer[token]!!.first().state
                outputStates += deriveState(templateState, amount, payChangeTo)
            }

            for (state in gathered) tx.addInputState(state)
            for (state in outputStates) tx.addOutputState(state)

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
         * @return the public keys which must sign the transaction for it to be valid.
         */
        @Throws(InsufficientBalanceException::class)
        @JvmStatic
        @Deprecated("Replaced with generateExit() which takes in a party to pay change to")
        fun <S : FungibleAsset<T>, T : Any> generateExit(tx: TransactionBuilder, amountIssued: Amount<Issued<T>>,
                                                         assetStates: List<StateAndRef<S>>,
                                                         deriveState: (TransactionState<S>, Amount<Issued<T>>, AbstractParty) -> TransactionState<S>,
                                                         generateMoveCommand: () -> CommandData,
                                                         generateExitCommand: (Amount<Issued<T>>) -> CommandData): Set<PublicKey> {
            val owner = assetStates.map { it.state.data.owner }.toSet().firstOrNull() ?: throw InsufficientBalanceException(amountIssued)
            return generateExit(tx, amountIssued, assetStates, owner, deriveState, generateMoveCommand, generateExitCommand)
        }

        /**
         * Generate an transaction exiting fungible assets from the ledger.
         *
         * @param tx transaction builder to add states and commands to.
         * @param amountIssued the amount to be exited, represented as a quantity of issued currency.
         * @param assetStates the asset states to take funds from. No checks are done about ownership of these states, it is
         * the responsibility of the caller to check that they do not attempt to exit funds held by others.
         * @param payChangeTo party to pay any change to; this is normally a confidential identity of the calling
         * party.
         * @return the public keys which must sign the transaction for it to be valid.
         */
        @Throws(InsufficientBalanceException::class)
        @JvmStatic
        fun <S : FungibleAsset<T>, T : Any> generateExit(tx: TransactionBuilder, amountIssued: Amount<Issued<T>>,
                                                         assetStates: List<StateAndRef<S>>,
                                                         payChangeTo: AbstractParty,
                                                         deriveState: (TransactionState<S>, Amount<Issued<T>>, AbstractParty) -> TransactionState<S>,
                                                         generateMoveCommand: () -> CommandData,
                                                         generateExitCommand: (Amount<Issued<T>>) -> CommandData): Set<PublicKey> {
            require(assetStates.isNotEmpty()) { "List of states to exit cannot be empty." }
            val currency = amountIssued.token.product
            val amount = Amount(amountIssued.quantity, currency)
            var acceptableCoins = assetStates.filter { (state) -> state.data.amount.token == amountIssued.token }
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
                listOf(deriveState(gathered.last().state, change, payChangeTo))
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
        fun <S : FungibleAsset<T>, T : Any> generateIssue(tx: TransactionBuilder,
                                                          transactionState: TransactionState<S>,
                                                          issueCommand: CommandData): Set<PublicKey> {
            check(tx.inputStates().isEmpty())
            check(tx.outputStates().map { it.data }.filterIsInstance(transactionState.javaClass).isEmpty())
            require(transactionState.data.amount.quantity > 0)
            val at = transactionState.data.amount.token.issuer
            val commandSigner = at.party.owningKey
            tx.addOutputState(transactionState)
            tx.addCommand(issueCommand, commandSigner)
            return setOf(commandSigner)
        }
    }

    abstract fun extractCommands(commands: Collection<CommandWithParties<CommandData>>): Collection<CommandWithParties<C>>

    /**
     * Generate an transaction exiting assets from the ledger.
     *
     * @param tx transaction builder to add states and commands to.
     * @param amountIssued the amount to be exited, represented as a quantity of issued currency.
     * @param assetStates the asset states to take funds from. No checks are done about ownership of these states, it is
     * the responsibility of the caller to check that they do not exit funds held by others.
     * @return the public keys which must sign the transaction for it to be valid.
     */
    @Throws(InsufficientBalanceException::class)
    @Deprecated("Replaced with generateExit() which takes in a party to pay change to")
    fun generateExit(tx: TransactionBuilder, amountIssued: Amount<Issued<T>>,
                     assetStates: List<StateAndRef<S>>): Set<PublicKey> {
        val changeOwner = assetStates.map { it.state.data.owner }.toSet().firstOrNull() ?: throw InsufficientBalanceException(amountIssued)
        return generateExit(
                tx,
                amountIssued,
                assetStates,
                changeOwner,
                deriveState = { state, amount, owner -> deriveState(state, amount, owner) },
                generateMoveCommand = { generateMoveCommand() },
                generateExitCommand = { amount -> generateExitCommand(amount) }
        )
    }

    /**
     * Generate an transaction exiting assets from the ledger.
     *
     * @param tx transaction builder to add states and commands to.
     * @param amountIssued the amount to be exited, represented as a quantity of issued currency.
     * @param assetStates the asset states to take funds from. No checks are done about ownership of these states, it is
     * the responsibility of the caller to check that they do not exit funds held by others.
     * @return the public keys which must sign the transaction for it to be valid.
     */
    @Throws(InsufficientBalanceException::class)
    fun generateExit(tx: TransactionBuilder, amountIssued: Amount<Issued<T>>,
                     assetStates: List<StateAndRef<S>>,
                     payChangeTo: AbstractParty): Set<PublicKey> {
        return generateExit(
                tx,
                amountIssued,
                assetStates,
                payChangeTo,
                deriveState = { state, amount, owner -> deriveState(state, amount, owner) },
                generateMoveCommand = { generateMoveCommand() },
                generateExitCommand = { amount -> generateExitCommand(amount) }
        )
    }

    abstract fun generateExitCommand(amount: Amount<Issued<T>>): CommandData
    abstract fun generateMoveCommand(): MoveCommand

    /**
     * Derive a new transaction state based on the given example, with amount and owner modified. This allows concrete
     * implementations to have fields in their state which we don't know about here, and we simply leave them untouched
     * when sending out "change" from spending/exiting.
     */
    abstract fun deriveState(txState: TransactionState<S>, amount: Amount<Issued<T>>, owner: AbstractParty): TransactionState<S>
}
