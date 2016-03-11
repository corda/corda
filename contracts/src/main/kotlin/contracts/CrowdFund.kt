/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts

import core.*
import core.crypto.SecureHash
import core.node.services.DummyTimestampingAuthority
import java.security.PublicKey
import java.time.Instant
import java.util.*

val CROWDFUND_PROGRAM_ID = SecureHash.sha256("crowdsourcing")

/**
 * This is a basic crowd funding contract. It allows a party to create a funding opportunity, then for others to
 * pledge during the funding period , and then for the party to either accept the funding (if the target has been reached)
 * return the funds to the pledge-makers (if the target has not been reached).
 *
 * Discussion
 * ----------
 *
 * This method of modelling a crowdfund is similar to how it'd be done in Ethereum. The state is essentially a database
 * in which transactions evolve it over time. The state transition model we are using here though means it's possible
 * to do it in a different approach, with some additional (not yet implemented) extensions to the model. In the UTXO
 * model you can do something more like the Lighthouse application (https://www.vinumeris.com/lighthouse) in which
 * the campaign data and people's pledges are transmitted out of band, with a pledge being a partially signed
 * transaction which is valid only when merged with other transactions. The pledges can then be combined by the project
 * owner at the point at which sufficient amounts of money have been gathered, and this creates a valid transaction
 * that claims the money.
 *
 * TODO: Prototype this second variant of crowdfunding once the core model has been sufficiently extended.
 * TODO: Experiment with the use of the javax.validation API to simplify the validation logic by annotating state members.
 *
 * See JIRA bug PD-21 for further discussion and followup.
 *
 * @author James Carlyle
 */
class CrowdFund : Contract {

    data class Campaign(
            val owner: PublicKey,
            val name: String,
            val target: Amount,
            val closingTime: Instant
    ) {
        override fun toString() = "Crowdsourcing($target sought by $owner by $closingTime)"
    }

    data class State(
            val campaign: Campaign,
            val closed: Boolean = false,
            val pledges: List<Pledge> = ArrayList()
    ) : ContractState {
        override val programRef = CROWDFUND_PROGRAM_ID

        val pledgedAmount: Amount get() = pledges.map { it.amount }.sumOrZero(campaign.target.currency)
    }

    data class Pledge(
        val owner: PublicKey,
        val amount: Amount
    )


    interface Commands : CommandData {
        class Register : TypeOnlyCommandData(), Commands
        class Pledge : TypeOnlyCommandData(), Commands
        class Close : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: TransactionForVerification) {
        // There are three possible things that can be done with Crowdsourcing.
        // The first is creating it. The second is funding it with cash. The third is closing it on or after the closing
        // date, and returning funds to pledge-makers if the target is unmet, or passing to the recipient.
        val command = tx.commands.requireSingleCommand<CrowdFund.Commands>()
        val outputCrowdFund: CrowdFund.State = tx.outStates.filterIsInstance<CrowdFund.State>().single()
        val outputCash: List<Cash.State> = tx.outStates.filterIsInstance<Cash.State>()

        val time = tx.getTimestampBy(DummyTimestampingAuthority.identity)?.midpoint
        if (time == null) throw IllegalArgumentException("must be timestamped")

        when (command.value) {
            is Commands.Register -> {
                requireThat {
                    "there is no input state" by tx.inStates.filterIsInstance<CrowdFund.State>().isEmpty()
                    "the transaction is signed by the owner of the crowdsourcing" by (command.signers.contains(outputCrowdFund.campaign.owner))
                    "the output registration is empty of pledges" by (outputCrowdFund.pledges.isEmpty())
                    "the output registration has a non-zero target" by (outputCrowdFund.campaign.target.pennies > 0)
                    "the output registration has a name" by (outputCrowdFund.campaign.name.isNotBlank())
                    "the output registration has a closing time in the future" by (time < outputCrowdFund.campaign.closingTime)
                    "the output registration has an open state" by (!outputCrowdFund.closed)
                }
            }

            is Commands.Pledge -> {
                val inputCrowdFund: CrowdFund.State = tx.inStates.filterIsInstance<CrowdFund.State>().single()
                val pledgedCash = outputCash.sumCashBy(inputCrowdFund.campaign.owner)
                requireThat {
                    "campaign details have not changed" by (inputCrowdFund.campaign == outputCrowdFund.campaign)
                    "the campaign is still open" by (inputCrowdFund.campaign.closingTime >= time)
                    "the pledge must be in the same currency as the goal" by (pledgedCash.currency == outputCrowdFund.campaign.target.currency)
                    "the pledged total has increased by the value of the pledge" by (outputCrowdFund.pledgedAmount == inputCrowdFund.pledgedAmount + pledgedCash)
                    "the output registration has an open state" by (!outputCrowdFund.closed)
                }
            }

            is Commands.Close -> {
                val inputCrowdFund: CrowdFund.State = tx.inStates.filterIsInstance<CrowdFund.State>().single()

                fun checkReturns(inputCrowdFund: CrowdFund.State, outputCash: List<Cash.State>): Boolean {
                    for (pledge in inputCrowdFund.pledges) {
                        if (outputCash.none { it.amount == pledge.amount && it.owner == pledge.owner }) return false
                    }
                    return true
                }

                requireThat {
                    "campaign details have not changed" by (inputCrowdFund.campaign == outputCrowdFund.campaign)
                    "the closing date has past" by (time >= outputCrowdFund.campaign.closingTime)
                    "the input has an open state" by (!inputCrowdFund.closed)
                    "the output registration has a closed state" by (outputCrowdFund.closed)
                    // Now check whether the target was met, and if so, return cash
                    if (inputCrowdFund.pledgedAmount < inputCrowdFund.campaign.target) {
                        "the output cash returns equal the pledge total, if the target is not reached" by (outputCash.sumCash() == inputCrowdFund.pledgedAmount)
                        "the output cash is distributed to the pledge-makers, if the target is not reached" by (checkReturns(inputCrowdFund, outputCash))
                        "the output cash is distributed to the pledge-makers, if the target is not reached" by (outputCash.map { it.amount }.containsAll(inputCrowdFund.pledges.map { it.amount }))
                    }
                    "the pledged total is unchanged" by (outputCrowdFund.pledgedAmount == inputCrowdFund.pledgedAmount)
                    "the pledges are unchanged" by (outputCrowdFund.pledges == inputCrowdFund.pledges)
                }
            }

            else -> throw IllegalArgumentException("Unrecognised command")
        }
    }

    /**
     * Returns a transaction that registers a crowd-funding campaing, owned by the issuing institution's key. Does not update
     * an existing transaction because it's not possible to register multiple campaigns in a single transaction
     */
    fun generateRegister(owner: PartyReference, fundingTarget: Amount, fundingName: String, closingTime: Instant): TransactionBuilder {
        val campaign = Campaign(owner = owner.party.owningKey, name = fundingName, target = fundingTarget, closingTime = closingTime)
        val state = State(campaign)
        return TransactionBuilder().withItems(state, Command(Commands.Register(), owner.party.owningKey))
    }

    /**
     * Updates the given partial transaction with an input/output/command to fund the opportunity.
     */
    fun generatePledge(tx: TransactionBuilder, campaign: StateAndRef<State>, subscriber: PublicKey) {
        tx.addInputState(campaign.ref)
        tx.addOutputState(campaign.state.copy(
                pledges = campaign.state.pledges + CrowdFund.Pledge(subscriber, 1000.DOLLARS)
        ))
        tx.addCommand(Commands.Pledge(), subscriber)
    }

    fun generateClose(tx: TransactionBuilder, campaign: StateAndRef<State>, wallet: List<StateAndRef<Cash.State>>) {
        tx.addInputState(campaign.ref)
        tx.addOutputState(campaign.state.copy(closed = true))
        tx.addCommand(Commands.Close(), campaign.state.campaign.owner)
        // If campaign target has not been met, compose cash returns
        if (campaign.state.pledgedAmount < campaign.state.campaign.target) {
            for (pledge in campaign.state.pledges) {
                Cash().generateSpend(tx, pledge.amount, pledge.owner, wallet)
            }
        }
    }

    override val legalContractReference: SecureHash = SecureHash.sha256("Crowdsourcing")
}
