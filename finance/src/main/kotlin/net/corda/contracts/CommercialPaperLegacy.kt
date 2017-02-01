package net.corda.contracts

import net.corda.contracts.asset.sumCashBy
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.NullCompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.VaultService
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.Emoji
import java.time.Instant
import java.util.*

/**
 * Legacy version of [CommercialPaper] that includes the full verification logic itself, rather than breaking it
 * into clauses. This is here just as an example for the contract tutorial.
 */

val CP_LEGACY_PROGRAM_ID = CommercialPaperLegacy()

// TODO: Generalise the notion of an owned instrument into a superclass/supercontract. Consider composition vs inheritance.
class CommercialPaperLegacy : Contract {
    // TODO: should reference the content of the legal agreement, not its URI
    override val legalContractReference: SecureHash = SecureHash.sha256("https://en.wikipedia.org/wiki/Commercial_paper")

    data class State(
            val issuance: PartyAndReference,
            override val owner: CompositeKey,
            val faceValue: Amount<Issued<Currency>>,
            val maturityDate: Instant
    ) : OwnableState, ICommercialPaperState {
        override val contract = CP_LEGACY_PROGRAM_ID
        override val participants = listOf(owner)

        fun withoutOwner() = copy(owner = NullCompositeKey)
        override fun withNewOwner(newOwner: CompositeKey) = Pair(Commands.Move(), copy(owner = newOwner))
        override fun toString() = "${Emoji.newspaper}CommercialPaper(of $faceValue redeemable on $maturityDate by '$issuance', owned by $owner)"

        // Although kotlin is smart enough not to need these, as we are using the ICommercialPaperState, we need to declare them explicitly for use later,
        override fun withOwner(newOwner: CompositeKey): ICommercialPaperState = copy(owner = newOwner)

        override fun withIssuance(newIssuance: PartyAndReference): ICommercialPaperState = copy(issuance = newIssuance)
        override fun withFaceValue(newFaceValue: Amount<Issued<Currency>>): ICommercialPaperState = copy(faceValue = newFaceValue)
        override fun withMaturityDate(newMaturityDate: Instant): ICommercialPaperState = copy(maturityDate = newMaturityDate)
    }

    interface Commands : CommandData {
        class Move : TypeOnlyCommandData(), Commands
        class Redeem : TypeOnlyCommandData(), Commands
        // We don't need a nonce in the issue command, because the issuance.reference field should already be unique per CP.
        // However, nothing in the platform enforces that uniqueness: it's up to the issuer.
        class Issue : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: TransactionForContract) {
        // Group by everything except owner: any modification to the CP at all is considered changing it fundamentally.
        val groups = tx.groupStates(State::withoutOwner)

        // There are two possible things that can be done with this CP. The first is trading it. The second is redeeming
        // it for cash on or after the maturity date.
        val command = tx.commands.requireSingleCommand<CommercialPaperLegacy.Commands>()
        val timestamp: Timestamp? = tx.timestamp

        // Suppress compiler warning as 'key' is an unused variable when destructuring 'groups'.
        @Suppress("UNUSED_VARIABLE")
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

                is Commands.Redeem -> {
                    // Redemption of the paper requires movement of on-ledger cash.
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
                        // TODO: this has a weird/incorrect assertion string because it doesn't quite match the logic in the clause version.
                        // TODO: Consider how to handle the case of mistaken issuances, or other need to patch.
                        "output values sum to more than the inputs" by inputs.isEmpty()
                    }
                }

                // TODO: Think about how to evolve contracts over time with new commands.
                else -> throw IllegalArgumentException("Unrecognised command")
            }
        }
    }

    fun generateIssue(issuance: PartyAndReference, faceValue: Amount<Issued<Currency>>, maturityDate: Instant,
                      notary: Party.Full): TransactionBuilder {
        val state = State(issuance, issuance.party.owningKey, faceValue, maturityDate)
        return TransactionBuilder(notary = notary).withItems(state, Command(Commands.Issue(), issuance.party.owningKey))
    }

    fun generateMove(tx: TransactionBuilder, paper: StateAndRef<State>, newOwner: CompositeKey) {
        tx.addInputState(paper)
        tx.addOutputState(paper.state.data.withOwner(newOwner))
        tx.addCommand(Command(Commands.Move(), paper.state.data.owner))
    }

    @Throws(InsufficientBalanceException::class)
    fun generateRedeem(tx: TransactionBuilder, paper: StateAndRef<State>, vault: VaultService) {
        // Add the cash movement using the states in our vault.
        vault.generateSpend(tx, paper.state.data.faceValue.withoutIssuer(), paper.state.data.owner)
        tx.addInputState(paper)
        tx.addCommand(Command(Commands.Redeem(), paper.state.data.owner))
    }
}
