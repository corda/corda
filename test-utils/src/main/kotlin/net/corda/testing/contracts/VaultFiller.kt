@file:JvmName("VaultFiller")

package net.corda.testing.contracts

import net.corda.contracts.Commodity
import net.corda.contracts.DealState
import net.corda.contracts.DummyDealContract
import net.corda.contracts.asset.*
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.CHARLIE
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.DUMMY_NOTARY_KEY
import java.security.KeyPair
import java.security.PublicKey
import java.time.Instant
import java.time.Instant.now
import java.util.*

@JvmOverloads
fun ServiceHub.fillWithSomeTestDeals(dealIds: List<String>,
                                     participants: List<AbstractParty> = emptyList()) : Vault<DealState> {
    val myKey: PublicKey = myInfo.legalIdentity.owningKey
    val me = AnonymousParty(myKey)

    val transactions: List<SignedTransaction> = dealIds.map {
        // Issue a deal state
        val dummyIssue = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
            addOutputState(DummyDealContract.State(ref = it, participants = participants.plus(me)))
            signWith(DUMMY_NOTARY_KEY)
        }
        return@map signInitialTransaction(dummyIssue)
    }

    recordTransactions(transactions)

    // Get all the StateAndRefs of all the generated transactions.
    val states = transactions.flatMap { stx ->
        stx.tx.outputs.indices.map { i -> stx.tx.outRef<DealState>(i) }
    }

    return Vault(states)
}

@JvmOverloads
fun ServiceHub.fillWithSomeTestLinearStates(numberToCreate: Int,
                                            externalId: String? = null,
                                            participants: List<AbstractParty> = emptyList(),
                                            linearString: String = "",
                                            linearNumber: Long = 0L,
                                            linearBoolean: Boolean = false,
                                            linearTimestamp: Instant = now()) : Vault<LinearState> {
    val myKey: PublicKey = myInfo.legalIdentity.owningKey
    val me = AnonymousParty(myKey)

    val transactions: List<SignedTransaction> = (1..numberToCreate).map {
        // Issue a Linear state
        val dummyIssue = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
            addOutputState(DummyLinearContract.State(
                    linearId = UniqueIdentifier(externalId),
                    participants = participants.plus(me),
                    linearString = linearString,
                    linearNumber = linearNumber,
                    linearBoolean = linearBoolean,
                    linearTimestamp = linearTimestamp))
            signWith(DUMMY_NOTARY_KEY)
        }

        return@map signInitialTransaction(dummyIssue)
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
                                    ownedBy: AbstractParty? = null,
                                    issuedBy: PartyAndReference = DUMMY_CASH_ISSUER,
                                    issuerKey: KeyPair = DUMMY_CASH_ISSUER_KEY): Vault<Cash.State> {
    val amounts = calculateRandomlySizedAmounts(howMuch, atLeastThisManyStates, atMostThisManyStates, rng)

    val myKey: PublicKey = ownedBy?.owningKey ?: myInfo.legalIdentity.owningKey
    val me = AnonymousParty(myKey)

    // We will allocate one state to one transaction, for simplicities sake.
    val cash = Cash()
    val transactions: List<SignedTransaction> = amounts.map { pennies ->
        val issuance = TransactionType.General.Builder(null as Party?)
        cash.generateIssue(issuance, Amount(pennies, Issued(issuedBy.copy(reference = ref), howMuch.token)), me, outputNotary)
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

// TODO: need to make all FungibleAsset commands (issue, move, exit) generic
fun ServiceHub.fillWithSomeTestCommodity(amount: Amount<Commodity>,
                                         outputNotary: Party = DUMMY_NOTARY,
                                         ref: OpaqueBytes = OpaqueBytes(ByteArray(1, { 1 })),
                                         ownedBy: AbstractParty? = null,
                                         issuedBy: PartyAndReference = DUMMY_OBLIGATION_ISSUER.ref(1),
                                         issuerKey: KeyPair = DUMMY_OBLIGATION_ISSUER_KEY): Vault<CommodityContract.State> {
    val myKey: PublicKey = ownedBy?.owningKey ?: myInfo.legalIdentity.owningKey
    val me = AnonymousParty(myKey)

    val commodity = CommodityContract()
    val issuance = TransactionType.General.Builder(null as Party?)
    commodity.generateIssue(issuance, Amount(amount.quantity, Issued(issuedBy.copy(reference = ref), amount.token)), me, outputNotary)
    issuance.signWith(issuerKey)
    val transaction = issuance.toSignedTransaction(true)

    recordTransactions(transaction)

    return Vault(setOf(transaction.tx.outRef<CommodityContract.State>(0)))
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

fun <T : LinearState> ServiceHub.consume(states: List<StateAndRef<T>>) {
    // Create a txn consuming different contract types
    states.forEach {
        val consumedTx = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
            addInputState(it)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction()

        recordTransactions(consumedTx)
    }
}

fun <T : LinearState> ServiceHub.consumeAndProduce(stateAndRef: StateAndRef<T>): StateAndRef<T> {
    // Create a txn consuming different contract types
    val consumedTx = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
        addInputState(stateAndRef)
        signWith(DUMMY_NOTARY_KEY)
    }.toSignedTransaction()

    recordTransactions(consumedTx)

    // Create a txn consuming different contract types
    val producedTx = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
        addOutputState(DummyLinearContract.State(linearId = stateAndRef.state.data.linearId,
                participants = stateAndRef.state.data.participants))
        signWith(DUMMY_NOTARY_KEY)
    }.toSignedTransaction()

    recordTransactions(producedTx)

    return producedTx.tx.outRef<T>(0)
}

fun <T : LinearState> ServiceHub.consumeAndProduce(states: List<StateAndRef<T>>) {
    states.forEach {
        consumeAndProduce(it)
    }
}

fun ServiceHub.consumeDeals(dealStates: List<StateAndRef<DealState>>) = consume(dealStates)
fun ServiceHub.consumeLinearStates(linearStates: List<StateAndRef<LinearState>>) = consume(linearStates)
fun ServiceHub.evolveLinearStates(linearStates: List<StateAndRef<LinearState>>) = consumeAndProduce(linearStates)
fun ServiceHub.evolveLinearState(linearState: StateAndRef<LinearState>) : StateAndRef<LinearState> = consumeAndProduce(linearState)

@JvmOverloads
fun ServiceHub.consumeCash(amount: Amount<Currency>, to: Party = CHARLIE): Vault<Cash.State> {
    // A tx that spends our money.
    val spendTX = TransactionType.General.Builder(DUMMY_NOTARY).apply {
        vaultService.generateSpend(this, amount, to)
        signWith(DUMMY_NOTARY_KEY)
    }.toSignedTransaction(checkSufficientSignatures = false)

    recordTransactions(spendTX)

    // Get all the StateRefs of all the generated transactions.
    val states = spendTX.tx.outputs.indices.map { i -> spendTX.tx.outRef<Cash.State>(i) }

    return Vault(states)
}

