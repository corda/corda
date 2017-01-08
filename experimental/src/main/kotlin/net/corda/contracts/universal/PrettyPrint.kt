package net.corda.contracts.universal

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import java.math.BigDecimal
import java.time.Instant

private class PrettyPrint(arr : Arrangement) {

    val parties = involvedParties(arr)

    private val sb = StringBuilder()
    private var indentLevel = 0

    private var atStart = true
    private fun print(msg: String) {
        if (atStart)
            repeat(indentLevel, { sb.append(' ') })
        sb.append(msg)
        atStart = false
    }

    private fun println(message: Any?) {
        if (atStart)
            repeat(indentLevel, { sb.append(' ') })
        sb.appendln(message)
        atStart = true
    }

    private fun print(msg: Any?) {
        if (atStart)
            repeat(indentLevel, { sb.append(' ') })
        sb.append(msg)
        atStart = false
    }

    fun <T> indent(body: () -> T): T {
        indentLevel += 2
        val rv = body()
        indentLevel -= 2
        return rv
    }

    val partyMap = mutableMapOf<CompositeKey, String>()
    val usedPartyNames = mutableSetOf<String>()

    fun createPartyName(party : Party) : String
    {
        val parts = party.name.toLowerCase().split(' ')

        var camelName = parts.drop(1).fold(parts.first()) {
            s, i -> s + i.first().toUpperCase() + i.drop(1)
        }

        if (usedPartyNames.contains(camelName)) {
            camelName += "_" + partyMap.size.toString()
        }

        partyMap.put(party.owningKey, camelName)
        usedPartyNames.add(camelName)

        return camelName
     }

    init {
        parties.forEach {
            println( "val ${createPartyName(it)} = Party(\"${it.name}\", \"${it.owningKey}\")" )
        }
    }

    fun prettyPrintPerBoolean(per: Perceivable<Boolean>) {
        when (per) {
            is Const -> {
                print("\"${per.value}\"")
            }
            is PerceivableOr -> {
                prettyPrintPerBoolean(per.left)
                print(" or ")
                prettyPrintPerBoolean(per.right)
            }
            is PerceivableAnd -> {
                prettyPrintPerBoolean(per.left)
                print(" and ")
                prettyPrintPerBoolean(per.right)
            }
            is TimePerceivable -> {
                when (per.cmp) {
                    Comparison.GT, Comparison.GTE ->  {
                        print("after(")
                        prettyPrintPerInstant(per.instant)
                        print(")")
                    }
                    Comparison.LT, Comparison.LTE -> {
                        print("before(")
                        prettyPrintPerInstant(per.instant)
                        print(")")
                    }
                }
            }
            is ActorPerceivable -> {
                print("signedBy(${partyMap[per.actor.owningKey]})")
            }
            else -> print(per)
        }
    }

    fun prettyPrintPerInstant(per: Perceivable<Instant>) {
        when (per) {
            is Const -> {
                print("\"${per.value}\"")
            }
            is StartDate -> {
                print("startDate")
            }
            is EndDate -> {
                print("endDate")
            }
            else -> print(per)
        }
    }

    fun prettyPrintPerBD(per: Perceivable<BigDecimal>) {
        when (per) {
            is PerceivableOperation<BigDecimal> -> {
                prettyPrintPerBD(per.left)
                when (per.op) {
                    Operation.PLUS -> print(" + ")
                    Operation.MINUS -> print(" - ")
                    Operation.DIV -> print(" / ")
                    Operation.TIMES -> print(" * ")
                    else -> print(per.op)
                }
                prettyPrintPerBD(per.right)
            }
            is UnaryPlus -> {
                print("(")
                prettyPrintPerBD(per.arg)
                print(".).plus()")
            }
            is Const -> {
                print(per.value)
            }
            is Interest -> {
                print("Interest(")
                prettyPrintPerBD(per.amount)
                print(", \"${per.dayCountConvention}\", ")
                prettyPrintPerBD(per.amount)
                print(", ")
                prettyPrintPerInstant(per.start)
                print(", ")
                prettyPrintPerInstant(per.end)
                print(")")
            }
            else -> println(per)
        }
    }

    fun prettyPrint(arr: Arrangement) {

        when (arr) {
            is Zero -> print("zero")
            is RollOut -> {
                println("rollOut(\"${arr.startDate}\".ld, \"${arr.endDate}\".ld, Frequency.${arr.frequency}) { ")
                indent {
                    prettyPrint(arr.template)
                }
                println("}")
            }
            is And -> {
                for (it in arr.arrangements) {
                    prettyPrint(it)
                }
            }
            is Continuation -> {
                println("next()")
            }
            is Obligation -> {
                print("${partyMap[arr.from.owningKey]}.gives( ${partyMap[arr.to.owningKey]}, ")
                prettyPrintPerBD(arr.amount)
                println(", ${arr.currency})")
            }
            is Actions -> {
                println("actions {")
                indent {
                    for ((name, condition, arrangement) in arr.actions) {
                        print("\"$name\".givenThat(")
                        prettyPrintPerBoolean(condition)
                        println(") {")
                        indent {
                            prettyPrint(arrangement)
                        }
                        println("}")
                    }
                }
                println("}")
            }
            else -> println(arr)
        }
    }

    override fun toString(): String {
        return sb.toString()
    }
}

fun prettyPrint(arr: Arrangement): String {
    val pb = PrettyPrint(arr)
    pb.prettyPrint(arr)
    return pb.toString()
}

