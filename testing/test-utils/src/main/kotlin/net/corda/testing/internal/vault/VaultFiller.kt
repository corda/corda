/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.internal.vault

import net.corda.core.contracts.*
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.finance.contracts.Commodity
import net.corda.finance.contracts.DealState
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.Obligation
import net.corda.finance.contracts.asset.OnLedgerAsset
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.*
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.internal.chooseIdentityAndCert
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.time.Instant.now
import java.util.*

/**
 * The service hub should provide at least a key management service and a storage service.
 * @param defaultNotary used in [fillWithSomeTestDeals] and [fillWithSomeTestLinearStates].
 * @param altNotary used in [fillWithSomeTestCash], [fillWithSomeTestCommodity] and consume/evolve methods. If not specified, same as [defaultNotary].
 * @param rngFactory used by [fillWithSomeTestCash] if no custom [Random] provided.
 */
class VaultFiller @JvmOverloads constructor(
        private val services: ServiceHub,
        private val defaultNotary: TestIdentity,
        private val altNotary: Party = defaultNotary.party,
        private val rngFactory: () -> Random = { Random(0L) }) {
    companion object {
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
    }

    @JvmOverloads
    fun fillWithSomeTestDeals(dealIds: List<String>,
                              issuerServices: ServiceHub = services,
                              participants: List<AbstractParty> = emptyList()): Vault<DealState> {
        val myKey: PublicKey = services.myInfo.chooseIdentity().owningKey
        val me = AnonymousParty(myKey)

        val transactions: List<SignedTransaction> = dealIds.map {
            // Issue a deal state
            val dummyIssue = TransactionBuilder(notary = defaultNotary.party).apply {
                addOutputState(DummyDealContract.State(ref = it, participants = participants.plus(me)), DUMMY_DEAL_PROGRAM_ID)
                addCommand(dummyCommand())
            }
            val stx = issuerServices.signInitialTransaction(dummyIssue)
            return@map services.addSignature(stx, defaultNotary.publicKey)
        }
        services.recordTransactions(transactions)
        // Get all the StateAndRefs of all the generated transactions.
        val states = transactions.flatMap { stx ->
            stx.tx.outputs.indices.map { i -> stx.tx.outRef<DealState>(i) }
        }

        return Vault(states)
    }

    @JvmOverloads
    fun fillWithSomeTestLinearStates(numberToCreate: Int,
                                     externalId: String? = null,
                                     participants: List<AbstractParty> = emptyList(),
                                     uniqueIdentifier: UniqueIdentifier? = null,
                                     linearString: String = "",
                                     linearNumber: Long = 0L,
                                     linearBoolean: Boolean = false,
                                     linearTimestamp: Instant = now()): Vault<LinearState> {
        val myKey: PublicKey = services.myInfo.chooseIdentity().owningKey
        val me = AnonymousParty(myKey)
        val issuerKey = defaultNotary.keyPair
        val signatureMetadata = SignatureMetadata(services.myInfo.platformVersion, Crypto.findSignatureScheme(issuerKey.public).schemeNumberID)
        val transactions: List<SignedTransaction> = (1..numberToCreate).map {
            // Issue a Linear state
            val dummyIssue = TransactionBuilder(notary = defaultNotary.party).apply {
                addOutputState(DummyLinearContract.State(
                        linearId = uniqueIdentifier ?: UniqueIdentifier(externalId),
                        participants = participants.plus(me),
                        linearString = linearString,
                        linearNumber = linearNumber,
                        linearBoolean = linearBoolean,
                        linearTimestamp = linearTimestamp), DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
                addCommand(dummyCommand())
            }
            return@map services.signInitialTransaction(dummyIssue).withAdditionalSignature(issuerKey, signatureMetadata)
        }
        services.recordTransactions(transactions)
        // Get all the StateAndRefs of all the generated transactions.
        val states = transactions.flatMap { stx ->
            stx.tx.outputs.indices.map { i -> stx.tx.outRef<LinearState>(i) }
        }

        return Vault(states)
    }

    @JvmOverloads
    fun fillWithSomeTestLinearAndDealStates(numberToCreate: Int,
                                     externalId: String? = null,
                                     participants: List<AbstractParty> = emptyList(),
                                     linearString: String = "",
                                     linearNumber: Long = 0L,
                                     linearBoolean: Boolean = false,
                                     linearTimestamp: Instant = now()): Vault<LinearState> {
        val myKey: PublicKey = services.myInfo.chooseIdentity().owningKey
        val me = AnonymousParty(myKey)
        val issuerKey = defaultNotary.keyPair
        val signatureMetadata = SignatureMetadata(services.myInfo.platformVersion, Crypto.findSignatureScheme(issuerKey.public).schemeNumberID)
        val transactions: List<SignedTransaction> = (1..numberToCreate).map {
            val dummyIssue = TransactionBuilder(notary = defaultNotary.party).apply {
                // Issue a Linear state
                addOutputState(DummyLinearContract.State(
                        linearId = UniqueIdentifier(externalId),
                        participants = participants.plus(me),
                        linearString = linearString,
                        linearNumber = linearNumber,
                        linearBoolean = linearBoolean,
                        linearTimestamp = linearTimestamp), DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
                // Issue a Deal state
                addOutputState(DummyDealContract.State(ref = "test ref", participants = participants.plus(me)), DUMMY_DEAL_PROGRAM_ID)
                addCommand(dummyCommand())
            }
            return@map services.signInitialTransaction(dummyIssue).withAdditionalSignature(issuerKey, signatureMetadata)
        }
        services.recordTransactions(transactions)
        // Get all the StateAndRefs of all the generated transactions.
        val states = transactions.flatMap { stx ->
            stx.tx.outputs.indices.map { i -> stx.tx.outRef<LinearState>(i) }
        }
        return Vault(states)
    }

    @JvmOverloads
    fun fillWithSomeTestCash(howMuch: Amount<Currency>,
                             issuerServices: ServiceHub,
                             thisManyStates: Int,
                             issuedBy: PartyAndReference,
                             owner: AbstractParty? = null,
                             rng: Random? = null) = fillWithSomeTestCash(howMuch, issuerServices, thisManyStates, thisManyStates, issuedBy, owner, rng)

    /**
     * Creates a random set of between (by default) 3 and 10 cash states that add up to the given amount and adds them
     * to the vault. This is intended for unit tests. By default the cash is owned by the legal
     * identity key from the storage service.
     *
     * @param issuerServices service hub of the issuer node, which will be used to sign the transaction.
     * @return a vault object that represents the generated states (it will NOT be the full vault from the service hub!).
     */
    fun fillWithSomeTestCash(howMuch: Amount<Currency>,
                             issuerServices: ServiceHub,
                             atLeastThisManyStates: Int,
                             atMostThisManyStates: Int,
                             issuedBy: PartyAndReference,
                             owner: AbstractParty? = null,
                             rng: Random? = null): Vault<Cash.State> {
        val amounts = calculateRandomlySizedAmounts(howMuch, atLeastThisManyStates, atMostThisManyStates, rng ?: rngFactory())
        // We will allocate one state to one transaction, for simplicities sake.
        val cash = Cash()
        val transactions: List<SignedTransaction> = amounts.map { pennies ->
            val issuance = TransactionBuilder(null as Party?)
            cash.generateIssue(issuance, Amount(pennies, Issued(issuedBy, howMuch.token)), owner ?: services.myInfo.singleIdentity(), altNotary)
            return@map issuerServices.signInitialTransaction(issuance, issuedBy.party.owningKey)
        }
        services.recordTransactions(transactions)
        // Get all the StateRefs of all the generated transactions.
        val states = transactions.flatMap { stx ->
            stx.tx.outputs.indices.map { i -> stx.tx.outRef<Cash.State>(i) }
        }

        return Vault(states)
    }

    /**
     * Records a dummy state in the Vault (useful for creating random states when testing vault queries)
     */
    fun fillWithDummyState() : Vault<DummyState> {
        val outputState = TransactionState(
                data = DummyState(Random().nextInt(), participants = listOf(services.myInfo.singleIdentity())),
                contract = DummyContract.PROGRAM_ID,
                notary = defaultNotary.party
        )
        val builder = TransactionBuilder()
                .addOutputState(outputState)
                .addCommand(DummyCommandData, defaultNotary.party.owningKey)
        val stxn = services.signInitialTransaction(builder)
        services.recordTransactions(stxn)
        return Vault(setOf(stxn.tx.outRef(0)))
    }

    /**
     * Puts together an issuance transaction for the specified amount that starts out being owned by the given pubkey.
     */
    fun generateCommoditiesIssue(tx: TransactionBuilder, amount: Amount<Issued<Commodity>>, owner: AbstractParty, notary: Party)
            = OnLedgerAsset.generateIssue(tx, TransactionState(CommodityState(amount, owner), Obligation.PROGRAM_ID, notary), Obligation.Commands.Issue())


    /**
     *
     * @param issuerServices service hub of the issuer node, which will be used to sign the transaction.
     * @return a vault object that represents the generated states (it will NOT be the full vault from the service hub!).
     */
    // TODO: need to make all FungibleAsset commands (issue, move, exit) generic
    fun fillWithSomeTestCommodity(amount: Amount<Commodity>, issuerServices: ServiceHub, issuedBy: PartyAndReference): Vault<CommodityState> {
        val myKey: PublicKey = services.myInfo.chooseIdentity().owningKey
        val me = AnonymousParty(myKey)

        val issuance = TransactionBuilder(null as Party?)
        generateCommoditiesIssue(issuance, Amount(amount.quantity, Issued(issuedBy, amount.token)), me, altNotary)
        val transaction = issuerServices.signInitialTransaction(issuance, issuedBy.party.owningKey)
        services.recordTransactions(transaction)
        return Vault(setOf(transaction.tx.outRef(0)))
    }

    private fun <T : LinearState> consume(states: List<StateAndRef<T>>) {
        // Create a txn consuming different contract types
        states.forEach {
            val builder = TransactionBuilder(notary = altNotary).apply {
                addInputState(it)
                addCommand(dummyCommand(altNotary.owningKey))
            }
            val consumedTx = services.signInitialTransaction(builder, altNotary.owningKey)
            services.recordTransactions(consumedTx)
        }
    }

    private fun <T : LinearState> consumeAndProduce(stateAndRef: StateAndRef<T>): StateAndRef<T> {
        // Create a txn consuming different contract types
        var builder = TransactionBuilder(notary = altNotary).apply {
            addInputState(stateAndRef)
            addCommand(dummyCommand(altNotary.owningKey))
        }
        val consumedTx = services.signInitialTransaction(builder, altNotary.owningKey)
        services.recordTransactions(consumedTx)
        // Create a txn consuming different contract types
        builder = TransactionBuilder(notary = altNotary).apply {
            addOutputState(DummyLinearContract.State(linearId = stateAndRef.state.data.linearId,
                    participants = stateAndRef.state.data.participants), DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
            addCommand(dummyCommand(altNotary.owningKey))
        }
        val producedTx = services.signInitialTransaction(builder, altNotary.owningKey)
        services.recordTransactions(producedTx)
        return producedTx.tx.outRef(0)
    }

    private fun <T : LinearState> consumeAndProduce(states: List<StateAndRef<T>>) {
        states.forEach {
            consumeAndProduce(it)
        }
    }

    fun consumeDeals(dealStates: List<StateAndRef<DealState>>) = consume(dealStates)
    fun consumeLinearStates(linearStates: List<StateAndRef<LinearState>>) = consume(linearStates)
    fun evolveLinearStates(linearStates: List<StateAndRef<LinearState>>) = consumeAndProduce(linearStates)
    fun evolveLinearState(linearState: StateAndRef<LinearState>): StateAndRef<LinearState> = consumeAndProduce(linearState)
    /**
     * Consume cash, sending any change to the default identity for this node. Only suitable for use in test scenarios,
     * where nodes have a default identity.
     */
    fun consumeCash(amount: Amount<Currency>, to: AbstractParty): Vault.Update<ContractState> {
        val ourIdentity = services.myInfo.chooseIdentityAndCert()
        val update = services.vaultService.rawUpdates.toFuture()
        // A tx that spends our money.
        val builder = TransactionBuilder(altNotary).apply {
            Cash.generateSpend(services, this, amount, ourIdentity, to)
        }
        val spendTx = services.signInitialTransaction(builder, altNotary.owningKey)
        services.recordTransactions(spendTx)
        return update.getOrThrow(Duration.ofSeconds(3))
    }
}



/** A state representing a commodity claim against some party */
data class CommodityState(
        override val amount: Amount<Issued<Commodity>>,

        /** There must be a MoveCommand signed by this key to claim the amount */
        override val owner: AbstractParty
) : FungibleAsset<Commodity> {
    constructor(deposit: PartyAndReference, amount: Amount<Commodity>, owner: AbstractParty)
            : this(Amount(amount.quantity, Issued(deposit, amount.token)), owner)

    override val exitKeys: Set<PublicKey> = Collections.singleton(owner.owningKey)
    override val participants = listOf(owner)

    override fun withNewOwnerAndAmount(newAmount: Amount<Issued<Commodity>>, newOwner: AbstractParty): FungibleAsset<Commodity>
            = copy(amount = amount.copy(newAmount.quantity), owner = newOwner)

    override fun toString() = "Commodity($amount at ${amount.token.issuer} owned by $owner)"

    override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(Obligation.Commands.Move(), copy(owner = newOwner))
}

