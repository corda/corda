package net.corda.contracts.asset

import net.corda.contracts.clause.AbstractConserveAmount
import net.corda.contracts.clause.AbstractIssue
import net.corda.contracts.clause.NoZeroSizedOutputs
import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.AnyOf
import net.corda.core.contracts.clauses.GroupClauseVerifier
import net.corda.core.contracts.clauses.verifyClause
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.newSecureRandom
import net.corda.core.transactions.TransactionBuilder
import java.util.*

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Commodity
//

// Just a fake program identifier for now. In a real system it could be, for instance, the hash of the program bytecode.
val COMMODITY_PROGRAM_ID = CommodityContract()

/**
 * A commodity contract represents an amount of some commodity, tracked on a distributed ledger. The design of this
 * contract is intentionally similar to the [Cash] contract, and the same commands (issue, move, exit) apply, the
 * differences are in representation of the underlying commodity. Issuer in this context means the party who has the
 * commodity, or is otherwise responsible for delivering the commodity on demand, and the deposit reference is use for
 * internal accounting by the issuer (it might be, for example, a warehouse and/or location within a warehouse).
 *
 * This is an early stage example contract used to illustrate non-cash fungible assets, and is likely to change significantly
 * in future.
 */
// TODO: Need to think about expiry of commodities, how to require payment of storage costs, etc.
class CommodityContract : OnLedgerAsset<Commodity, CommodityContract.Commands, CommodityContract.State>() {
    /**
     * TODO:
     * 1) hash should be of the contents, not the URI
     * 2) allow the content to be specified at time of instance creation?
     *
     * Motivation: it's the difference between a state object referencing a programRef, which references a
     * legalContractReference and a state object which directly references both.  The latter allows the legal wording
     * to evolve without requiring code changes. But creates a risk that users create objects governed by a program
     * that is inconsistent with the legal contract
     */
    override val legalContractReference: SecureHash = SecureHash.sha256("https://www.big-book-of-banking-law.gov/commodity-claims.html")

    override val conserveClause: AbstractConserveAmount<State, Commands, Commodity> = Clauses.ConserveAmount()

    /**
     * The clauses for this contract are essentially:
     *
     * 1. Group all commodity input and output states in a transaction by issued commodity, and then for each group:
     *  a. Check there are no zero sized output states in the group, and throw an error if so.
     *  b. Check for an issuance command, and do standard issuance checks if so, THEN STOP. Otherwise:
     *  c. Check for a move command (required) and an optional exit command, and that input and output totals are correctly
     *     conserved (output = input - exit)
     */
    interface Clauses {
        /**
         * Grouping clause to extract input and output states into matched groups and then run a set of clauses over
         * each group.
         */
        class Group : GroupClauseVerifier<State, Commands, Issued<Commodity>>(AnyOf(
                NoZeroSizedOutputs<State, Commands, Commodity>(),
                Issue(),
                ConserveAmount())) {
            /**
             * Group commodity states by issuance definition (issuer and underlying commodity).
             */
            override fun groupStates(tx: TransactionForContract)
                    = tx.groupStates<State, Issued<Commodity>> { it.amount.token }
        }

        /**
         * Standard issue clause, specialised to match the commodity issue command.
         */
        class Issue : AbstractIssue<State, Commands, Commodity>(
                sum = { sumCommodities() },
                sumOrZero = { sumCommoditiesOrZero(it) }
        ) {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Issue::class.java)
        }

        /**
         * Standard clause for conserving the amount from input to output.
         */
        class ConserveAmount : AbstractConserveAmount<State, Commands, Commodity>()
    }

    /** A state representing a commodity claim against some party */
    data class State(
            override val amount: Amount<Issued<Commodity>>,

            /** There must be a MoveCommand signed by this key to claim the amount */
            override val owner: CompositeKey
    ) : FungibleAsset<Commodity> {
        constructor(deposit: PartyAndReference, amount: Amount<Commodity>, owner: CompositeKey)
                : this(Amount(amount.quantity, Issued(deposit, amount.token)), owner)

        override val contract = COMMODITY_PROGRAM_ID
        override val exitKeys = Collections.singleton(owner)
        override val participants = listOf(owner)

        override fun move(newAmount: Amount<Issued<Commodity>>, newOwner: CompositeKey): FungibleAsset<Commodity>
                = copy(amount = amount.copy(newAmount.quantity, amount.token), owner = newOwner)

        override fun toString() = "Commodity($amount at ${amount.token.issuer} owned by $owner)"

        override fun withNewOwner(newOwner: CompositeKey) = Pair(Commands.Move(), copy(owner = newOwner))
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
         * Allows new commodity states to be issued into existence: the nonce ("number used once") ensures the transaction
         * has a unique ID even when there are no inputs.
         */
        data class Issue(override val nonce: Long = newSecureRandom().nextLong()) : FungibleAsset.Commands.Issue, Commands

        /**
         * A command stating that money has been withdrawn from the shared ledger and is now accounted for
         * in some other way.
         */
        data class Exit(override val amount: Amount<Issued<Commodity>>) : Commands, FungibleAsset.Commands.Exit<Commodity>
    }

    override fun verify(tx: TransactionForContract)
            = verifyClause(tx, Clauses.Group(), extractCommands(tx.commands))

    override fun extractCommands(commands: Collection<AuthenticatedObject<CommandData>>): List<AuthenticatedObject<Commands>>
            = commands.select<CommodityContract.Commands>()

    /**
     * Puts together an issuance transaction from the given template, that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder, tokenDef: Issued<Commodity>, pennies: Long, owner: CompositeKey, notary: Party.Full)
            = generateIssue(tx, Amount(pennies, tokenDef), owner, notary)

    /**
     * Puts together an issuance transaction for the specified amount that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder, amount: Amount<Issued<Commodity>>, owner: CompositeKey, notary: Party.Full) {
        check(tx.inputStates().isEmpty())
        check(tx.outputStates().map { it.data }.sumCashOrNull() == null)
        val at = amount.token.issuer
        tx.addOutputState(TransactionState(State(amount, owner), notary))
        tx.addCommand(generateIssueCommand(), at.party.owningKey)
    }


    override fun deriveState(txState: TransactionState<State>, amount: Amount<Issued<Commodity>>, owner: CompositeKey)
            = txState.copy(data = txState.data.copy(amount = amount, owner = owner))

    override fun generateExitCommand(amount: Amount<Issued<Commodity>>) = Commands.Exit(amount)
    override fun generateIssueCommand() = Commands.Issue()
    override fun generateMoveCommand() = Commands.Move()
}

/**
 * Sums the cash states in the list, throwing an exception if there are none, or if any of the cash
 * states cannot be added together (i.e. are different currencies).
 */
fun Iterable<ContractState>.sumCommodities() = filterIsInstance<CommodityContract.State>().map { it.amount }.sumOrThrow()

/** Sums the cash states in the list, returning null if there are none. */
@Suppress("unused") fun Iterable<ContractState>.sumCommoditiesOrNull() = filterIsInstance<CommodityContract.State>().map { it.amount }.sumOrNull()

/** Sums the cash states in the list, returning zero of the given currency if there are none. */
fun Iterable<ContractState>.sumCommoditiesOrZero(currency: Issued<Commodity>) = filterIsInstance<CommodityContract.State>().map { it.amount }.sumOrZero(currency)
