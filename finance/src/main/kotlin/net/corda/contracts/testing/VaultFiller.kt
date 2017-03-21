@file:JvmName("VaultFiller")

package net.corda.contracts.testing

import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.contracts.asset.DUMMY_CASH_ISSUER_KEY
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import java.security.KeyPair
import java.security.PublicKey
import java.util.*

fun ServiceHub.fillWithSomeTestDeals(dealIds: List<String>, revisions: Int? = 0) : Vault<DealState> {
    val freshKey = keyManagementService.freshKey()
    val transactions: List<SignedTransaction> = dealIds.map {
        // Issue a deal state
        val dummyIssue = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
            addOutputState(DummyDealContract.State(ref = it, participants = listOf(freshKey.public)))
            signWith(freshKey)
            signWith(DUMMY_NOTARY_KEY)
        }
        return@map dummyIssue.toSignedTransaction()
    }

    recordTransactions(transactions)

    // Get all the StateAndRefs of all the generated transactions.
    val states = transactions.flatMap { stx ->
        stx.tx.outputs.indices.map { i -> stx.tx.outRef<DealState>(i) }
    }

    return Vault(states)
}

fun ServiceHub.fillWithSomeTestLinearStates(numberToCreate: Int, uid: UniqueIdentifier = UniqueIdentifier()) : Vault<LinearState> {
    val freshKey = keyManagementService.freshKey()

    val transactions: List<SignedTransaction> = (1..numberToCreate).map {
        // Issue a Linear state
        val dummyIssue = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
            addOutputState(DummyLinearContract.State(linearId = uid, participants = listOf(freshKey.public)))
            signWith(freshKey)
            signWith(DUMMY_NOTARY_KEY)
        }

        return@map dummyIssue.toSignedTransaction(true)
    }

    recordTransactions(transactions)

    // Get all the StateAndRefs of all the generated transactions.
    val states = transactions.flatMap { stx ->
        stx.tx.outputs.indices.map { i -> stx.tx.outRef<LinearState>(i) }
    }

    return Vault(states)
}

/**
 * Creates a random set of between (by default) 3 and 10 cash states that add up to the given amount and adds them
 * to the vault. This is intended for unit tests. The cash is issued by [DUMMY_CASH_ISSUER] and owned by the legal
 * identity key from the storage service.
 *
 * The service hub needs to provide at least a key management service and a storage service.
 *
 * @param outputNotary the notary to use for output states. The transaction is NOT signed by this notary.
 * @return a vault object that represents the generated states (it will NOT be the full vault from the service hub!).
 */
fun ServiceHub.fillWithSomeTestCash(howMuch: Amount<Currency>,
                                    outputNotary: Party = DUMMY_NOTARY,
                                    atLeastThisManyStates: Int = 3,
                                    atMostThisManyStates: Int = 10,
                                    rng: Random = Random(),
                                    ref: OpaqueBytes = OpaqueBytes(ByteArray(1, { 1 })),
                                    ownedBy: PublicKey? = null,
                                    issuedBy: PartyAndReference = DUMMY_CASH_ISSUER,
                                    issuerKey: KeyPair = DUMMY_CASH_ISSUER_KEY): Vault<Cash.State> {
    val amounts = calculateRandomlySizedAmounts(howMuch, atLeastThisManyStates, atMostThisManyStates, rng)

    val myKey: PublicKey = ownedBy ?: myInfo.legalIdentity.owningKey

    // We will allocate one state to one transaction, for simplicities sake.
    val cash = Cash()
    val transactions: List<SignedTransaction> = amounts.map { pennies ->
        val issuance = TransactionType.General.Builder(null)
        cash.generateIssue(issuance, Amount(pennies, Issued(issuedBy.copy(reference = ref), howMuch.token)), myKey, outputNotary)
        issuance.signWith(issuerKey)

        return@map issuance.toSignedTransaction(true)
    }

    recordTransactions(transactions)

    // Get all the StateRefs of all the generated transactions.
    val states = transactions.flatMap { stx ->
        stx.tx.outputs.indices.map { i -> stx.tx.outRef<Cash.State>(i) }
    }

    return Vault(states)
}

fun calculateRandomlySizedAmounts(howMuch: Amount<Currency>, min: Int, max: Int, rng: Random): LongArray {
    val numSlots = min + Math.floor(rng.nextDouble() * (max - min)).toInt()
    val baseSize = howMuch.quantity / numSlots
    check(baseSize > 0) { baseSize }

    val amounts = LongArray(numSlots) { baseSize }
    var distanceFromGoal = 0L
    // If we want 10 slots then max adjust is 0.1, so even if all random numbers come out to the largest downward
    // adjustment possible, the last slot ends at zero. With 20 slots, max adjust is 0.05 etc.
    val maxAdjust = 1.0 / numSlots
    for (i in amounts.indices) {
        if (i != amounts.lastIndex) {
            val adjustBy = rng.nextDouble() * maxAdjust - (maxAdjust / 2)
            val adjustment = (1 + adjustBy)
            val adjustTo = (amounts[i] * adjustment).toLong()
            amounts[i] = adjustTo
            distanceFromGoal += baseSize - adjustTo
        } else {
            amounts[i] += distanceFromGoal
        }
    }

    // The desired amount may not have divided equally to start with, so adjust the first value to make up.
    amounts[0] += howMuch.quantity - amounts.sum()

    return amounts
}
