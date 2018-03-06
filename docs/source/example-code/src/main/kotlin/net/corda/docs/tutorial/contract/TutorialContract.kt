/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.docs.tutorial.contract

import net.corda.core.contracts.*
import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.utils.sumCashBy
import net.corda.testing.core.singleIdentityAndCert
import java.time.Instant
import java.util.*

class CommercialPaper : Contract {
    // DOCSTART 8
    companion object {
        const val CP_PROGRAM_ID: ContractClassName = "net.corda.finance.contracts.CommercialPaper"
    }
    // DOCEND 8

    // DOCSTART 3
    override fun verify(tx: LedgerTransaction) {
        // Group by everything except owner: any modification to the CP at all is considered changing it fundamentally.
        val groups = tx.groupStates(State::withoutOwner)

        // There are two possible things that can be done with this CP. The first is trading it. The second is redeeming
        // it for cash on or after the maturity date.
        val command = tx.commands.requireSingleCommand<CommercialPaper.Commands>()
        // DOCEND 3

        // DOCSTART 4
        val timeWindow: TimeWindow? = tx.timeWindow

        for ((inputs, outputs, _) in groups) {
            when (command.value) {
                is Commands.Move -> {
                    val input = inputs.single()
                    requireThat {
                        "the transaction is signed by the owner of the CP" using (input.owner.owningKey in command.signers)
                        "the state is propagated" using (outputs.size == 1)
                        // Don't need to check anything else, as if outputs.size == 1 then the output is equal to
                        // the input ignoring the owner field due to the grouping.
                    }
                }

                is Commands.Redeem -> {
                    // Redemption of the paper requires movement of on-ledger cash.
                    val input = inputs.single()
                    val received = tx.outputs.map { it.data }.sumCashBy(input.owner)
                    val time = timeWindow?.fromTime ?: throw IllegalArgumentException("Redemptions must be timestamped")
                    requireThat {
                        "the paper must have matured" using (time >= input.maturityDate)
                        "the received amount equals the face value" using (received == input.faceValue)
                        "the paper must be destroyed" using outputs.isEmpty()
                        "the transaction is signed by the owner of the CP" using (input.owner.owningKey in command.signers)
                    }
                }

                is Commands.Issue -> {
                    val output = outputs.single()
                    val time = timeWindow?.untilTime ?: throw IllegalArgumentException("Issuances must be timestamped")
                    requireThat {
                        // Don't allow people to issue commercial paper under other entities identities.
                        "output states are issued by a command signer" using (output.issuance.party.owningKey in command.signers)
                        "output values sum to more than the inputs" using (output.faceValue.quantity > 0)
                        "the maturity date is not in the past" using (time < output.maturityDate)
                        // Don't allow an existing CP state to be replaced by this issuance.
                        "can't reissue an existing state" using inputs.isEmpty()
                    }
                }

                else -> throw IllegalArgumentException("Unrecognised command")
            }
        }
        // DOCEND 4
    }

    // DOCSTART 2
    interface Commands : CommandData {
        class Move : TypeOnlyCommandData(), Commands
        class Redeem : TypeOnlyCommandData(), Commands
        class Issue : TypeOnlyCommandData(), Commands
    }
    // DOCEND 2

    // DOCSTART 5
    fun generateIssue(issuance: PartyAndReference, faceValue: Amount<Issued<Currency>>, maturityDate: Instant,
                      notary: Party): TransactionBuilder {
        val state = State(issuance, issuance.party, faceValue, maturityDate)
        val stateAndContract = StateAndContract(state, CP_PROGRAM_ID)
        return TransactionBuilder(notary = notary).withItems(stateAndContract, Command(Commands.Issue(), issuance.party.owningKey))
    }
    // DOCEND 5

    // DOCSTART 6
    fun generateMove(tx: TransactionBuilder, paper: StateAndRef<State>, newOwner: AbstractParty) {
        tx.addInputState(paper)
        val outputState = paper.state.data.withNewOwner(newOwner).ownableState
        tx.addOutputState(outputState, CP_PROGRAM_ID)
        tx.addCommand(Command(Commands.Move(), paper.state.data.owner.owningKey))
    }
    // DOCEND 6

    // DOCSTART 7
    @Throws(InsufficientBalanceException::class)
    fun generateRedeem(tx: TransactionBuilder, paper: StateAndRef<State>, services: ServiceHub) {
        // Add the cash movement using the states in our vault.
        Cash.generateSpend(
                services = services,
                tx = tx,
                amount = paper.state.data.faceValue.withoutIssuer(),
                ourIdentity = services.myInfo.singleIdentityAndCert(),
                to = paper.state.data.owner
        )
        tx.addInputState(paper)
        tx.addCommand(Command(Commands.Redeem(), paper.state.data.owner.owningKey))
    }
    // DOCEND 7
}

// DOCSTART 1
data class State(
        val issuance: PartyAndReference,
        override val owner: AbstractParty,
        val faceValue: Amount<Issued<Currency>>,
        val maturityDate: Instant
) : OwnableState {
    override val participants = listOf(owner)

    fun withoutOwner() = copy(owner = AnonymousParty(NullKeys.NullPublicKey))
    override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(CommercialPaper.Commands.Move(), copy(owner = newOwner))
}
// DOCEND 1