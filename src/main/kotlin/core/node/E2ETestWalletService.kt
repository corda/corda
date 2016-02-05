/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import contracts.Cash
import core.*
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * This class implements a simple, in memory wallet that tracks states that are owned by us, and also has a convenience
 * method to auto-generate some self-issued cash states that can be used for test trading. A real wallet would persist
 * states relevant to us into a database and once such a wallet is implemented, this scaffolding can be removed.
 */
@ThreadSafe
class E2ETestWalletService(private val services: ServiceHub) : WalletService {
    // Variables inside InnerState are protected with a lock by the ThreadBox and aren't in scope unless you're
    // inside mutex.locked {} code block. So we can't forget to take the lock unless we accidentally leak a reference
    // to wallet somewhere.
    private class InnerState {
        var wallet: Wallet = Wallet(emptyList<StateAndRef<OwnableState>>())
    }
    private val mutex = ThreadBox(InnerState())

    override val currentWallet: Wallet get() = mutex.locked { wallet }

    /**
     * Creates a random set of between (by default) 3 and 10 cash states that add up to the given amount and adds them
     * to the wallet.
     *
     * The cash is self issued with the current nodes identity, as fetched from the storage service. Thus it
     * would not be trusted by any sensible market participant and is effectively an IOU. If it had been issued by
     * the central bank, well ... that'd be a different story altogether.
     */
    fun fillWithSomeTestCash(howMuch: Amount, atLeastThisManyStates: Int = 3, atMostThisManyStates: Int = 10, rng: Random = Random()) {
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
            cash.craftIssue(issuance, Amount(pennies, howMuch.currency), depositRef, freshKey.public)
            issuance.signWith(myKey)

            return@map issuance.toSignedTransaction(true)
        }

        val statesAndRefs = transactions.map {
            StateAndRef(it.tx.outputStates[0] as OwnableState, ContractStateRef(it.id, 0))
        }

        mutex.locked {
            wallet = wallet.copy(wallet.states + statesAndRefs)
        }
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
