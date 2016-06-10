package com.r3corda.contracts.generic

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash

/**
 * Created by sofusmortensen on 23/05/16.
 */

val GENERIC_PROGRAM_ID = GenericContract()

class GenericContract : Contract {

    data class State(override val notary: Party,
                     val details: Kontract) : ContractState {
        override val contract = GENERIC_PROGRAM_ID
    }

    interface Commands : CommandData {

        // transition according to business rules defined in contract
        data class Action(val name: String) : Commands

        // replace parties
        // must be signed by all parties present in contract before and after command
        class Move(val from: Party, val to: Party) : TypeOnlyCommandData(), Commands

        // must be signed by all parties present in contract
        class Issue : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: TransactionForVerification) {

        requireThat {
            "transaction has a single command".by (tx.commands.size == 1 )
        }

        val cmd = tx.commands.requireSingleCommand<GenericContract.Commands>()

        val value = cmd.value

        when (value) {
            is Commands.Action -> {
                val inState = tx.inStates.single() as State
                val actions = actions(inState.details)

                requireThat {
                    "action must be defined" by ( actions.containsKey(value.name) )
                    "action must be authorized" by ( cmd.signers.any { actions[ value.name ]!!.actors.any { party -> party.owningKey == it } } )
                    "condition must be met" by ( true ) // todo
                }

                when (tx.outStates.size) {
                    1 -> {
                        val outState = tx.outStates.single() as State
                        requireThat {
                            "output state must match action result state" by (actions[value.name]!!.kontract.equals(outState.details))
                        }
                    }
                    0 -> throw IllegalArgumentException("must have at least one out state")
                    else -> {

                        var allContracts = And( tx.outStates.map { (it as State).details }.toSet() )

                        requireThat {
                            "output states must match action result state" by (actions[value.name]!!.kontract.equals(allContracts))
                        }

                    }
                }
            }
            is Commands.Issue -> {
                val outState = tx.outStates.single() as State
                requireThat {
                    "the transaction is signed by all liable parties" by ( liableParties(outState.details).all { it in cmd.signers } )
                    "the transaction has no input states" by tx.inStates.isEmpty()
                }
            }
            is Commands.Move -> {
                val inState = tx.inStates.single() as State
                val outState = tx.outStates.single() as State
                requireThat {
                    // todo:
                    // - check actual state output
                    "the transaction is signed by all liable parties" by ( liableParties(outState.details).all { it in cmd.signers } )
                    "output state does not reflect move command" by (replaceParty(inState.details, value.from, value.to).equals(outState.details))
                }
            }
            else -> throw IllegalArgumentException("Unrecognised command")
        }

    }

    override val legalContractReference: SecureHash
        get() = throw UnsupportedOperationException()

    fun generateIssue(tx: TransactionBuilder, kontract: Kontract, at: PartyAndReference, notary: Party) {
        check(tx.inputStates().isEmpty())
        tx.addOutputState( State(notary, kontract) )
        tx.addCommand(Commands.Issue(), at.party.owningKey)
    }
}

