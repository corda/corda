package com.r3corda.contracts.universal

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import java.security.PublicKey
import java.time.Instant

/**
 * Created by sofusmortensen on 23/05/16.
 */

val UNIVERSAL_PROGRAM_ID = UniversalContract()

class UniversalContract : Contract {

    data class State(override val participants: List<PublicKey>,
                     val details: Arrangement) : ContractState {
        override val contract = UNIVERSAL_PROGRAM_ID
    }

    interface Commands : CommandData {

        // transition according to business rules defined in contract
        data class Action(val name: String) : Commands

        // replace parties
        // must be signed by all parties present in contract before and after command
        class Move(val from: Party, val to: Party) : TypeOnlyCommandData(), Commands

        // must be signed by all liable parties present in contract
        class Issue : TypeOnlyCommandData(), Commands
    }

    fun eval(tx: TransactionForContract, condition: Perceivable<Instant>) : Instant = when (condition) {
        is Const<Instant> -> condition.value
        else -> throw NotImplementedError()
    }

    fun eval(tx: TransactionForContract, condition: Perceivable<Boolean>) : Boolean = when (condition) {
        is PerceivableAnd -> eval(tx, condition.left) && eval(tx, condition.right)
        is PerceivableOr -> eval(tx, condition.right) || eval(tx, condition.right)
        is Const<Boolean> -> condition.value
        is TimePerceivable -> when (condition.cmp) {
            Comparison.LTE -> tx.timestamp!!.after!! <= eval(tx, condition.instant)
            Comparison.GTE -> tx.timestamp!!.before!! >= eval(tx, condition.instant)
            else -> throw NotImplementedError()
        }
        else -> throw NotImplementedError()
    }


    override fun verify(tx: TransactionForContract) {

        requireThat {
            "transaction has a single command".by (tx.commands.size == 1 )
        }

        val cmd = tx.commands.requireSingleCommand<UniversalContract.Commands>()

        val value = cmd.value

        when (value) {
            is Commands.Action -> {
                val inState = tx.inputs.single() as State
                val actions = actions(inState.details)

                val action = actions[value.name] ?: throw IllegalArgumentException("Failed requirement: action must be defined")

                requireThat {
                    "action must be timestamped" by ( tx.timestamp != null )
                    "action must be authorized" by ( cmd.signers.any { action.actors.any { party -> party.owningKey == it } } )
                    "condition must be met" by ( eval(tx, action.condition) )
                }

                when (tx.outputs.size) {
                    1 -> {
                        val outState = tx.outputs.single() as State
                        requireThat {
                            "output state must match action result state" by (action.arrangement.equals(outState.details))
                        }
                    }
                    0 -> throw IllegalArgumentException("must have at least one out state")
                    else -> {

                        var allContracts = And( tx.outputs.map { (it as State).details }.toSet() )

                        requireThat {
                            "output states must match action result state" by (action.arrangement.equals(allContracts))
                        }

                    }
                }
            }
            is Commands.Issue -> {
                val outState = tx.outputs.single() as State
                requireThat {
                    "the transaction is signed by all liable parties" by ( liableParties(outState.details).all { it in cmd.signers } )
                    "the transaction has no input states" by tx.inputs.isEmpty()
                }
            }
            is Commands.Move -> {
                val inState = tx.inputs.single() as State
                val outState = tx.outputs.single() as State
                requireThat {
                    "the transaction is signed by all liable parties" by
                            ( liableParties(outState.details).all { it in cmd.signers } )
                    "output state does not reflect move command" by
                            (replaceParty(inState.details, value.from, value.to).equals(outState.details))
                }
            }
            else -> throw IllegalArgumentException("Unrecognised command")
        }
    }

    override val legalContractReference: SecureHash
        get() = throw UnsupportedOperationException()

    fun generateIssue(tx: TransactionBuilder, arrangement: Arrangement, at: PartyAndReference, notary: PublicKey) {
        check(tx.inputStates().isEmpty())
        tx.addOutputState( State(listOf(notary), arrangement) )
        tx.addCommand(Commands.Issue(), at.party.owningKey)
    }
}

