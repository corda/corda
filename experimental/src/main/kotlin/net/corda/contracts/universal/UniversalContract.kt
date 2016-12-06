package net.corda.contracts.universal

import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.TransactionBuilder
import java.math.BigDecimal
import java.time.Instant

val UNIVERSAL_PROGRAM_ID = UniversalContract()

class UniversalContract : Contract {
    data class State(override val participants: List<CompositeKey>,
                     val details: Arrangement) : ContractState {
        override val contract = UNIVERSAL_PROGRAM_ID
    }

    interface Commands : CommandData {

        data class Fix(val fixes: List<net.corda.core.contracts.Fix>) : Commands

        // transition according to business rules defined in contract
        data class Action(val name: String) : Commands

        // replace parties
        // must be signed by all parties present in contract before and after command
        class Move(val from: Party, val to: Party) : TypeOnlyCommandData(), Commands

        // must be signed by all liable parties present in contract
        class Issue : TypeOnlyCommandData(), Commands
    }

    fun eval(@Suppress("UNUSED_PARAMETER") tx: TransactionForContract, expr: Perceivable<Instant>): Instant? = when (expr) {
        is Const -> expr.value
        is StartDate -> null
        is EndDate -> null
        else -> throw Error("Unable to evaluate")
    }

    fun eval(tx: TransactionForContract, expr: Perceivable<Boolean>): Boolean = when (expr) {
        is PerceivableAnd -> eval(tx, expr.left) && eval(tx, expr.right)
        is PerceivableOr -> eval(tx, expr.left) || eval(tx, expr.right)
        is Const<Boolean> -> expr.value
        is TimePerceivable -> when (expr.cmp) {
            Comparison.LTE -> tx.timestamp!!.after!! <= eval(tx, expr.instant)
            Comparison.GTE -> tx.timestamp!!.before!! >= eval(tx, expr.instant)
            else -> throw NotImplementedError("eval special")
        }
        is ActorPerceivable -> tx.commands.single().signers.contains(expr.actor.owningKey)
        else -> throw NotImplementedError("eval - Boolean - " + expr.javaClass.name)
    }

    fun eval(tx: TransactionForContract, expr: Perceivable<BigDecimal>): BigDecimal =
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
                    requireThat { "Fixing must be included" by false }
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

    fun validateImmediateTransfers(tx: TransactionForContract, arrangement: Arrangement): Arrangement = when (arrangement) {
        is Obligation -> {
            val amount = eval(tx, arrangement.amount)
            requireThat { "transferred quantity is non-negative" by (amount >= BigDecimal.ZERO) }
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

        if (nextStart < end) {
            // TODO: we may have to save original start date in order to roll out correctly
            val newRollOut = RollOut(nextStart, end, rollOut.frequency, rollOut.template)
            return replaceNext(arr, newRollOut)
        } else {
            return removeNext(arr)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> replaceStartEnd(p: Perceivable<T>, start: Instant, end: Instant): Perceivable<T> =
            when (p) {
                is Const -> p
                is TimePerceivable -> TimePerceivable(p.cmp, replaceStartEnd(p.instant, start, end)) as Perceivable<T>
                is EndDate -> const(end) as Perceivable<T>
                is StartDate -> const(start) as Perceivable<T>
                is UnaryPlus -> UnaryPlus(replaceStartEnd(p.arg, start, end))
                is PerceivableOperation -> PerceivableOperation<T>(replaceStartEnd(p.left, start, end), p.op, replaceStartEnd(p.right, start, end))
                is Interest -> Interest(replaceStartEnd(p.amount, start, end), p.dayCountConvention, replaceStartEnd(p.interest, start, end), replaceStartEnd(p.start, start, end), replaceStartEnd(p.end, start, end)) as Perceivable<T>
                is Fixing -> Fixing(p.source, replaceStartEnd(p.date, start, end), p.tenor) as Perceivable<T>
                is PerceivableAnd -> (replaceStartEnd(p.left, start, end) and replaceStartEnd(p.right, start, end)) as Perceivable<T>
                is PerceivableOr -> (replaceStartEnd(p.left, start, end) or replaceStartEnd(p.right, start, end)) as Perceivable<T>
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

    override fun verify(tx: TransactionForContract) {

        requireThat {
            "transaction has a single command".by(tx.commands.size == 1)
        }

        val cmd = tx.commands.requireSingleCommand<UniversalContract.Commands>()

        val value = cmd.value

        when (value) {
            is Commands.Action -> {
                val inState = tx.inputs.single() as State
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
                    "action must be timestamped" by (tx.timestamp != null)
                    // "action must be authorized" by (cmd.signers.any { action.actors.any { party -> party.owningKey == it } })
                    // todo perhaps merge these two requirements?
                    "condition must be met" by (eval(tx, action.condition))
                }

                // verify that any resulting transfers can be resolved
                //val arrangement = checkAndReduce(tx, action.arrangement)
                val arrangement = validateImmediateTransfers(tx, action.arrangement)

                when (tx.outputs.size) {
                    1 -> {
                        val outState = tx.outputs.single() as State
                        requireThat {
                            "output state must match action result state" by (arrangement.equals(outState.details))
                            "output state must match action result state" by (rest == zero)
                        }
                    }
                    0 -> throw IllegalArgumentException("must have at least one out state")
                    else -> {
                        val allContracts = And(tx.outputs.map { (it as State).details }.toSet())

                        requireThat {
                            "output states must match action result state" by (arrangement.equals(allContracts))
                        }

                    }
                }
            }
            is Commands.Issue -> {
                val outState = tx.outputs.single() as State
                requireThat {
                    "the transaction is signed by all liable parties" by (liableParties(outState.details).all { it in cmd.signers })
                    "the transaction has no input states" by tx.inputs.isEmpty()
                }
            }
            is Commands.Move -> {
                val inState = tx.inputs.single() as State
                val outState = tx.outputs.single() as State
                requireThat {
                    "the transaction is signed by all liable parties" by
                            (liableParties(outState.details).all { it in cmd.signers })
                    "output state does not reflect move command" by
                            (replaceParty(inState.details, value.from, value.to).equals(outState.details))
                }
            }
            is Commands.Fix -> {
                val inState = tx.inputs.single() as State
                val arr = when (inState.details) {
                    is Actions -> inState.details
                    is RollOut -> reduceRollOut(inState.details)
                    else -> throw IllegalArgumentException("Unexpected arrangement, " + tx.inputs.single())
                }
                val outState = tx.outputs.single() as State

                val unusedFixes = value.fixes.map { it.of }.toMutableSet()
                val expectedArr = replaceFixing(tx, arr,
                        value.fixes.associateBy({ it.of }, { it.value }), unusedFixes)

                requireThat {
                    "relevant fixing must be included" by unusedFixes.isEmpty()
                    "output state does not reflect fix command" by
                            (expectedArr.equals(outState.details))
                }
            }
            else -> throw IllegalArgumentException("Unrecognised command")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> replaceFixing(tx: TransactionForContract, perceivable: Perceivable<T>,
                          fixings: Map<FixOf, BigDecimal>, unusedFixings: MutableSet<FixOf>): Perceivable<T> =
            when (perceivable) {
                is Const -> perceivable
                is UnaryPlus -> UnaryPlus(replaceFixing(tx, perceivable.arg, fixings, unusedFixings))
                is PerceivableOperation -> PerceivableOperation(replaceFixing(tx, perceivable.left, fixings, unusedFixings),
                        perceivable.op, replaceFixing(tx, perceivable.right, fixings, unusedFixings))
                is Interest -> Interest(replaceFixing(tx, perceivable.amount, fixings, unusedFixings),
                        perceivable.dayCountConvention, replaceFixing(tx, perceivable.interest, fixings, unusedFixings),
                        perceivable.start, perceivable.end) as Perceivable<T>
                is Fixing -> {
                    val dt = eval(tx, perceivable.date)
                    if (dt != null && fixings.containsKey(FixOf(perceivable.source, dt.toLocalDate(), perceivable.tenor))) {
                        unusedFixings.remove(FixOf(perceivable.source, dt.toLocalDate(), perceivable.tenor))
                        Const(fixings[FixOf(perceivable.source, dt.toLocalDate(), perceivable.tenor)]!!) as Perceivable<T>
                    } else perceivable
                }
                else -> throw NotImplementedError("replaceFixing - " + perceivable.javaClass.name)
            }

    fun replaceFixing(tx: TransactionForContract, arr: Action,
                      fixings: Map<FixOf, BigDecimal>, unusedFixings: MutableSet<FixOf>) =
            Action(arr.name, replaceFixing(tx, arr.condition, fixings, unusedFixings), replaceFixing(tx, arr.arrangement, fixings, unusedFixings))

    fun replaceFixing(tx: TransactionForContract, arr: Arrangement,
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

    override val legalContractReference: SecureHash
        get() = throw UnsupportedOperationException()

    fun generateIssue(tx: TransactionBuilder, arrangement: Arrangement, at: PartyAndReference, notary: CompositeKey) {
        check(tx.inputStates().isEmpty())
        tx.addOutputState(State(listOf(notary), arrangement))
        tx.addCommand(Commands.Issue(), at.party.owningKey)
    }
}

