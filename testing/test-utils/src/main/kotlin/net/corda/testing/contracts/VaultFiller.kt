@file:JvmName("VaultFiller")

package net.corda.testing.contracts

import net.corda.core.contracts.*
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.contracts.Commodity
import net.corda.finance.contracts.DealState
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.CommodityContract
import net.corda.finance.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.finance.contracts.asset.DUMMY_OBLIGATION_ISSUER
import net.corda.testing.*
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.time.Instant.now
import java.util.*

@JvmOverloads
fun ServiceHub.fillWithSomeTestDeals(dealIds: List<String>,
                                     issuerServices: ServiceHub = this,
                                     participants: List<AbstractParty> = emptyList(),
                                     notary: Party = DUMMY_NOTARY): Vault<DealState> {
    val myKey: PublicKey = myInfo.chooseIdentity().owningKey
    val me = AnonymousParty(myKey)

    val transactions: List<SignedTransaction> = dealIds.map {
        // Issue a deal state
        val dummyIssue = TransactionBuilder(notary = notary).apply {
            addOutputState(DummyDealContract.State(ref = it, participants = participants.plus(me)), DUMMY_DEAL_PROGRAM_ID)
            addCommand(dummyCommand())
        }
        val stx = issuerServices.signInitialTransaction(dummyIssue)
        return@map addSignature(stx, notary.owningKey)
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
                                            linearTimestamp: Instant = now()): Vault<LinearState> {
    val myKey: PublicKey = myInfo.chooseIdentity().owningKey
    val me = AnonymousParty(myKey)
    val issuerKey = DUMMY_NOTARY_KEY
    val signatureMetadata = SignatureMetadata(myInfo.platformVersion, Crypto.findSignatureScheme(issuerKey.public).schemeNumberID)

    val transactions: List<SignedTransaction> = (1..numberToCreate).map {
        // Issue a Linear state
        val dummyIssue = TransactionBuilder(notary = DUMMY_NOTARY).apply {
            addOutputState(DummyLinearContract.State(
                    linearId = UniqueIdentifier(externalId),
                    participants = participants.plus(me),
                    linearString = linearString,
                    linearNumber = linearNumber,
                    linearBoolean = linearBoolean,
                    linearTimestamp = linearTimestamp), DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
            addCommand(dummyCommand())
        }

        return@map signInitialTransaction(dummyIssue).withAdditionalSignature(issuerKey, signatureMetadata)
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
 * @param issuerServices service hub of the issuer node, which will be used to sign the transaction.
 * @param outputNotary the notary to use for output states. The transaction is NOT signed by this notary.
 * @return a vault object that represents the generated states (it will NOT be the full vault from the service hub!).
 */
fun ServiceHub.fillWithSomeTestCash(howMuch: Amount<Currency>,
                                    issuerServices: ServiceHub = this,
                                    outputNotary: Party = DUMMY_NOTARY,
                                    atLeastThisManyStates: Int = 3,
                                    atMostThisManyStates: Int = 10,
                                    rng: Random = Random(),
                                    ownedBy: AbstractParty? = null,
                                    issuedBy: PartyAndReference = DUMMY_CASH_ISSUER): Vault<Cash.State> {
    val amounts = calculateRandomlySizedAmounts(howMuch, atLeastThisManyStates, atMostThisManyStates, rng)

    val myKey = ownedBy?.owningKey ?: myInfo.chooseIdentity().owningKey
    val anonParty = AnonymousParty(myKey)

    // We will allocate one state to one transaction, for simplicities sake.
    val cash = Cash()
    val transactions: List<SignedTransaction> = amounts.map { pennies ->
        val issuance = TransactionBuilder(null as Party?)
        cash.generateIssue(issuance, Amount(pennies, Issued(issuedBy, howMuch.token)), anonParty, outputNotary)

        return@map issuerServices.signInitialTransaction(issuance, issuedBy.party.owningKey)
    }

    recordTransactions(transactions)

    // Get all the StateRefs of all the generated transactions.
    val states = transactions.flatMap { stx ->
        stx.tx.outputs.indices.map { i -> stx.tx.outRef<Cash.State>(i) }
    }

    return Vault(states)
}

/**
 *
 * @param issuerServices service hub of the issuer node, which will be used to sign the transaction.
 * @param outputNotary the notary to use for output states. The transaction is NOT signed by this notary.
 * @return a vault object that represents the generated states (it will NOT be the full vault from the service hub!).
 */
// TODO: need to make all FungibleAsset commands (issue, move, exit) generic
fun ServiceHub.fillWithSomeTestCommodity(amount: Amount<Commodity>,
                                         issuerServices: ServiceHub = this,
                                         outputNotary: Party = DUMMY_NOTARY,
                                         ref: OpaqueBytes = OpaqueBytes(ByteArray(1, { 1 })),
                                         ownedBy: AbstractParty? = null,
                                         issuedBy: PartyAndReference = DUMMY_OBLIGATION_ISSUER.ref(1)): Vault<CommodityContract.State> {
    val myKey: PublicKey = ownedBy?.owningKey ?: myInfo.chooseIdentity().owningKey
    val me = AnonymousParty(myKey)

    val commodity = CommodityContract()
    val issuance = TransactionBuilder(null as Party?)
    commodity.generateIssue(issuance, Amount(amount.quantity, Issued(issuedBy.copy(reference = ref), amount.token)), me, outputNotary)
    val transaction = issuerServices.signInitialTransaction(issuance, issuedBy.party.owningKey)

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

fun <T : LinearState> ServiceHub.consume(states: List<StateAndRef<T>>, notary: Party) {
    // Create a txn consuming different contract types
    states.forEach {
        val builder = TransactionBuilder(notary = notary).apply {
            addInputState(it)
            addCommand(dummyCommand(notary.owningKey))
        }
        val consumedTx = signInitialTransaction(builder, notary.owningKey)

        recordTransactions(consumedTx)
    }
}

fun <T : LinearState> ServiceHub.consumeAndProduce(stateAndRef: StateAndRef<T>, notary: Party): StateAndRef<T> {
    // Create a txn consuming different contract types
    var builder = TransactionBuilder(notary = notary).apply {
        addInputState(stateAndRef)
        addCommand(dummyCommand(notary.owningKey))
    }
    val consumedTx = signInitialTransaction(builder, notary.owningKey)

    recordTransactions(consumedTx)

    // Create a txn consuming different contract types
    builder = TransactionBuilder(notary = notary).apply {
        addOutputState(DummyLinearContract.State(linearId = stateAndRef.state.data.linearId,
                participants = stateAndRef.state.data.participants), DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
        addCommand(dummyCommand(notary.owningKey))
    }
    val producedTx = signInitialTransaction(builder, notary.owningKey)

    recordTransactions(producedTx)

    return producedTx.tx.outRef<T>(0)
}

fun <T : LinearState> ServiceHub.consumeAndProduce(states: List<StateAndRef<T>>, notary: Party) {
    states.forEach {
        consumeAndProduce(it, notary)
    }
}

fun ServiceHub.consumeDeals(dealStates: List<StateAndRef<DealState>>, notary: Party) = consume(dealStates, notary)
fun ServiceHub.consumeLinearStates(linearStates: List<StateAndRef<LinearState>>, notary: Party) = consume(linearStates, notary)
fun ServiceHub.evolveLinearStates(linearStates: List<StateAndRef<LinearState>>, notary: Party) = consumeAndProduce(linearStates, notary)
fun ServiceHub.evolveLinearState(linearState: StateAndRef<LinearState>, notary: Party): StateAndRef<LinearState> = consumeAndProduce(linearState, notary)

/**
 * Consume cash, sending any change to the default identity for this node. Only suitable for use in test scenarios,
 * where nodes have a default identity.
 */
@JvmOverloads
fun ServiceHub.consumeCash(amount: Amount<Currency>, to: Party = CHARLIE, notary: Party): Vault.Update<ContractState> {
    return consumeCash(amount, myInfo.chooseIdentityAndCert(), to, notary)
}

/**
 * Consume cash, sending any change to the specified identity.
 */
@JvmOverloads
fun ServiceHub.consumeCash(amount: Amount<Currency>, ourIdentity: PartyAndCertificate, to: Party = CHARLIE, notary: Party): Vault.Update<ContractState> {
    val update = vaultService.rawUpdates.toFuture()
    val services = this

    // A tx that spends our money.
    val builder = TransactionBuilder(notary).apply {
        Cash.generateSpend(services, this, amount, ourIdentity, to)
    }
    val spendTx = signInitialTransaction(builder, notary.owningKey)

    recordTransactions(spendTx)

    return update.getOrThrow(Duration.ofSeconds(3))
}
