package com.r3corda.contracts

import com.r3corda.contracts.asset.Cash
import com.r3corda.contracts.asset.InsufficientBalanceException
import com.r3corda.contracts.asset.sumCashBy
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.NullPublicKey
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.toStringShort
import com.r3corda.core.utilities.Emoji
import java.security.PublicKey
import java.time.Instant
import java.util.*

/**
 * This is an ultra-trivial implementation of commercial paper, which is essentially a simpler version of a corporate
 * bond. It can be seen as a company-specific currency. A company issues CP with a particular face value, say $100,
 * but sells it for less, say $90. The paper can be redeemed for cash at a given date in the future. Thus this example
 * would have a 10% interest rate with a single repayment. Commercial paper is often rolled over (the maturity date
 * is adjusted as if the paper was redeemed and immediately repurchased, but without having to front the cash).
 *
 * This contract is not intended to realistically model CP. It is here only to act as a next step up above cash in
 * the prototyping phase. It is thus very incomplete.
 *
 * Open issues:
 *  - In this model, you cannot merge or split CP. Can you do this normally? We could model CP as a specialised form
 *    of cash, or reuse some of the cash code? Waiting on response from Ayoub and Rajar about whether CP can always
 *    be split/merged or only in secondary markets. Even if current systems can't do this, would it be a desirable
 *    feature to have anyway?
 *  - The funding steps of CP is totally ignored in this model.
 *  - No attention is paid to the existing roles of custodians, funding banks, etc.
 *  - There are regional variations on the CP concept, for instance, American CP requires a special "CUSIP number"
 *    which may need to be tracked. That, in turn, requires validation logic (there is a bean validator that knows how
 *    to do this in the Apache BVal project).
 */

val CP_PROGRAM_ID = CommercialPaper()

// TODO: Generalise the notion of an owned instrument into a superclass/supercontract. Consider composition vs inheritance.
class CommercialPaper : Contract {
    // TODO: should reference the content of the legal agreement, not its URI
    override val legalContractReference: SecureHash = SecureHash.sha256("https://en.wikipedia.org/wiki/Commercial_paper")

    data class State(
            val issuance: PartyAndReference,
            override val owner: PublicKey,
            val faceValue: Amount<Issued<Currency>>,
            val maturityDate: Instant
    ) : OwnableState, ICommercialPaperState {
        override val contract = CP_PROGRAM_ID
        override val participants: List<PublicKey>
            get() = listOf(owner)

        fun withoutOwner() = copy(owner = NullPublicKey)
        override fun withNewOwner(newOwner: PublicKey) = Pair(Commands.Move(), copy(owner = newOwner))
        override fun toString() = "${Emoji.newspaper}CommercialPaper(of $faceValue redeemable on $maturityDate by '$issuance', owned by ${owner.toStringShort()})"

        // Although kotlin is smart enough not to need these, as we are using the ICommercialPaperState, we need to declare them explicitly for use later,
        override fun withOwner(newOwner: PublicKey): ICommercialPaperState = copy(owner = newOwner)

        override fun withIssuance(newIssuance: PartyAndReference): ICommercialPaperState = copy(issuance = newIssuance)
        override fun withFaceValue(newFaceValue: Amount<Issued<Currency>>): ICommercialPaperState = copy(faceValue = newFaceValue)
        override fun withMaturityDate(newMaturityDate: Instant): ICommercialPaperState = copy(maturityDate = newMaturityDate)
    }

    interface Commands : CommandData {
        class Move: TypeOnlyCommandData(), Commands
        data class Redeem(val notary: Party) : Commands
        // We don't need a nonce in the issue command, because the issuance.reference field should already be unique per CP.
        // However, nothing in the platform enforces that uniqueness: it's up to the issuer.
        data class Issue(val notary: Party) : Commands
    }

    override fun verify(tx: TransactionForContract) {
        // Group by everything except owner: any modification to the CP at all is considered changing it fundamentally.
        val groups = tx.groupStates() { it: State -> it.withoutOwner() }

        // There are two possible things that can be done with this CP. The first is trading it. The second is redeeming
        // it for cash on or after the maturity date.
        val command = tx.commands.requireSingleCommand<CommercialPaper.Commands>()
        // If it's an issue, we can't take notary from inputs, so it must be specified in the command
        val timestamp: TimestampCommand? = if (command.value is Commands.Issue)
            tx.getTimestampBy((command.value as Commands.Issue).notary)
        else if (command.value is Commands.Redeem)
            tx.getTimestampBy((command.value as Commands.Redeem).notary)
        else
            null

        for ((inputs, outputs, key) in groups) {
            when (command.value) {
                is Commands.Move -> {
                    val input = inputs.single()
                    requireThat {
                        "the transaction is signed by the owner of the CP" by (input.owner in command.signers)
                        "the state is propagated" by (outputs.size == 1)
                        // Don't need to check anything else, as if outputs.size == 1 then the output is equal to
                        // the input ignoring the owner field due to the grouping.
                    }
                }

            // Redemption of the paper requires movement of on-ledger cash.
                is Commands.Redeem -> {
                    val input = inputs.single()
                    val received = tx.outputs.sumCashBy(input.owner)
                    val time = timestamp?.after ?: throw IllegalArgumentException("Redemptions must be timestamped")
                    requireThat {
                        "the paper must have matured" by (time >= input.maturityDate)
                        "the received amount equals the face value" by (received == input.faceValue)
                        "the paper must be destroyed" by outputs.isEmpty()
                        "the transaction is signed by the owner of the CP" by (input.owner in command.signers)
                    }
                }

                is Commands.Issue -> {
                    val output = outputs.single()
                    val time = timestamp?.before ?: throw IllegalArgumentException("Issuances must be timestamped")
                    requireThat {
                        // Don't allow people to issue commercial paper under other entities identities.
                        "output states are issued by a command signer" by
                                (output.issuance.party.owningKey in command.signers)
                        "output values sum to more than the inputs" by (output.faceValue.quantity > 0)
                        "the maturity date is not in the past" by (time < output.maturityDate)
                        // Don't allow an existing CP state to be replaced by this issuance.
                        // TODO: Consider how to handle the case of mistaken issuances, or other need to patch.
                        "output values sum to more than the inputs" by inputs.isEmpty()
                    }
                }

                // TODO: Think about how to evolve contracts over time with new commands.
                else -> throw IllegalArgumentException("Unrecognised command")
            }
        }
    }

    /**
     * Returns a transaction that issues commercial paper, owned by the issuing parties key. Does not update
     * an existing transaction because you aren't able to issue multiple pieces of CP in a single transaction
     * at the moment: this restriction is not fundamental and may be lifted later.
     */
    fun generateIssue(faceValue: Amount<Issued<Currency>>, maturityDate: Instant, notary: Party): TransactionBuilder {
        val issuance = faceValue.token.issuer
        val state = TransactionState(State(issuance, issuance.party.owningKey, faceValue, maturityDate), notary)
        return TransactionType.General.Builder(notary = notary).withItems(state, Command(Commands.Issue(notary), issuance.party.owningKey))
    }

    /**
     * Updates the given partial transaction with an input/output/command to reassign ownership of the paper.
     */
    fun generateMove(tx: TransactionBuilder, paper: StateAndRef<State>, newOwner: PublicKey) {
        tx.addInputState(paper)
        tx.addOutputState(TransactionState(paper.state.data.copy(owner = newOwner), paper.state.notary))
        tx.addCommand(Commands.Move(), paper.state.data.owner)
    }

    /**
     * Intended to be called by the issuer of some commercial paper, when an owner has notified us that they wish
     * to redeem the paper. We must therefore send enough money to the key that owns the paper to satisfy the face
     * value, and then ensure the paper is removed from the ledger.
     *
     * @throws InsufficientBalanceException if the wallet doesn't contain enough money to pay the redeemer
     */
    @Throws(InsufficientBalanceException::class)
    fun generateRedeem(tx: TransactionBuilder, paper: StateAndRef<State>, wallet: List<StateAndRef<Cash.State>>) {
        // Add the cash movement using the states in our wallet.
        val amount = paper.state.data.faceValue.let { amount -> Amount<Currency>(amount.quantity, amount.token.product) }
        Cash().generateSpend(tx, amount, paper.state.data.owner, wallet)
        tx.addInputState(paper)
        tx.addCommand(CommercialPaper.Commands.Redeem(paper.state.notary), paper.state.data.owner)
    }
}

