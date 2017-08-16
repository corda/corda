package net.corda.contracts.asset

import net.corda.contracts.Commodity
import net.corda.core.contracts.*
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey
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
    /** A state representing a commodity claim against some party */
    data class State(
            override val amount: Amount<Issued<Commodity>>,

            /** There must be a MoveCommand signed by this key to claim the amount */
            override val owner: AbstractParty
    ) : FungibleAsset<Commodity> {
        constructor(deposit: PartyAndReference, amount: Amount<Commodity>, owner: AbstractParty)
                : this(Amount(amount.quantity, Issued(deposit, amount.token)), owner)

        override val contract = COMMODITY_PROGRAM_ID
        override val exitKeys: Set<PublicKey> = Collections.singleton(owner.owningKey)
        override val participants = listOf(owner)

        override fun move(newAmount: Amount<Issued<Commodity>>, newOwner: AbstractParty): FungibleAsset<Commodity>
                = copy(amount = amount.copy(newAmount.quantity), owner = newOwner)

        override fun toString() = "Commodity($amount at ${amount.token.issuer} owned by $owner)"

        override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(Commands.Move(), copy(owner = newOwner))
    }

    // Just for grouping
    @CordaSerializable
    interface Commands : FungibleAsset.Commands {
        /**
         * A command stating that money has been moved, optionally to fulfil another contract.
         *
         * @param contract the contract this move is for the attention of. Only that contract's verify function
         * should take the moved states into account when considering whether it is valid. Typically this will be
         * null.
         */
        data class Move(override val contract: Class<out Contract>? = null) : FungibleAsset.Commands.Move, Commands

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

    override fun verify(tx: LedgerTransaction) {
        // Each group is a set of input/output states with distinct (reference, commodity) attributes. These types
        // of commodity are not fungible and must be kept separated for bookkeeping purposes.
        val groups = tx.groupStates { it: CommodityContract.State -> it.amount.token }

        for ((inputs, outputs, key) in groups) {
            // Either inputs or outputs could be empty.
            val issuer = key.issuer
            val commodity = key.product
            val party = issuer.party

            requireThat {
                "there are no zero sized outputs" using ( outputs.none { it.amount.quantity == 0L } )
            }

            val issueCommand = tx.commands.select<Commands.Issue>().firstOrNull()
            if (issueCommand != null) {
                verifyIssueCommand(inputs, outputs, tx, issueCommand, commodity, issuer)
            } else {
                val inputAmount = inputs.sumCommoditiesOrNull() ?: throw IllegalArgumentException("there is at least one commodity input for this group")
                val outputAmount = outputs.sumCommoditiesOrZero(Issued(issuer, commodity))

                // If we want to remove commodity from the ledger, that must be signed for by the issuer.
                // A mis-signed or duplicated exit command will just be ignored here and result in the exit amount being zero.
                val exitCommand = tx.commands.select<Commands.Exit>(party = party).singleOrNull()
                val amountExitingLedger = exitCommand?.value?.amount ?: Amount(0, Issued(issuer, commodity))

                requireThat {
                    "there are no zero sized inputs" using ( inputs.none { it.amount.quantity == 0L } )
                    "for reference ${issuer.reference} at issuer ${party.nameOrNull()} the amounts balance" using
                            (inputAmount == outputAmount + amountExitingLedger)
                }

                verifyMoveCommand<Commands.Move>(inputs, tx.commands)
            }
        }
    }

    private fun verifyIssueCommand(inputs: List<State>,
                                   outputs: List<State>,
                                   tx: LedgerTransaction,
                                   issueCommand: AuthenticatedObject<Commands.Issue>,
                                   commodity: Commodity,
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
        val inputAmount = inputs.sumCommoditiesOrZero(Issued(issuer, commodity))
        val outputAmount = outputs.sumCommodities()
        val commodityCommands = tx.commands.select<CommodityContract.Commands>()
        requireThat {
            "the issue command has a nonce" using (issueCommand.value.nonce != 0L)
            "output deposits are owned by a command signer" using (issuer.party in issueCommand.signingParties)
            "output values sum to more than the inputs" using (outputAmount > inputAmount)
            "there is only a single issue command" using (commodityCommands.count() == 1)
        }
    }

    override fun extractCommands(commands: Collection<AuthenticatedObject<CommandData>>): List<AuthenticatedObject<Commands>>
            = commands.select<CommodityContract.Commands>()

    /**
     * Puts together an issuance transaction from the given template, that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder, tokenDef: Issued<Commodity>, pennies: Long, owner: AbstractParty, notary: Party)
            = generateIssue(tx, Amount(pennies, tokenDef), owner, notary)

    /**
     * Puts together an issuance transaction for the specified amount that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder, amount: Amount<Issued<Commodity>>, owner: AbstractParty, notary: Party)
            = generateIssue(tx, TransactionState(State(amount, owner), notary), generateIssueCommand())


    override fun deriveState(txState: TransactionState<State>, amount: Amount<Issued<Commodity>>, owner: AbstractParty)
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
