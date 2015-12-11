/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts

import core.*
import java.security.PublicKey
import java.security.SecureRandom
import java.util.*

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Cash
//

// Just a fake program identifier for now. In a real system it could be, for instance, the hash of the program bytecode.
val CASH_PROGRAM_ID = SecureHash.sha256("cash")

class InsufficientBalanceException(val amountMissing: Amount) : Exception()

/**
 * A cash transaction may split and merge money represented by a set of (issuer, depositRef) pairs, across multiple
 * input and output states. Imagine a Bitcoin transaction but in which all UTXOs had a colour
 * (a blend of issuer+depositRef) and you couldn't merge outputs of two colours together, but you COULD put them in
 * the same transaction.
 *
 * The goal of this design is to ensure that money can be withdrawn from the ledger easily: if you receive some money
 * via this contract, you always know where to go in order to extract it from the R3 ledger, no matter how many hands
 * it has passed through in the intervening time.
 *
 * At the same time, other contracts that just want money and don't care much who is currently holding it in their
 * vaults can ignore the issuer/depositRefs and just examine the amount fields.
 */
class Cash : Contract {
    /**
     * TODO:
     * 1) hash should be of the contents, not the URI
     * 2) allow the content to be specified at time of instance creation?
     *  Motivation: it's the difference between a state object referencing a programRef, which references a
     *  legalContractReference and a state object which directly references both.  The latter allows the legal wording
     *  to evolve without requiring code changes. But creates a risk that users create objects governed by a program
     *  that is inconsistent with the legal contract
     */
    override val legalContractReference: SecureHash = SecureHash.sha256("https://www.big-book-of-banking-law.gov/cash-claims.html");

    /** A state representing a cash claim against some party */
    data class State(
            /** Where the underlying currency backing this ledger entry can be found (propagated) */
            val deposit: PartyReference,

            val amount: Amount,

            /** There must be a MoveCommand signed by this key to claim the amount */
            val owner: PublicKey
    ) : ContractState {
        override val programRef = CASH_PROGRAM_ID
        override fun toString() = "Cash($amount at $deposit owned by $owner)"
    }

    // Just for grouping
    interface Commands : Command {
        class Move() : TypeOnlyCommand(), Commands

        /**
         * Allows new cash states to be issued into existence: the nonce ("number used once") ensures the transaction
         * has a unique ID even when there are no inputs.
         */
        data class Issue(val nonce: Long = SecureRandom.getInstanceStrong().nextLong()) : Commands

        /**
         * A command stating that money has been withdrawn from the shared ledger and is now accounted for
         * in some other way.
         */
        data class Exit(val amount: Amount) : Commands
    }

    /** This is the function EVERYONE runs */
    override fun verify(tx: TransactionForVerification) {
        // Each group is a set of input/output states with distinct (deposit, currency) attributes. These types
        // of cash are not fungible and must be kept separated for bookkeeping purposes.
        val groups = tx.groupStates<Cash.State>() { Pair(it.deposit, it.amount.currency) }

        for ((inputs, outputs) in groups) {
            requireThat {
                "all outputs represent at least one penny" by outputs.none { it.amount.pennies == 0L }
            }


            val issueCommand = tx.commands.select<Commands.Issue>().singleOrNull()
            if (issueCommand != null) {
                // If we have an issue command, perform special processing: the group is allowed to have no inputs,
                // and the output states must have a deposit reference owned by the signer. Note that this means
                // literally anyone with access to the network can issue cash claims of arbitrary amounts! It is up
                // to the recipient to decide if the backing party is trustworthy or not, via some
                // as-yet-unwritten identity service. See ADP-22 for discussion.
                val outputsInstitution = outputs.map { it.deposit.party }.singleOrNull()
                if (outputsInstitution != null) {
                    requireThat {
                        "the issue command has a nonce" by (issueCommand.value.nonce != 0L)
                        "output deposits are owned by a command signer" by
                                outputs.all { issueCommand.signingParties.contains(it.deposit.party) }
                        "there are no inputs in this group" by inputs.isEmpty()
                    }
                    continue
                } else {
                    // There was an issue command, but it wasn't signed for this group. It may apply to other
                    // groups.
                }
            }

            val inputAmount = inputs.sumCashOrNull() ?: throw IllegalArgumentException("there is at least one cash input for this group")
            val outputAmount = outputs.sumCashOrZero(inputAmount.currency)

            val deposit = inputs.first().deposit

            requireThat {
                "there is at least one cash input" by inputs.isNotEmpty()
                "there are no zero sized inputs" by inputs.none { it.amount.pennies == 0L }
                "there are no zero sized outputs" by outputs.none { it.amount.pennies == 0L }
                "all outputs in this group use the currency of the inputs" by
                        outputs.all { it.amount.currency == inputAmount.currency }
            }

            val exitCommand = tx.commands.select<Commands.Exit>(party = deposit.party).singleOrNull()
            val amountExitingLedger = exitCommand?.value?.amount ?: Amount(0, inputAmount.currency)

            requireThat {
                "for deposit ${deposit.reference} at issuer ${deposit.party.name} the amounts balance" by
                        (inputAmount == outputAmount + amountExitingLedger)
            }

            // Now check the digital signatures on the move command. Every input has an owning public key, and we must
            // see a signature from each of those keys. The actual signatures have been verified against the transaction
            // data by the platform before execution.
            val owningPubKeys  = inputs.map  { it.owner }.toSet()
            val keysThatSigned = tx.commands.requireSingleCommand<Commands.Move>().signers.toSet()
            requireThat {
                "the owning keys are the same as the signing keys" by keysThatSigned.containsAll(owningPubKeys)
            }
        }
    }

    /**
     * Puts together an issuance transaction for the specified amount that starts out being owned by the given pubkey.
     */
    fun craftIssue(tx: PartialTransaction, amount: Amount, at: PartyReference, owner: PublicKey) {
        check(tx.inputStates().isEmpty())
        check(tx.outputStates().sumCashOrNull() == null)
        tx.addOutputState(Cash.State(at, amount, owner))
        tx.addArg(WireCommand(Cash.Commands.Issue(), listOf(at.party.owningKey)))
    }

    /**
     * Generate a transaction that consumes one or more of the given input states to move money to the given pubkey.
     * Note that the wallet list is not updated: it's up to you to do that.
     *
     * @param onlyFromParties if non-null, the wallet will be filtered to only include cash states issued by the set
     *                        of given parties. This can be useful if the party you're trying to pay has expectations
     *                        about which type of cash claims they are willing to accept.
     */
    @Throws(InsufficientBalanceException::class)
    fun craftSpend(tx: PartialTransaction, amount: Amount, to: PublicKey,
                   cashStates: List<StateAndRef<State>>, onlyFromParties: Set<Party>? = null) {
        // Discussion
        //
        // This code is analogous to the Wallet.send() set of methods in bitcoinj, and has the same general outline.
        //
        // First we must select a set of cash states (which for convenience we will call 'coins' here, as in bitcoinj).
        // The input states can be considered our "wallet", and may consist of coins of different currencies, and from
        // different institutions and deposits.
        //
        // Coin selection is a complex problem all by itself and many different approaches can be used. It is easily
        // possible for different actors to use different algorithms and approaches that, for example, compete on
        // privacy vs efficiency (number of states created). Some spends may be artificial just for the purposes of
        // obfuscation and so on.
        //
        // Having selected coins of the right currency, we must craft output states for the amount we're sending and
        // the "change", which goes back to us. The change is required to make the amounts balance. We may need more
        // than one change output in order to avoid merging coins from different deposits. The point of this design
        // is to ensure that ledger entries are immutable and globally identifiable.
        //
        // Finally, we add the states to the provided partial transaction.

        val currency = amount.currency
        val acceptableCoins = run {
            val ofCurrency = cashStates.filter { it.state.amount.currency == currency }
            if (onlyFromParties != null)
                ofCurrency.filter { it.state.deposit.party in onlyFromParties }
            else
                ofCurrency
        }

        val gathered = arrayListOf<StateAndRef<Cash.State>>()
        var gatheredAmount = Amount(0, currency)
        for (c in acceptableCoins) {
            if (gatheredAmount >= amount) break
            gathered.add(c)
            gatheredAmount += c.state.amount
        }

        if (gatheredAmount < amount)
            throw InsufficientBalanceException(amount - gatheredAmount)

        val change = gatheredAmount - amount
        val keysUsed = gathered.map { it.state.owner }.toSet()

        val states = gathered.groupBy { it.state.deposit }.map {
            val (deposit, coins) = it
            val totalAmount = coins.map { it.state.amount }.sumOrThrow()
            State(deposit, totalAmount, to)
        }

        val outputs = if (change.pennies > 0) {
            // Just copy a key across as the change key. In real life of course, this works but leaks private data.
            // In bitcoinj we derive a fresh key here and then shuffle the outputs to ensure it's hard to follow
            // value flows through the transaction graph.
            val changeKey = gathered.first().state.owner
            // Add a change output and adjust the last output downwards.
            states.subList(0, states.lastIndex) +
                    states.last().let { it.copy(amount = it.amount - change) } +
                    State(gathered.last().state.deposit, change, changeKey)
        } else states

        for (state in gathered) tx.addInputState(state.ref)
        for (state in outputs) tx.addOutputState(state)
        // What if we already have a move command with the right keys? Filter it out here or in platform code?
        tx.addArg(WireCommand(Commands.Move(), keysUsed.toList()))
    }
}

// Small DSL extensions.

/** Sums the cash states in the list that are owned by the given key, throwing an exception if there are none. */
fun Iterable<ContractState>.sumCashBy(owner: PublicKey) = filterIsInstance<Cash.State>().filter { it.owner == owner }.map { it.amount }.sumOrThrow()
/** Sums the cash states in the list, throwing an exception if there are none. */
fun Iterable<ContractState>.sumCash() = filterIsInstance<Cash.State>().map { it.amount }.sumOrThrow()
/** Sums the cash states in the list, returning null if there are none. */
fun Iterable<ContractState>.sumCashOrNull() = filterIsInstance<Cash.State>().map { it.amount }.sumOrNull()
/** Sums the cash states in the list, returning zero of the given currency if there are none. */
fun Iterable<ContractState>.sumCashOrZero(currency: Currency) = filterIsInstance<Cash.State>().map { it.amount }.sumOrZero(currency)

