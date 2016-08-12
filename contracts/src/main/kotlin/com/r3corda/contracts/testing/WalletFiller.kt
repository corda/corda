@file:JvmName("WalletFiller")
package com.r3corda.contracts.testing

import com.r3corda.contracts.asset.Cash
import com.r3corda.contracts.asset.DUMMY_CASH_ISSUER
import com.r3corda.contracts.asset.DUMMY_CASH_ISSUER_KEY
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.Issued
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.contracts.TransactionType
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.Wallet
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.DUMMY_NOTARY_KEY
import java.security.KeyPair
import java.security.PublicKey
import java.util.*


/**
 * Creates a random set of between (by default) 3 and 10 cash states that add up to the given amount and adds them
 * to the wallet. This is intended for unit tests. The cash is issued by [DUMMY_CASH_ISSUER] and owned by the legal
 * identity key from the storage service.
 *
 * The service hub needs to provide at least a key management service and a storage service.
 *
 * @param outputNotary the notary to use for output states. The transaction is NOT signed by this notary.
 * @return a wallet object that represents the generated states (it will NOT be the full wallet from the service hub!).
 */
fun ServiceHub.fillWithSomeTestCash(howMuch: Amount<Currency>,
                                    outputNotary: Party = DUMMY_NOTARY,
                                    atLeastThisManyStates: Int = 3,
                                    atMostThisManyStates: Int = 10,
                                    rng: Random = Random(),
                                    ref: OpaqueBytes = OpaqueBytes(ByteArray(1, { 1 })),
                                    ownedBy: PublicKey? = null): Wallet {
    val amounts = calculateRandomlySizedAmounts(howMuch, atLeastThisManyStates, atMostThisManyStates, rng)

    val myKey: PublicKey = ownedBy ?: storageService.myLegalIdentityKey.public

    // We will allocate one state to one transaction, for simplicities sake.
    val cash = Cash()
    val transactions: List<SignedTransaction> = amounts.map { pennies ->
        val issuance = TransactionType.General.Builder(null)
        cash.generateIssue(issuance, Amount(pennies, Issued(DUMMY_CASH_ISSUER.copy(reference = ref), howMuch.token)), myKey, outputNotary)
        issuance.signWith(DUMMY_CASH_ISSUER_KEY)

        return@map issuance.toSignedTransaction(true)
    }

    recordTransactions(transactions)

    // Get all the StateRefs of all the generated transactions.
    val states = transactions.flatMap { stx ->
        stx.tx.outputs.indices.map { i -> stx.tx.outRef<Cash.State>(i) }
    }

    return Wallet(states)
}

private fun calculateRandomlySizedAmounts(howMuch: Amount<Currency>, min: Int, max: Int, rng: Random): LongArray {
    val numStates = min + Math.floor(rng.nextDouble() * (max - min)).toInt()
    val amounts = LongArray(numStates)
    val baseSize = howMuch.quantity / numStates
    check(baseSize > 0) { baseSize }
    var filledSoFar = 0L
    for (i in 0..numStates - 1) {
        if (i < numStates - 1) {
            // Adjust the amount a bit up or down, to give more realistic amounts (not all identical).
            amounts[i] = baseSize + (baseSize / 2 * (rng.nextDouble() - 0.5)).toLong()
            filledSoFar += amounts[i]
        } else {
            // Handle inexact rounding.
            amounts[i] = howMuch.quantity - filledSoFar
        }
        check(amounts[i] >= 0) { "${amounts[i]} : $filledSoFar : $howMuch" }
    }
    check(amounts.sum() == howMuch.quantity)
    return amounts
}