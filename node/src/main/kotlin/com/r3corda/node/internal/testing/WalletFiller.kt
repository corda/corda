package com.r3corda.node.internal.testing

import com.r3corda.contracts.cash.Cash
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.Issued
import com.r3corda.core.contracts.TransactionType
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.serialization.OpaqueBytes
import java.util.*

object WalletFiller {

    /**
     * Creates a random set of between (by default) 3 and 10 cash states that add up to the given amount and adds them
     * to the wallet.
     *
     * The cash is self issued with the current nodes identity, as fetched from the storage service. Thus it
     * would not be trusted by any sensible market participant and is effectively an IOU. If it had been issued by
     * the central bank, well ... that'd be a different story altogether.
     */
    fun fillWithSomeTestCash(services: ServiceHub, notary: Party, howMuch: Amount<Currency>, atLeastThisManyStates: Int = 3,
                             atMostThisManyStates: Int = 10, rng: Random = Random(),
                             ref: OpaqueBytes = OpaqueBytes(ByteArray(1, { 0 }))) {
        val amounts = calculateRandomlySizedAmounts(howMuch, atLeastThisManyStates, atMostThisManyStates, rng)

        val myIdentity = services.storageService.myLegalIdentity
        val myKey = services.storageService.myLegalIdentityKey

        // We will allocate one state to one transaction, for simplicities sake.
        val cash = Cash()
        val transactions = amounts.map { pennies ->
            // This line is what makes the cash self issued. We just use zero as our deposit reference: we don't need
            // this field as there's no other database or source of truth we need to sync with.
            val depositRef = myIdentity.ref(ref)

            val issuance = TransactionType.General.Builder()
            val freshKey = services.keyManagementService.freshKey()
            cash.generateIssue(issuance, Amount(pennies, Issued(depositRef, howMuch.token)), freshKey.public, notary)
            issuance.signWith(myKey)

            return@map issuance.toSignedTransaction(true)
        }

        services.recordTransactions(transactions)
    }

    private fun calculateRandomlySizedAmounts(howMuch: Amount<Currency>, min: Int, max: Int, rng: Random): LongArray {
        val numStates = min + Math.floor(rng.nextDouble() * (max - min)).toInt()
        val amounts = LongArray(numStates)
        val baseSize = howMuch.quantity / numStates
        var filledSoFar = 0L
        for (i in 0..numStates - 1) {
            if (i < numStates - 1) {
                // Adjust the amount a bit up or down, to give more realistic amounts (not all identical).
                amounts[i] = baseSize + (baseSize / 2 * (rng.nextDouble() - 0.5)).toLong()
                filledSoFar += baseSize
            } else {
                // Handle inexact rounding.
                amounts[i] = howMuch.quantity - filledSoFar
            }
        }
        return amounts
    }
}