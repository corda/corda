/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

// So the static extension functions get put into a class with a better name than CashKt
package com.r3.corda.enterprise.perftestcordapp.contracts.asset

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.contracts.Amount.Companion.sumOrThrow
import net.corda.core.crypto.NullKeys.NULL_PARTY
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.Emoji
import net.corda.core.node.ServiceHub
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.toBase58String
import com.r3.corda.enterprise.perftestcordapp.schemas.CashSchemaV1
import com.r3.corda.enterprise.perftestcordapp.utils.sumCash
import com.r3.corda.enterprise.perftestcordapp.utils.sumCashOrNull
import com.r3.corda.enterprise.perftestcordapp.utils.sumCashOrZero
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.cash.selection.AbstractCashSelection
import net.corda.core.crypto.toStringShort
import java.math.BigInteger
import java.security.PublicKey
import java.util.*

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Cash
//

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
class Cash : OnLedgerAsset<Currency, Cash.Commands, Cash.State>() {
    override fun extractCommands(commands: Collection<CommandWithParties<CommandData>>): List<CommandWithParties<Cash.Commands>>
            = commands.select<Cash.Commands>()

    // DOCSTART 1
    /** A state representing a cash claim against some party. */
    data class State(
            override val amount: Amount<Issued<Currency>>,

            /** There must be a MoveCommand signed by this key to claim the amount. */
            override val owner: AbstractParty
    ) : FungibleAsset<Currency>, QueryableState {
        constructor(deposit: PartyAndReference, amount: Amount<Currency>, owner: AbstractParty)
                : this(Amount(amount.quantity, Issued(deposit, amount.token)), owner)

        override val exitKeys = setOf(owner.owningKey, amount.token.issuer.party.owningKey)
        override val participants = listOf(owner)

        override fun withNewOwnerAndAmount(newAmount: Amount<Issued<Currency>>, newOwner: AbstractParty): FungibleAsset<Currency>
                = copy(amount = amount.copy(newAmount.quantity), owner = newOwner)

        override fun toString() = "${Emoji.bagOfCash}Cash($amount at ${amount.token.issuer} owned by $owner)"

        override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(Commands.Move(), copy(owner = newOwner))
        infix fun ownedBy(owner: AbstractParty) = copy(owner = owner)
        infix fun issuedBy(party: AbstractParty) = copy(amount = Amount(amount.quantity, amount.token.copy(issuer = amount.token.issuer.copy(party = party))))
        infix fun issuedBy(deposit: PartyAndReference) = copy(amount = Amount(amount.quantity, amount.token.copy(issuer = deposit)))
        infix fun withDeposit(deposit: PartyAndReference): Cash.State = copy(amount = amount.copy(token = amount.token.copy(issuer = deposit)))

        /** Object Relational Mapping support. */
        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is CashSchemaV1 -> CashSchemaV1.PersistentCashState(
                        owner = this.owner,
                        pennies = this.amount.quantity,
                        currency = this.amount.token.product.currencyCode,
                        issuerPartyHash = this.amount.token.issuer.party.owningKey.toStringShort(),
                        issuerRef = this.amount.token.issuer.reference.bytes
                )
            /** Additional schema mappings would be added here (eg. CashSchemaV2, CashSchemaV3, ...) */
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }

        /** Object Relational Mapping support. */
        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(CashSchemaV1)
        /** Additional used schemas would be added here (eg. CashSchemaV2, CashSchemaV3, ...) */
    }
    // DOCEND 1

    // Just for grouping
    interface Commands : CommandData {
        /**
         * A command stating that money has been moved, optionally to fulfil another contract.
         *
         * @param contract the contract this move is for the attention of. Only that contract's verify function
         * should take the moved states into account when considering whether it is valid. Typically this will be
         * null.
         */
        data class Move(override val contract: Class<out Contract>? = null) : MoveCommand

        /**
         * Allows new cash states to be issued into existence.
         */
        class Issue : TypeOnlyCommandData()

        /**
         * A command stating that money has been withdrawn from the shared ledger and is now accounted for
         * in some other way.
         */
        data class Exit(val amount: Amount<Issued<Currency>>) : CommandData
    }

    /**
     * Puts together an issuance transaction from the given template, that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder, tokenDef: Issued<Currency>, pennies: Long, owner: AbstractParty, notary: Party)
            = generateIssue(tx, Amount(pennies, tokenDef), owner, notary)

    /**
     * Puts together an issuance transaction for the specified amount that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder, amount: Amount<Issued<Currency>>, owner: AbstractParty, notary: Party)
            = generateIssue(tx, TransactionState(State(amount, owner), PROGRAM_ID, notary), Commands.Issue())

    override fun deriveState(txState: TransactionState<State>, amount: Amount<Issued<Currency>>, owner: AbstractParty)
            = txState.copy(data = txState.data.copy(amount = amount, owner = owner))

    override fun generateExitCommand(amount: Amount<Issued<Currency>>) = Commands.Exit(amount)
    override fun generateMoveCommand() = Commands.Move()

    override fun verify(tx: LedgerTransaction) {
        // Each group is a set of input/output states with distinct (reference, currency) attributes. These types
        // of cash are not fungible and must be kept separated for bookkeeping purposes.
        val groups = tx.groupStates { it: Cash.State -> it.amount.token }

        for ((inputs, outputs, key) in groups) {
            // Either inputs or outputs could be empty.
            val issuer = key.issuer
            val currency = key.product

            requireThat {
                "there are no zero sized outputs" using (outputs.none { it.amount.quantity == 0L })
            }

            val issueCommand = tx.commands.select<Commands.Issue>().firstOrNull()
            if (issueCommand != null) {
                verifyIssueCommand(inputs, outputs, tx, issueCommand, currency, issuer)
            } else {
                val inputAmount = inputs.sumCashOrNull() ?: throw IllegalArgumentException("there is at least one cash input for this group")
                val outputAmount = outputs.sumCashOrZero(Issued(issuer, currency))

                // If we want to remove cash from the ledger, that must be signed for by the issuer.
                // A mis-signed or duplicated exit command will just be ignored here and result in the exit amount being zero.
                val exitKeys: Set<PublicKey> = inputs.flatMap { it.exitKeys }.toSet()
                val exitCommand = tx.commands.select<Commands.Exit>(parties = null, signers = exitKeys).filter { it.value.amount.token == key }.singleOrNull()
                val amountExitingLedger = exitCommand?.value?.amount ?: Amount(0, Issued(issuer, currency))

                requireThat {
                    "there are no zero sized inputs" using inputs.none { it.amount.quantity == 0L }
                    "for reference ${issuer.reference} at issuer ${issuer.party} the amounts balance: ${inputAmount.quantity} - ${amountExitingLedger.quantity} != ${outputAmount.quantity}" using
                            (inputAmount == outputAmount + amountExitingLedger)
                }

                verifyMoveCommand<Commands.Move>(inputs, tx.commands)
            }
        }
    }

    private fun verifyIssueCommand(inputs: List<State>,
                                   outputs: List<State>,
                                   tx: LedgerTransaction,
                                   issueCommand: CommandWithParties<Commands.Issue>,
                                   currency: Currency,
                                   issuer: PartyAndReference) {
        // If we have an issue command, perform special processing: the group is allowed to have no inputs,
        // and the output states must have a deposit reference owned by the signer.
        //
        // Whilst the transaction *may* have no inputs, it can have them, and in this case the outputs must
        // sum to more than the inputs. An issuance of zero size is not allowed.
        //
        // Note that this means literally anyone with access to the network can issue cash claims of arbitrary
        // amounts! It is up to the recipient to decide if the backing party is trustworthy or not, via some
        // as-yet-unwritten identity service. See ADP-22 for discussion.

        // The grouping ensures that all outputs have the same deposit reference and currency.
        val inputAmount = inputs.sumCashOrZero(Issued(issuer, currency))
        val outputAmount = outputs.sumCash()
        val cashCommands = tx.commands.select<Commands.Issue>()
        requireThat {
            // TODO: This doesn't work with the trader demo, so use the underlying key instead
            // "output states are issued by a command signer" by (issuer.party in issueCommand.signingParties)
            "output states are issued by a command signer" using (issuer.party.owningKey in issueCommand.signers)
            "output values sum to more than the inputs" using (outputAmount > inputAmount)
            "there is only a single issue command" using (cashCommands.count() == 1)
        }
    }

    companion object {
        const val PROGRAM_ID: ContractClassName = "com.r3.corda.enterprise.perftestcordapp.contracts.asset.Cash"

        /**
         * Generate a transaction that moves an amount of currency to the given party, and sends any change back to
         * sole identity of the calling node. Fails for nodes with multiple identities.
         *
         * Note: an [Amount] of [Currency] is only fungible for a given Issuer Party within a [FungibleAsset]
         *
         * @param services The [ServiceHub] to provide access to the database session.
         * @param tx A builder, which may contain inputs, outputs and commands already. The relevant components needed
         *           to move the cash will be added on top.
         * @param amount How much currency to send.
         * @param to the recipient party.
         * @param onlyFromParties if non-null, the asset states will be filtered to only include those issued by the set
         *                        of given parties. This can be useful if the party you're trying to pay has expectations
         *                        about which type of asset claims they are willing to accept.
         * @return A [Pair] of the same transaction builder passed in as [tx], and the list of keys that need to sign
         *         the resulting transaction for it to be valid.
         * @throws InsufficientBalanceException when a cash spending transaction fails because
         *         there is insufficient quantity for a given currency (and optionally set of Issuer Parties).
         */
        @JvmStatic
        @Throws(InsufficientBalanceException::class)
        @Suspendable
        @Deprecated("Our identity should be specified", replaceWith = ReplaceWith("generateSpend(services, tx, amount, to, ourIdentity, onlyFromParties)"))
        fun generateSpend(services: ServiceHub,
                          tx: TransactionBuilder,
                          amount: Amount<Currency>,
                          to: AbstractParty,
                          onlyFromParties: Set<AbstractParty> = emptySet()) = generateSpend(services, tx, listOf(PartyAndAmount(to, amount)), services.myInfo.legalIdentitiesAndCerts.single(), onlyFromParties)

        /**
         * Generate a transaction that moves an amount of currency to the given party.
         *
         * Note: an [Amount] of [Currency] is only fungible for a given Issuer Party within a [FungibleAsset]
         *
         * @param services The [ServiceHub] to provide access to the database session.
         * @param tx A builder, which may contain inputs, outputs and commands already. The relevant components needed
         *           to move the cash will be added on top.
         * @param amount How much currency to send.
         * @param to the recipient party.
         * @param ourIdentity well known identity to create a new confidential identity from, for sending change to.
         * @param onlyFromParties if non-null, the asset states will be filtered to only include those issued by the set
         *                        of given parties. This can be useful if the party you're trying to pay has expectations
         *                        about which type of asset claims they are willing to accept.
         * @return A [Pair] of the same transaction builder passed in as [tx], and the list of keys that need to sign
         *         the resulting transaction for it to be valid.
         * @throws InsufficientBalanceException when a cash spending transaction fails because
         *         there is insufficient quantity for a given currency (and optionally set of Issuer Parties).
         */
        @JvmStatic
        @Throws(InsufficientBalanceException::class)
        @Suspendable
        fun generateSpend(services: ServiceHub,
                          tx: TransactionBuilder,
                          amount: Amount<Currency>,
                          ourIdentity: PartyAndCertificate,
                          to: AbstractParty,
                          onlyFromParties: Set<AbstractParty> = emptySet()): Pair<TransactionBuilder, List<PublicKey>> {
            return generateSpend(services, tx, listOf(PartyAndAmount(to, amount)), ourIdentity, onlyFromParties)
        }

        /**
         * Generate a transaction that moves money of the given amounts to the recipients specified, and sends any change
         * back to sole identity of the calling node. Fails for nodes with multiple identities.
         *
         * Note: an [Amount] of [Currency] is only fungible for a given Issuer Party within a [FungibleAsset]
         *
         * @param services The [ServiceHub] to provide access to the database session.
         * @param tx A builder, which may contain inputs, outputs and commands already. The relevant components needed
         *           to move the cash will be added on top.
         * @param payments A list of amounts to pay, and the party to send the payment to.
         * @param onlyFromParties if non-null, the asset states will be filtered to only include those issued by the set
         *                        of given parties. This can be useful if the party you're trying to pay has expectations
         *                        about which type of asset claims they are willing to accept.
         * @return A [Pair] of the same transaction builder passed in as [tx], and the list of keys that need to sign
         *         the resulting transaction for it to be valid.
         * @throws InsufficientBalanceException when a cash spending transaction fails because
         *         there is insufficient quantity for a given currency (and optionally set of Issuer Parties).
         */
        @JvmStatic
        @Throws(InsufficientBalanceException::class)
        @Suspendable
        @Deprecated("Our identity should be specified", replaceWith = ReplaceWith("generateSpend(services, tx, amount, to, ourIdentity, onlyFromParties)"))
        fun generateSpend(services: ServiceHub,
                          tx: TransactionBuilder,
                          payments: List<PartyAndAmount<Currency>>,
                          onlyFromParties: Set<AbstractParty> = emptySet()) = generateSpend(services, tx, payments, services.myInfo.legalIdentitiesAndCerts.single(), onlyFromParties)

        /**
         * Generate a transaction that moves money of the given amounts to the recipients specified.
         *
         * Note: an [Amount] of [Currency] is only fungible for a given Issuer Party within a [FungibleAsset]
         *
         * @param services The [ServiceHub] to provide access to the database session.
         * @param tx A builder, which may contain inputs, outputs and commands already. The relevant components needed
         *           to move the cash will be added on top.
         * @param payments A list of amounts to pay, and the party to send the payment to.
         * @param ourIdentity well known identity to create a new confidential identity from, for sending change to.
         * @param onlyFromParties if non-null, the asset states will be filtered to only include those issued by the set
         *                        of given parties. This can be useful if the party you're trying to pay has expectations
         *                        about which type of asset claims they are willing to accept.
         * @return A [Pair] of the same transaction builder passed in as [tx], and the list of keys that need to sign
         *         the resulting transaction for it to be valid.
         * @throws InsufficientBalanceException when a cash spending transaction fails because
         *         there is insufficient quantity for a given currency (and optionally set of Issuer Parties).
         */
        @JvmStatic
        @Throws(InsufficientBalanceException::class)
        @Suspendable
        fun generateSpend(services: ServiceHub,
                          tx: TransactionBuilder,
                          payments: List<PartyAndAmount<Currency>>,
                          ourIdentity: PartyAndCertificate,
                          onlyFromParties: Set<AbstractParty> = emptySet()): Pair<TransactionBuilder, List<PublicKey>> {
            fun deriveState(txState: TransactionState<Cash.State>, amt: Amount<Issued<Currency>>, owner: AbstractParty)
                    = txState.copy(data = txState.data.copy(amount = amt, owner = owner))

            // Retrieve unspent and unlocked cash states that meet our spending criteria.
            val totalAmount = payments.map { it.amount }.sumOrThrow()
            val cashSelection = AbstractCashSelection.getInstance({ services.jdbcSession().metaData })
            val acceptableCoins = cashSelection.unconsumedCashStatesForSpending(services, totalAmount, onlyFromParties, tx.notary, tx.lockId)
            val revocationEnabled = false // Revocation is currently unsupported
            // Generate a new identity that change will be sent to for confidentiality purposes. This means that a
            // third party with a copy of the transaction (such as the notary) cannot identify who the change was
            // sent to
            val changeIdentity = services.keyManagementService.freshKeyAndCert(ourIdentity, revocationEnabled)
            return OnLedgerAsset.generateSpend(tx, payments, acceptableCoins,
                    changeIdentity.party.anonymise(),
                    { state, quantity, owner -> deriveState(state, quantity, owner) },
                    { Cash().generateMoveCommand() })
        }
    }
}

// Unit testing helpers. These could go in a separate file but it's hardly worth it for just a few functions.

/** A dummy, randomly generated issuer party by the name of "Snake Oil Issuer" */
val DUMMY_CASH_ISSUER_NAME = CordaX500Name(organisation = "Snake Oil Issuer", locality = "London", country = "GB")
/** A randomly generated key. */
val DUMMY_CASH_ISSUER_KEY by lazy { entropyToKeyPair(BigInteger.valueOf(10)) }
/** A dummy, randomly generated issuer party by the name of "Snake Oil Issuer" */
val DUMMY_CASH_ISSUER by lazy { Party(DUMMY_CASH_ISSUER_NAME, DUMMY_CASH_ISSUER_KEY.public).ref(1) }
/** An extension property that lets you write 100.DOLLARS.CASH */
val Amount<Currency>.CASH: Cash.State get() = Cash.State(Amount(quantity, Issued(DUMMY_CASH_ISSUER, token)), NULL_PARTY)
/** An extension property that lets you get a cash state from an issued token, under the [NULL_PARTY] */
val Amount<Issued<Currency>>.STATE: Cash.State get() = Cash.State(this, NULL_PARTY)
