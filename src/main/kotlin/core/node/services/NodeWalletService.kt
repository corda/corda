/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node.services

import contracts.Cash
import core.*
import core.utilities.loggerFor
import core.utilities.trace
import java.security.PublicKey
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * This class implements a simple, in memory wallet that tracks states that are owned by us, and also has a convenience
 * method to auto-generate some self-issued cash states that can be used for test trading. A real wallet would persist
 * states relevant to us into a database and once such a wallet is implemented, this scaffolding can be removed.
 */
@ThreadSafe
class NodeWalletService(private val services: ServiceHub) : WalletService {
    private val log = loggerFor<NodeWalletService>()

    // Variables inside InnerState are protected with a lock by the ThreadBox and aren't in scope unless you're
    // inside mutex.locked {} code block. So we can't forget to take the lock unless we accidentally leak a reference
    // to wallet somewhere.
    private class InnerState {
        var wallet: Wallet = Wallet(emptyList<StateAndRef<OwnableState>>())
    }
    private val mutex = ThreadBox(InnerState())

    override val currentWallet: Wallet get() = mutex.locked { wallet }

    /**
     * Returns a snapshot of how much cash we have in each currency, ignoring details like issuer. Note: currencies for
     * which we have no cash evaluate to null, not 0.
     */
    override val cashBalances: Map<Currency, Amount>
        get() = mutex.locked { wallet }.let { wallet ->
            wallet.states.
                // Select the states we own which are cash, ignore the rest, take the amounts.
                mapNotNull { (it.state as? Cash.State)?.amount }.
                // Turn into a Map<Currency, List<Amount>> like { GBP -> (£100, £500, etc), USD -> ($2000, $50) }
                groupBy { it.currency }.
                // Collapse to Map<Currency, Amount> by summing all the amounts of the same currency together.
                mapValues { it.value.sumOrThrow() }
        }

    override fun notifyAll(txns: Iterable<WireTransaction>): Wallet {
        val ourKeys = services.keyManagementService.keys.keys

        // Note how terribly incomplete this all is!
        //
        // - We don't notify anyone of anything, there are no event listeners.
        // - We don't handle or even notice invalidations due to double spends of things in our wallet.
        // - We have no concept of confidence (for txns where there is no definite finality).
        // - No notification that keys are used, for the case where we observe a spend of our own states.
        // - No ability to create complex spends.
        // - No logging or tracking of how the wallet got into this state.
        // - No persistence.
        // - Does tx relevancy calculation and key management need to be interlocked? Probably yes.
        //
        // ... and many other things .... (Wallet.java in bitcoinj is several thousand lines long)

        mutex.locked {
            // Starting from the current wallet, keep applying the transaction updates, calculating a new Wallet each
            // time, until we get to the result (this is perhaps a bit inefficient, but it's functional and easily
            // unit tested).
            wallet = txns.fold(currentWallet) { current, tx -> current.update(tx, ourKeys) }
            return wallet
        }
    }

    private fun Wallet.update(tx: WireTransaction, ourKeys: Set<PublicKey>): Wallet {
        val ourNewStates = tx.outputs.
                filterIsInstance<OwnableState>().
                filter { it.owner in ourKeys }.
                map { tx.outRef<OwnableState>(it) }

        // Now calculate the states that are being spent by this transaction.
        val consumed: Set<StateRef> = states.map { it.ref }.intersect(tx.inputs)

        // Is transaction irrelevant?
        if (consumed.isEmpty() && ourNewStates.isEmpty()) {
            log.trace { "tx ${tx.id} was irrelevant to this wallet, ignoring" }
            return this
        }

        // And calculate the new wallet.
        val newStates = states.filter { it.ref !in consumed } + ourNewStates

        log.trace {
            "Applied tx ${tx.id.prefixChars()} to the wallet: consumed ${consumed.size} states and added ${newStates.size}"
        }

        return Wallet(newStates)
    }

    /**
     * Creates a random set of between (by default) 3 and 10 cash states that add up to the given amount and adds them
     * to the wallet.
     *
     * The cash is self issued with the current nodes identity, as fetched from the storage service. Thus it
     * would not be trusted by any sensible market participant and is effectively an IOU. If it had been issued by
     * the central bank, well ... that'd be a different story altogether.
     *
     * TODO: Move this out of NodeWalletService
     */
    fun fillWithSomeTestCash(howMuch: Amount, atLeastThisManyStates: Int = 3, atMostThisManyStates: Int = 10,
                             rng: Random = Random()): Wallet {
        val amounts = calculateRandomlySizedAmounts(howMuch, atLeastThisManyStates, atMostThisManyStates, rng)

        val myIdentity = services.storageService.myLegalIdentity
        val myKey = services.storageService.myLegalIdentityKey

        // We will allocate one state to one transaction, for simplicities sake.
        val cash = Cash()
        val transactions = amounts.map { pennies ->
            // This line is what makes the cash self issued. We just use zero as our deposit reference: we don't need
            // this field as there's no other database or source of truth we need to sync with.
            val depositRef = myIdentity.ref(0)

            val issuance = TransactionBuilder()
            val freshKey = services.keyManagementService.freshKey()
            cash.generateIssue(issuance, Amount(pennies, howMuch.currency), depositRef, freshKey.public)
            issuance.signWith(myKey)

            return@map issuance.toSignedTransaction(true)
        }

        // TODO: Centralise the process of transaction acceptance and filtering into the wallet, then move this out.
        services.storageService.validatedTransactions.putAll(transactions.associateBy { it.id })

        return notifyAll(transactions.map { it.tx })
    }

    private fun calculateRandomlySizedAmounts(howMuch: Amount, min: Int, max: Int, rng: Random): LongArray {
        val numStates = min + Math.floor(rng.nextDouble() * (max - min)).toInt()
        val amounts = LongArray(numStates)
        val baseSize = howMuch.pennies / numStates
        var filledSoFar = 0L
        for (i in 0..numStates - 1) {
            if (i < numStates - 1) {
                // Adjust the amount a bit up or down, to give more realistic amounts (not all identical).
                amounts[i] = baseSize + (baseSize / 2 * (rng.nextDouble() - 0.5)).toLong()
                filledSoFar += baseSize
            } else {
                // Handle inexact rounding.
                amounts[i] = howMuch.pennies - filledSoFar
            }
        }
        return amounts
    }
}
