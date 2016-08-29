package com.r3corda.contracts.universal

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import java.math.BigDecimal
import java.security.PublicKey
import java.time.Instant
import java.util.*

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

        data class Fix(val fixes: List<com.r3corda.core.contracts.Fix>) : Commands

        // transition according to business rules defined in contract
        data class Action(val name: String) : Commands

        // replace parties
        // must be signed by all parties present in contract before and after command
        class Move(val from: Party, val to: Party) : TypeOnlyCommandData(), Commands

        // must be signed by all liable parties present in contract
        class Issue : TypeOnlyCommandData(), Commands
    }

    fun eval(tx: TransactionForContract, expr: Perceivable<Instant>) : Instant = when (expr) {
        is Const<Instant> -> expr.value
        else -> throw NotImplementedError()
    }

    fun eval(tx: TransactionForContract, expr: Perceivable<Boolean>) : Boolean = when (expr) {
        is PerceivableAnd -> eval(tx, expr.left) && eval(tx, expr.right)
        is PerceivableOr -> eval(tx, expr.right) || eval(tx, expr.right)
        is Const<Boolean> -> expr.value
        is TimePerceivable -> when (expr.cmp) {
            Comparison.LTE -> tx.timestamp!!.after!! <= eval(tx, expr.instant)
            Comparison.GTE -> tx.timestamp!!.before!! >= eval(tx, expr.instant)
            else -> throw NotImplementedError()
        }
        else -> throw NotImplementedError()
    }

    fun eval(tx: TransactionForContract, expr: Perceivable<BigDecimal>) : BigDecimal = when (expr) {
        is Const -> expr.value
        is Fixing -> {
            requireThat { "Fixing must be included" by false }
            BigDecimal(0.0)
        }
        else -> throw Error("fook")
    }

    fun eval(tx: TransactionForContract, expr: Perceivable<Amount<Currency>>) : Perceivable<Amount<Currency>> = when (expr) {
        is PerceivableOperation -> when (expr.op) {
            Operation.DIV -> throw NotImplementedError()
            Operation.MINUS -> {
                eval(tx, expr.left)
                eval(tx, expr.right)
                throw NotImplementedError()
            }
            Operation.PLUS -> throw NotImplementedError()
            Operation.TIMES -> throw NotImplementedError()
        }
        is UnaryPlus -> {
            eval(tx, expr.arg)
            expr
        }
        is Interest -> {
            eval(tx, expr.interest)
            expr
        }
        else -> expr
    }

    fun checkAndReduce(tx: TransactionForContract, arrangement: Arrangement) : Arrangement = when (arrangement)
    {
        is Transfer -> Transfer( eval(tx, arrangement.amount), arrangement.from, arrangement.to )
        is And -> And( arrangement.arrangements.map { checkAndReduce(tx, it) }.toSet() )
        else -> arrangement
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

                val arrangement = checkAndReduce(tx, action.arrangement)

                when (tx.outputs.size) {
                    1 -> {
                        val outState = tx.outputs.single() as State
                        requireThat {
                            "output state must match action result state" by (arrangement.equals(outState.details))
                        }
                    }
                    0 -> throw IllegalArgumentException("must have at least one out state")
                    else -> {

                        var allContracts = And( tx.outputs.map { (it as State).details }.toSet() )

                        requireThat {
                            "output states must match action result state" by (arrangement.equals(allContracts))
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
            is Commands.Fix -> {
                val inState = tx.inputs.single() as State
                val outState = tx.outputs.single() as State

                val unusedFixes = value.fixes.map { it.of }.toMutableSet()
                val arr = replaceFixing(tx, inState.details,
                        value.fixes.associateBy({ it.of }, { it.value }), unusedFixes)

                requireThat {
                    "relevant fixing must be included" by unusedFixes.isEmpty()
                    "output state does not reflect fix command" by
                            (arr.equals(outState.details))
                }
            }
            else -> throw IllegalArgumentException("Unrecognised command")
        }
    }

    fun <T> replaceFixing(tx: TransactionForContract, perceivable: Perceivable<T>,
                          fixings: Map<FixOf, BigDecimal>, unusedFixings: MutableSet<FixOf>): Perceivable<T> =
            when (perceivable) {
                is Const -> perceivable
                is UnaryPlus -> UnaryPlus(replaceFixing(tx, perceivable.arg, fixings, unusedFixings))
                is PerceivableOperation -> PerceivableOperation(replaceFixing(tx, perceivable.left, fixings, unusedFixings),
                        perceivable.op, replaceFixing(tx, perceivable.right, fixings, unusedFixings))
                is Interest -> Interest( replaceFixing(tx, perceivable.amount, fixings, unusedFixings),
                        perceivable.dayCountConvention, replaceFixing(tx, perceivable.interest, fixings, unusedFixings),
                        perceivable.start, perceivable.end) as Perceivable<T>
                is Fixing -> if (fixings.containsKey(FixOf(perceivable.source, perceivable.date, perceivable.tenor))) {
                    unusedFixings.remove(FixOf(perceivable.source, perceivable.date, perceivable.tenor))
                    Const(fixings[FixOf(perceivable.source, perceivable.date, perceivable.tenor)]!!) as Perceivable<T>
                }
                else perceivable
                else -> throw NotImplementedError(perceivable.toString())
            }

    fun replaceFixing(tx: TransactionForContract, arr: Action,
                      fixings: Map<FixOf, BigDecimal>, unusedFixings: MutableSet<FixOf>) =
            Action(arr.name, replaceFixing(tx, arr.condition, fixings, unusedFixings),
                    arr.actors, replaceFixing(tx, arr.arrangement, fixings, unusedFixings))

    fun replaceFixing(tx: TransactionForContract, arr: Arrangement,
                      fixings: Map<FixOf, BigDecimal>, unusedFixings: MutableSet<FixOf>) : Arrangement =
            when (arr) {
                is Zero -> arr
                is Transfer -> Transfer(replaceFixing(tx, arr.amount, fixings, unusedFixings), arr.from, arr.to)
                is Or -> Or( arr.actions.map { replaceFixing(tx, it, fixings, unusedFixings) }.toSet() )
                else -> throw NotImplementedError( arr.toString() )
            }

    override val legalContractReference: SecureHash
        get() = throw UnsupportedOperationException()

    fun generateIssue(tx: TransactionBuilder, arrangement: Arrangement, at: PartyAndReference, notary: PublicKey) {
        check(tx.inputStates().isEmpty())
        tx.addOutputState( State(listOf(notary), arrangement) )
        tx.addCommand(Commands.Issue(), at.party.owningKey)
    }
}

