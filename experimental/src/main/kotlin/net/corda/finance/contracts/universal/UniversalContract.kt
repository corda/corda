package net.corda.finance.contracts.universal

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.BusinessCalendar
import net.corda.finance.contracts.FixOf
import java.math.BigDecimal
import java.time.Instant

const val UNIVERSAL_PROGRAM_ID = "net.corda.finance.contracts.universal.UniversalContract"

class UniversalContract : Contract {
    data class State(override val participants: List<AbstractParty>,
                     val details: Arrangement) : ContractState

    interface Commands : CommandData {

        data class Fix(val fixes: List<net.corda.finance.contracts.Fix>) : Commands

        // transition according to business rules defined in contract
        data class Action(val name: String) : Commands

        // replace parties
        // must be signed by all parties present in contract before and after command
        class Move(val from: Party, val to: Party) : TypeOnlyCommandData(), Commands

        // must be signed by all liable parties present in contract
        class Issue : TypeOnlyCommandData(), Commands

        // Split contract in two, ratio must be positive and less than one.
        // todo: Who should sign this?
        class Split(val ratio: BigDecimal) : Commands
    }

    fun eval(@Suppress("UNUSED_PARAMETER") tx: LedgerTransaction, expr: Perceivable<Instant>): Instant? = when (expr) {
        is Const -> expr.value
        is StartDate -> null
        is EndDate -> null
        else -> throw Error("Unable to evaluate")
    }

    fun eval(tx: LedgerTransaction, expr: Perceivable<Boolean>): Boolean = when (expr) {
        is PerceivableAnd -> eval(tx, expr.left) && eval(tx, expr.right)
        is PerceivableOr -> eval(tx, expr.left) || eval(tx, expr.right)
        is Const<Boolean> -> expr.value
        is TimePerceivable -> when (expr.cmp) {
            Comparison.LTE -> tx.timeWindow!!.fromTime!! <= eval(tx, expr.instant)
            Comparison.GTE -> tx.timeWindow!!.untilTime!! >= eval(tx, expr.instant)
            else -> throw NotImplementedError("eval special")
        }
        is ActorPerceivable -> tx.commands.single().signers.contains(expr.actor.owningKey)
        else -> throw NotImplementedError("eval - Boolean - " + expr.javaClass.name)
    }

    fun eval(tx: LedgerTransaction, expr: Perceivable<BigDecimal>): BigDecimal =
            when (expr) {
                is Const<BigDecimal> -> expr.value
                is UnaryPlus -> {
                    val x = eval(tx, expr.arg)
                    if (x > BigDecimal.ZERO)
                        x
                    else
                        BigDecimal.ZERO
                }
                is PerceivableOperation -> {
                    val l = eval(tx, expr.left)
                    val r = eval(tx, expr.right)

                    when (expr.op) {
                        Operation.DIV -> l / r
                        Operation.MINUS -> l - r
                        Operation.PLUS -> l + r
                        Operation.TIMES -> l * r
                        else -> throw NotImplementedError("eval - amount - operation " + expr.op)
                    }
                }
                is Fixing -> {
                    requireThat { "Fixing must be included" using false }
                    0.0.bd
                }
                is Interest -> {
                    val a = eval(tx, expr.amount)
                    val i = eval(tx, expr.interest)

                    //TODO

                    a * i / 100.0.bd
                }
                else -> throw NotImplementedError("eval - BigDecimal - " + expr.javaClass.name)
            }

    fun validateImmediateTransfers(tx: LedgerTransaction, arrangement: Arrangement): Arrangement = when (arrangement) {
        is Obligation -> {
            val amount = eval(tx, arrangement.amount)
            requireThat { "transferred quantity is non-negative" using (amount >= BigDecimal.ZERO) }
            Obligation(const(amount), arrangement.currency, arrangement.from, arrangement.to)
        }
        is And -> And(arrangement.arrangements.map { validateImmediateTransfers(tx, it) }.toSet())
        else -> arrangement
    }

    // TODO: think about multi layered rollouts
    fun reduceRollOut(rollOut: RollOut): Arrangement {
        val start = rollOut.startDate
        val end = rollOut.endDate

        // TODO: calendar + rolling conventions
        val schedule = BusinessCalendar.createGenericSchedule(start, rollOut.frequency, noOfAdditionalPeriods = 1, endDate = end)

        val nextStart = schedule.first()
        // TODO: look into schedule for final dates

        val arr = replaceStartEnd(rollOut.template, start.toInstant(), nextStart.toInstant())

        return if (nextStart < end) {
            // TODO: we may have to save original start date in order to roll out correctly
            val newRollOut = RollOut(nextStart, end, rollOut.frequency, rollOut.template)
            replaceNext(arr, newRollOut)
        } else {
            removeNext(arr)
        }
    }

    fun <T> replaceStartEnd(p: Perceivable<T>, start: Instant, end: Instant): Perceivable<T> =
            when (p) {
                is Const -> p
                is TimePerceivable -> uncheckedCast(TimePerceivable(p.cmp, replaceStartEnd(p.instant, start, end)))
                is EndDate -> uncheckedCast(const(end))
                is StartDate -> uncheckedCast(const(start))
                is UnaryPlus -> UnaryPlus(replaceStartEnd(p.arg, start, end))
                is PerceivableOperation -> PerceivableOperation(replaceStartEnd(p.left, start, end), p.op, replaceStartEnd(p.right, start, end))
                is Interest -> uncheckedCast(Interest(replaceStartEnd(p.amount, start, end), p.dayCountConvention, replaceStartEnd(p.interest, start, end), replaceStartEnd(p.start, start, end), replaceStartEnd(p.end, start, end)))
                is Fixing -> uncheckedCast(Fixing(p.source, replaceStartEnd(p.date, start, end), p.tenor))
                is PerceivableAnd -> uncheckedCast(replaceStartEnd(p.left, start, end) and replaceStartEnd(p.right, start, end))
                is PerceivableOr -> uncheckedCast(replaceStartEnd(p.left, start, end) or replaceStartEnd(p.right, start, end))
                is ActorPerceivable -> p
                else -> throw NotImplementedError("replaceStartEnd " + p.javaClass.name)
            }

    fun replaceStartEnd(arrangement: Arrangement, start: Instant, end: Instant): Arrangement =
            when (arrangement) {
                is And -> And(arrangement.arrangements.map { replaceStartEnd(it, start, end) }.toSet())
                is Zero -> arrangement
                is Obligation -> Obligation(replaceStartEnd(arrangement.amount, start, end), arrangement.currency, arrangement.from, arrangement.to)
                is Actions -> Actions(arrangement.actions.map { Action(it.name, replaceStartEnd(it.condition, start, end), replaceStartEnd(it.arrangement, start, end)) }.toSet())
                is Continuation -> arrangement
                else -> throw NotImplementedError("replaceStartEnd " + arrangement.javaClass.name)
            }

    fun replaceNext(arrangement: Arrangement, nextReplacement: RollOut): Arrangement =
            when (arrangement) {
                is Actions -> Actions(arrangement.actions.map { Action(it.name, it.condition, replaceNext(it.arrangement, nextReplacement)) }.toSet())
                is And -> And(arrangement.arrangements.map { replaceNext(it, nextReplacement) }.toSet())
                is Obligation -> arrangement
                is Zero -> arrangement
                is Continuation -> nextReplacement
                else -> throw NotImplementedError("replaceNext " + arrangement.javaClass.name)
            }

    fun removeNext(arrangement: Arrangement): Arrangement =
            when (arrangement) {
                is Actions -> Actions(arrangement.actions.map { Action(it.name, it.condition, removeNext(it.arrangement)) }.toSet())
                is And -> {
                    val a = arrangement.arrangements.map { removeNext(it) }.filter { it != zero }
                    if (a.count() > 1)
                        And(a.toSet())
                    else
                        a.single()
                }
                is Obligation -> arrangement
                is Zero -> arrangement
                is Continuation -> zero
                else -> throw NotImplementedError("replaceNext " + arrangement.javaClass.name)
            }

    override fun verify(tx: LedgerTransaction) {

        requireThat {
            "transaction has a single command".using(tx.commands.size == 1)
        }

        val cmd = tx.commands.requireSingleCommand<UniversalContract.Commands>()

        val value = cmd.value

        when (value) {
            is Commands.Action -> {
                val inState = tx.inputsOfType<State>().single()
                val arr = when (inState.details) {
                    is Actions -> inState.details
                    is RollOut -> reduceRollOut(inState.details)
                    else -> throw IllegalArgumentException("Unexpected arrangement, " + tx.inputs.single())
                }

                val actions = actions(arr)

                val action = actions[value.name] ?: throw IllegalArgumentException("Failed requirement: action must be defined")

                // TODO: not sure this is necessary??
                val rest = extractRemainder(arr, action)

                // for now - let's assume not
                assert(rest is Zero)

                requireThat {
                    "action must have a time-window" using (tx.timeWindow != null)
                    // "action must be authorized" by (cmd.signers.any { action.actors.any { party -> party.owningKey == it } })
                    // todo perhaps merge these two requirements?
                    "condition must be met" using (eval(tx, action.condition))
                }

                // verify that any resulting transfers can be resolved
                //val arrangement = checkAndReduce(tx, action.arrangement)
                val arrangement = validateImmediateTransfers(tx, action.arrangement)

                when (tx.outputs.size) {
                    1 -> {
                        val outState = tx.outputsOfType<State>().single()
                        requireThat {
                            "output state must match action result state" using (arrangement == outState.details)
                            "output state must match action result state" using (rest == zero)
                        }
                    }
                    0 -> throw IllegalArgumentException("must have at least one out state")
                    else -> {
                        val allContracts = And(tx.outputsOfType<State>().map { it.details }.toSet())

                        requireThat {
                            "output states must match action result state" using (arrangement == allContracts)
                        }

                    }
                }
            }
            is Commands.Issue -> {
                val outState = tx.outputsOfType<State>().single()
                requireThat {
                    "the transaction is signed by all liable parties" using (liableParties(outState.details).all { it in cmd.signers })
                    "the transaction has no input states" using tx.inputs.isEmpty()
                }
            }
            is Commands.Move -> {
                val inState = tx.inputsOfType<State>().single()
                val outState = tx.outputsOfType<State>().single()
                requireThat {
                    "the transaction is signed by all liable parties" using
                            (liableParties(outState.details).all { it in cmd.signers })
                    "output state does not reflect move command" using
                            (replaceParty(inState.details, value.from, value.to) == outState.details)
                }
            }
            is Commands.Fix -> {
                val inState = tx.inputsOfType<State>().single()
                val arr = when (inState.details) {
                    is Actions -> inState.details
                    is RollOut -> reduceRollOut(inState.details)
                    else -> throw IllegalArgumentException("Unexpected arrangement, " + tx.inputs.single())
                }
                val outState = tx.outputsOfType<State>().single()

                val unusedFixes = value.fixes.map { it.of }.toMutableSet()
                val expectedArr = replaceFixing(tx, arr,
                        value.fixes.associateBy({ it.of }, { it.value }), unusedFixes)

                requireThat {
                    "relevant fixing must be included" using unusedFixes.isEmpty()
                    "output state does not reflect fix command" using
                            (expectedArr == outState.details)
                }
            }
            else -> throw IllegalArgumentException("Unrecognised command")
        }
    }

    fun <T> replaceFixing(tx: LedgerTransaction, perceivable: Perceivable<T>,
                          fixings: Map<FixOf, BigDecimal>, unusedFixings: MutableSet<FixOf>): Perceivable<T> =
            when (perceivable) {
                is Const -> perceivable
                is UnaryPlus -> UnaryPlus(replaceFixing(tx, perceivable.arg, fixings, unusedFixings))
                is PerceivableOperation -> PerceivableOperation(replaceFixing(tx, perceivable.left, fixings, unusedFixings),
                        perceivable.op, replaceFixing(tx, perceivable.right, fixings, unusedFixings))
                is Interest -> uncheckedCast(Interest(replaceFixing(tx, perceivable.amount, fixings, unusedFixings),
                        perceivable.dayCountConvention, replaceFixing(tx, perceivable.interest, fixings, unusedFixings),
                        perceivable.start, perceivable.end))
                is Fixing -> {
                    val dt = eval(tx, perceivable.date)
                    if (dt != null && fixings.containsKey(FixOf(perceivable.source, dt.toLocalDate(), perceivable.tenor))) {
                        unusedFixings.remove(FixOf(perceivable.source, dt.toLocalDate(), perceivable.tenor))
                        uncheckedCast(Const(fixings[FixOf(perceivable.source, dt.toLocalDate(), perceivable.tenor)]!!))
                    } else perceivable
                }
                else -> throw NotImplementedError("replaceFixing - " + perceivable.javaClass.name)
            }

    fun replaceFixing(tx: LedgerTransaction, arr: Action,
                      fixings: Map<FixOf, BigDecimal>, unusedFixings: MutableSet<FixOf>) =
            Action(arr.name, replaceFixing(tx, arr.condition, fixings, unusedFixings), replaceFixing(tx, arr.arrangement, fixings, unusedFixings))

    fun replaceFixing(tx: LedgerTransaction, arr: Arrangement,
                      fixings: Map<FixOf, BigDecimal>, unusedFixings: MutableSet<FixOf>): Arrangement =
            when (arr) {
                is Zero -> arr
                is And -> And(arr.arrangements.map { replaceFixing(tx, it, fixings, unusedFixings) }.toSet())
                is Obligation -> Obligation(replaceFixing(tx, arr.amount, fixings, unusedFixings), arr.currency, arr.from, arr.to)
                is Actions -> Actions(arr.actions.map { Action(it.name, it.condition, replaceFixing(tx, it.arrangement, fixings, unusedFixings)) }.toSet())
                is RollOut -> RollOut(arr.startDate, arr.endDate, arr.frequency, replaceFixing(tx, arr.template, fixings, unusedFixings))
                is Continuation -> arr
                else -> throw NotImplementedError("replaceFixing - " + arr.javaClass.name)
            }

    fun generateIssue(tx: TransactionBuilder, arrangement: Arrangement, at: PartyAndReference, notary: Party) {
        check(tx.inputStates().isEmpty())
        tx.addOutputState(State(listOf(notary), arrangement), UNIVERSAL_PROGRAM_ID)
        tx.addCommand(Commands.Issue(), at.party.owningKey)
    }
}

