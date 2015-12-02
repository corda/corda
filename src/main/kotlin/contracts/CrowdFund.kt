/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts

import core.*
import core.serialization.SerializeableWithKryo
import java.security.PublicKey
import java.time.Instant
import java.util.*

val CROWDFUND_PROGRAM_ID = SecureHash.sha256("crowdsourcing")

/**
 * This is a basic crowd funding contract. It allows a party to create a funding opportunity, then for others to
 * pledge during the funding period , and then for the party to either accept the funding (if the target has been reached)
 * return the funds to the pledge-makers (if the target has not been reached)
 */
class CrowdFund : Contract {

    data class State(
            val owner: PublicKey,
            val fundingName: String,
            val fundingTarget: Amount,
            val closingTime: Instant,
            val closed: Boolean = false,
            val pledgeTotal: Amount = 0.DOLLARS,
            val pledgeCount: Int = 0,
            val pledges: List<Pledge> = ArrayList()
    ) : ContractState {
        override val programRef = CROWDFUND_PROGRAM_ID
        override fun toString() = "Crowdsourcing($fundingTarget sought by $owner by $closingTime)"
    }

    data class Pledge(
            val owner: PublicKey,
            val amount: Amount
    ) : SerializeableWithKryo


    interface Commands : Command {
        class Register : TypeOnlyCommand(), Commands
        class Fund : TypeOnlyCommand(), Commands
        class Funded : TypeOnlyCommand(), Commands
        class Unfunded : TypeOnlyCommand(), Commands
    }

    override fun verify(tx: TransactionForVerification) {
        // There are two possible things that can be done with Crowdsourcing.
        // The first is creating it. The second is funding it with cash
        // The third is closing it on or after the closing date, and returning funds to
        // pledge-makers if the target is unmet, or passing to the recipient.
        val command = tx.commands.requireSingleCommand<CrowdFund.Commands>()

        val outputCrowdFund: CrowdFund.State = tx.outStates.filterIsInstance<CrowdFund.State>().single()
        val outputCash: List<Cash.State> = tx.outStates.filterIsInstance<Cash.State>()

        when (command.value) {
            is Commands.Register -> {
                requireThat {
                    "the transaction is signed by the owner of the crowdsourcing" by (command.signers.contains(outputCrowdFund.owner))
                    "the output registration is empty of pledges" by (outputCrowdFund.pledges.isEmpty())
                    "the output registration has a non-zero target" by (outputCrowdFund.fundingTarget.pennies > 0)
                    "the output registration has a zero starting pledge total" by (outputCrowdFund.pledgeTotal.pennies == 0)
                    "the output registration has a zero starting pledge count" by (outputCrowdFund.pledgeCount == 0)
                    "the output registration has a funding currency" by (outputCrowdFund.pledgeTotal.currency.currencyCode.isNotBlank()) // TODO is this necessary? currency is not nullable
                    "the output registration has a name" by (outputCrowdFund.fundingName.isNotBlank())
                    "the output registration has a closing time in the future" by (outputCrowdFund.closingTime > tx.time)
                    "the output registration has an open state" by (!outputCrowdFund.closed)
                }
            }

            is Commands.Fund -> {
                val inputCrowdFund: CrowdFund.State = tx.inStates.filterIsInstance<CrowdFund.State>().single()
                val inputCash: List<Cash.State> = tx.inStates.filterIsInstance<Cash.State>()
                val pledge = outputCrowdFund.pledges.last()
                val pledgedCash = outputCash.single()
                val time = tx.time ?: throw IllegalStateException("Transaction must be timestamped")
                requireThat {
                    "the funding is still open" by (time <= inputCrowdFund.closingTime)
                    // TODO  "the transaction is signed by the owner of the pledge" by (command.signers.contains(inputCrowdFund.owner))
                    "the transaction is signed by the pledge-maker" by (command.signers.contains(pledge.owner))
                    "the pledge must be for a non-zero amount" by (pledge.amount.pennies > 0)
                    "the pledge must be in the same currency as the goal" by (pledge.amount.currency == outputCrowdFund.fundingTarget.currency)
                    "the number of pledges must have increased by one" by (outputCrowdFund.pledgeCount == inputCrowdFund.pledgeCount + 1)
                    "the pledged total has increased by the value of the pledge" by (outputCrowdFund.pledgeTotal.pennies == inputCrowdFund.pledgeTotal.pennies + inputCash.sumCash().pennies)
                    "the pledge has been added to the list of pledges" by (outputCrowdFund.pledges.size == outputCrowdFund.pledgeCount)
                    "the cash input has been assigned to the funding owner" by (pledgedCash.owner == inputCrowdFund.owner)
                    // TODO how to simplify the boilerplate associated with unchanged elements
                    "the owner hasn't changed" by (outputCrowdFund.owner == inputCrowdFund.owner)
                    "the funding name has not changed" by (outputCrowdFund.fundingName == inputCrowdFund.fundingName)
                    "the funding target has not changed" by (outputCrowdFund.fundingTarget == inputCrowdFund.fundingTarget)
                    "the closing time has not changed" by (outputCrowdFund.closingTime == inputCrowdFund.closingTime)
                    "the pledged total currency is unchanged" by (outputCrowdFund.pledgeTotal.currency == inputCrowdFund.pledgeTotal.currency)
                    "the output registration has an open state" by (!outputCrowdFund.closed)
                }
            }

            is Commands.Unfunded -> {
                val inputCrowdFund: CrowdFund.State = tx.inStates.filterIsInstance<CrowdFund.State>().single()
                // TODO how can this be made smarter? feels wrong as a separate function
                fun checkReturns(inputCrowdFund: CrowdFund.State, outputCash: List<Cash.State>): Boolean {
                    for (pledge in inputCrowdFund.pledges) {
                        if (outputCash.filter { it.amount == pledge.amount && it.owner == pledge.owner }.isEmpty()) return false
                    }
                    return true
                }

                val time = tx.time ?: throw IllegalStateException("Transaction must be timestamped")

                requireThat {
                    "the closing date has past" by (time >= outputCrowdFund.closingTime)
                    "the pledges did not meet the target" by (inputCrowdFund.pledgeTotal < inputCrowdFund.fundingTarget)
                    "the output cash returns equal the pledge total, if the target is not reached" by (outputCash.sumCash() == inputCrowdFund.pledgeTotal)
                    "the output cash is distributed to the pledge-makers, if the target is not reached" by (checkReturns(inputCrowdFund, outputCash))
                    "the output cash is distributed to the pledge-makers, if the target is not reached" by (outputCash.map { it.amount }.containsAll(inputCrowdFund.pledges.map { it.amount }))
                    "the input has an open state" by (!inputCrowdFund.closed)
                    "the output registration has a closed state" by (outputCrowdFund.closed)
                    // TODO how to simplify the boilerplate associated with unchanged elements
                    "the owner hasn't changed" by (outputCrowdFund.owner == inputCrowdFund.owner)
                    "the funding name has not changed" by (outputCrowdFund.fundingName == inputCrowdFund.fundingName)
                    "the funding target has not changed" by (outputCrowdFund.fundingTarget == inputCrowdFund.fundingTarget)
                    "the closing time has not changed" by (outputCrowdFund.closingTime == inputCrowdFund.closingTime)
                    "the pledged total is unchanged" by (outputCrowdFund.pledgeTotal == inputCrowdFund.pledgeTotal)
                    "the pledged count is unchanged" by (outputCrowdFund.pledgeCount == inputCrowdFund.pledgeCount)
                    "the pledges are unchanged" by (outputCrowdFund.pledges == inputCrowdFund.pledges)
                }
            }

            is Commands.Funded -> {
                val inputCrowdFund: CrowdFund.State = tx.inStates.filterIsInstance<CrowdFund.State>().single()
                val time = tx.time ?: throw IllegalStateException("Transaction must be timestamped")
                requireThat {
                    "the closing date has past" by (time >= outputCrowdFund.closingTime)
                    "the input has an open state" by (!inputCrowdFund.closed)
                    "the output registration has a closed state" by (outputCrowdFund.closed)
                    // TODO how to simplify the boilerplate associated with unchanged elements
                    "the owner hasn't changed" by (outputCrowdFund.owner == inputCrowdFund.owner)
                    "the funding name has not changed" by (outputCrowdFund.fundingName == inputCrowdFund.fundingName)
                    "the funding target has not changed" by (outputCrowdFund.fundingTarget == inputCrowdFund.fundingTarget)
                    "the closing time has not changed" by (outputCrowdFund.closingTime == inputCrowdFund.closingTime)
                    "the pledged total is unchanged" by (outputCrowdFund.pledgeTotal == inputCrowdFund.pledgeTotal)
                    "the pledged count is unchanged" by (outputCrowdFund.pledgeCount == inputCrowdFund.pledgeCount)
                    "the pledges are unchanged" by (outputCrowdFund.pledges == inputCrowdFund.pledges)
                }
            }

            else -> throw IllegalArgumentException("Unrecognised command")
        }
    }

    /**
     * Returns a transaction that registers a crowd-funding campaing, owned by the issuing parties key. Does not update
     * an existing transaction because it's not possible to register multiple campaigns in a single transaction
     */
    fun craftRegister(owner: PartyReference, fundingTarget: Amount, fundingName: String, closingTime: Instant): PartialTransaction {
        val state = State(owner = owner.party.owningKey, fundingName = fundingName, fundingTarget = fundingTarget, closingTime = closingTime)
        return PartialTransaction(state, WireCommand(CrowdFund.Commands.Register(), owner.party.owningKey))
    }

    /**
     * Updates the given partial transaction with an input/output/command to fund the opportunity.
     */
    fun craftFund(tx: PartialTransaction, campaign: StateAndRef<State>, subscriber: PublicKey) {
        tx.addInputState(campaign.ref)
        tx.addOutputState(campaign.state.copy(
                pledges = campaign.state.pledges + CrowdFund.Pledge(subscriber, 1000.DOLLARS),
                pledgeCount = campaign.state.pledgeCount + 1,
                pledgeTotal = campaign.state.pledgeTotal + 1000.DOLLARS
        ))
        tx.addArg(WireCommand(CrowdFund.Commands.Fund(), subscriber))
    }

    fun craftFunded(tx: PartialTransaction, campaign: StateAndRef<State>) {
        tx.addInputState(campaign.ref)
        tx.addOutputState(campaign.state.copy(closed = true))
        tx.addArg(WireCommand(CrowdFund.Commands.Funded(), campaign.state.owner))
    }

    override val legalContractReference: SecureHash = SecureHash.sha256("Crowdsourcing")
}
