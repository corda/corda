package net.corda.contracts.asset

import net.corda.contracts.clause.AbstractConserveAmount
import net.corda.contracts.clause.AbstractIssue
import net.corda.contracts.clause.NoZeroSizedOutputs
import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.AllOf
import net.corda.core.contracts.clauses.FirstOf
import net.corda.core.contracts.clauses.GroupClauseVerifier
import net.corda.core.contracts.clauses.verifyClause
import net.corda.core.crypto.*
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.Emoji
import net.corda.schemas.CashSchemaV1
import java.math.BigInteger
import java.util.*

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Cash
//

// Just a fake program identifier for now. In a real system it could be, for instance, the hash of the program bytecode.
val CASH_PROGRAM_ID = Cash()

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
    /**
     * TODO:
     * 1) hash should be of the contents, not the URI
     * 2) allow the content to be specified at time of instance creation?
     *
     * Motivation: it's the difference between a state object referencing a programRef, which references a
     * legalContractReference and a state object which directly references both.  The latter allows the legal wording
     * to evolve without requiring code changes. But creates a risk that users create objects governed by a program
     * that is inconsistent with the legal contract.
     */
    override val legalContractReference: SecureHash = SecureHash.sha256("https://www.big-book-of-banking-law.gov/cash-claims.html")
    override val conserveClause: AbstractConserveAmount<State, Commands, Currency> = Clauses.ConserveAmount()
    override fun extractCommands(commands: Collection<AuthenticatedObject<CommandData>>): List<AuthenticatedObject<Cash.Commands>>
            = commands.select<Cash.Commands>()

    interface Clauses {
        class Group : GroupClauseVerifier<State, Commands, Issued<Currency>>(AllOf<State, Commands, Issued<Currency>>(
                NoZeroSizedOutputs<State, Commands, Currency>(),
                FirstOf<State, Commands, Issued<Currency>>(
                        Issue(),
                        ConserveAmount())
        )
        ) {
            override fun groupStates(tx: TransactionForContract): List<TransactionForContract.InOutGroup<State, Issued<Currency>>>
                    = tx.groupStates<State, Issued<Currency>> { it.amount.token }
        }

        class Issue : AbstractIssue<State, Commands, Currency>(
                sum = { sumCash() },
                sumOrZero = { sumCashOrZero(it) }
        ) {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Issue::class.java)
        }

        class ConserveAmount : AbstractConserveAmount<State, Commands, Currency>()
    }

    /** A state representing a cash claim against some party. */
    data class State(
            override val amount: Amount<Issued<Currency>>,

            /** There must be a MoveCommand signed by this key to claim the amount. */
            override val owner: CompositeKey
    ) : FungibleAsset<Currency>, QueryableState {
        constructor(deposit: PartyAndReference, amount: Amount<Currency>, owner: CompositeKey)
                : this(Amount(amount.quantity, Issued(deposit, amount.token)), owner)

        override val exitKeys = setOf(owner, amount.token.issuer.party.owningKey)
        override val contract = CASH_PROGRAM_ID
        override val participants = listOf(owner)

        override fun move(newAmount: Amount<Issued<Currency>>, newOwner: CompositeKey): FungibleAsset<Currency>
                = copy(amount = amount.copy(newAmount.quantity, amount.token), owner = newOwner)

        override fun toString() = "${Emoji.bagOfCash}Cash($amount at ${amount.token.issuer} owned by $owner)"

        override fun withNewOwner(newOwner: CompositeKey) = Pair(Commands.Move(), copy(owner = newOwner))

        /** Object Relational Mapping support. */
        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is CashSchemaV1 -> CashSchemaV1.PersistentCashState(
                        owner = this.owner.toBase58String(),
                        pennies = this.amount.quantity,
                        currency = this.amount.token.product.currencyCode,
                        issuerParty = this.amount.token.issuer.party.owningKey.toBase58String(),
                        issuerRef = this.amount.token.issuer.reference.bytes
                )
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }

        /** Object Relational Mapping support. */
        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(CashSchemaV1)
    }

    // Just for grouping
    interface Commands : FungibleAsset.Commands {
        /**
         * A command stating that money has been moved, optionally to fulfil another contract.
         *
         * @param contractHash the contract this move is for the attention of. Only that contract's verify function
         * should take the moved states into account when considering whether it is valid. Typically this will be
         * null.
         */
        data class Move(override val contractHash: SecureHash? = null) : FungibleAsset.Commands.Move, Commands

        /**
         * Allows new cash states to be issued into existence: the nonce ("number used once") ensures the transaction
         * has a unique ID even when there are no inputs.
         */
        data class Issue(override val nonce: Long = newSecureRandom().nextLong()) : FungibleAsset.Commands.Issue, Commands

        /**
         * A command stating that money has been withdrawn from the shared ledger and is now accounted for
         * in some other way.
         */
        data class Exit(override val amount: Amount<Issued<Currency>>) : Commands, FungibleAsset.Commands.Exit<Currency>
    }

    /**
     * Puts together an issuance transaction from the given template, that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder, tokenDef: Issued<Currency>, pennies: Long, owner: CompositeKey, notary: Party)
            = generateIssue(tx, Amount(pennies, tokenDef), owner, notary)

    /**
     * Puts together an issuance transaction for the specified amount that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder, amount: Amount<Issued<Currency>>, owner: CompositeKey, notary: Party) {
        check(tx.inputStates().isEmpty())
        check(tx.outputStates().map { it.data }.sumCashOrNull() == null)
        val at = amount.token.issuer
        tx.addOutputState(TransactionState(State(amount, owner), notary))
        tx.addCommand(generateIssueCommand(), at.party.owningKey)
    }

    override fun deriveState(txState: TransactionState<State>, amount: Amount<Issued<Currency>>, owner: CompositeKey)
            = txState.copy(data = txState.data.copy(amount = amount, owner = owner))

    override fun generateExitCommand(amount: Amount<Issued<Currency>>) = Commands.Exit(amount)
    override fun generateIssueCommand() = Commands.Issue()
    override fun generateMoveCommand() = Commands.Move()

    override fun verify(tx: TransactionForContract)
            = verifyClause(tx, Clauses.Group(), extractCommands(tx.commands))
}

// Small DSL extensions.

/**
 * Sums the cash states in the list belonging to a single owner, throwing an exception
 * if there are none, or if any of the cash states cannot be added together (i.e. are
 * different currencies or issuers).
 */
fun Iterable<ContractState>.sumCashBy(owner: CompositeKey): Amount<Issued<Currency>> = filterIsInstance<Cash.State>().filter { it.owner == owner }.map { it.amount }.sumOrThrow()

/**
 * Sums the cash states in the list, throwing an exception if there are none, or if any of the cash
 * states cannot be added together (i.e. are different currencies or issuers).
 */
fun Iterable<ContractState>.sumCash(): Amount<Issued<Currency>> = filterIsInstance<Cash.State>().map { it.amount }.sumOrThrow()

/** Sums the cash states in the list, returning null if there are none. */
fun Iterable<ContractState>.sumCashOrNull(): Amount<Issued<Currency>>? = filterIsInstance<Cash.State>().map { it.amount }.sumOrNull()

/** Sums the cash states in the list, returning zero of the given currency+issuer if there are none. */
fun Iterable<ContractState>.sumCashOrZero(currency: Issued<Currency>): Amount<Issued<Currency>> {
    return filterIsInstance<Cash.State>().map { it.amount }.sumOrZero(currency)
}

fun Cash.State.ownedBy(owner: CompositeKey) = copy(owner = owner)
fun Cash.State.issuedBy(party: AnonymousParty) = copy(amount = Amount(amount.quantity, amount.token.copy(issuer = amount.token.issuer.copy(party = party))))
fun Cash.State.issuedBy(deposit: PartyAndReference) = copy(amount = Amount(amount.quantity, amount.token.copy(issuer = deposit)))
fun Cash.State.withDeposit(deposit: PartyAndReference): Cash.State = copy(amount = amount.copy(token = amount.token.copy(issuer = deposit)))

infix fun Cash.State.`owned by`(owner: CompositeKey) = ownedBy(owner)
infix fun Cash.State.`issued by`(party: AnonymousParty) = issuedBy(party)
infix fun Cash.State.`issued by`(deposit: PartyAndReference) = issuedBy(deposit)
infix fun Cash.State.`with deposit`(deposit: PartyAndReference): Cash.State = withDeposit(deposit)

// Unit testing helpers. These could go in a separate file but it's hardly worth it for just a few functions.

/** A randomly generated key. */
val DUMMY_CASH_ISSUER_KEY by lazy { entropyToKeyPair(BigInteger.valueOf(10)) }
/** A dummy, randomly generated issuer party by the name of "Snake Oil Issuer" */
val DUMMY_CASH_ISSUER by lazy { Party("Snake Oil Issuer", DUMMY_CASH_ISSUER_KEY.public.composite).ref(1) }
/** An extension property that lets you write 100.DOLLARS.CASH */
val Amount<Currency>.CASH: Cash.State get() = Cash.State(Amount(quantity, Issued(DUMMY_CASH_ISSUER, token)), NullCompositeKey)
/** An extension property that lets you get a cash state from an issued token, under the [NullPublicKey] */
val Amount<Issued<Currency>>.STATE: Cash.State get() = Cash.State(this, NullCompositeKey)
