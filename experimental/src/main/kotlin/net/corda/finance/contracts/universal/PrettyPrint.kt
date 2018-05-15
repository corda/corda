/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.contracts.universal

import net.corda.core.crypto.toStringShort
import net.corda.core.identity.Party
import java.math.BigDecimal
import java.security.PublicKey
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

    val partyMap = mutableMapOf<PublicKey, String>()
    val usedPartyNames = mutableSetOf<String>()

    fun createPartyName(party : Party) : String
    {
        val parts = party.name.organisation.toLowerCase().split(' ')

        var camelName = parts.drop(1).fold(parts.first()) {
            s, i -> s + i.first().toUpperCase() + i.drop(1)
        }

        if (usedPartyNames.contains(camelName)) {
            camelName += "_" + partyMap.size.toString()
        }

        partyMap[party.owningKey] = camelName
        usedPartyNames.add(camelName)

        return camelName
     }

    init {
        parties.forEach {
            println("val ${createPartyName(it)} = Party(\"${it.name.organisation}\", \"${it.owningKey.toStringShort()}\")")
        }
    }

    fun prettyPrint(per: Perceivable<Boolean>, x: Boolean? = null) {
        when (per) {
            is Const -> print("\"${per.value}\"")
            is PerceivableOr -> {
                prettyPrint(per.left)
                print(" or ")
                prettyPrint(per.right)
            }
            is PerceivableAnd -> {
                prettyPrint(per.left)
                print(" and ")
                prettyPrint(per.right)
            }
            is TimePerceivable -> {
                when (per.cmp) {
                    Comparison.GT, Comparison.GTE ->  {
                        print("after(")
                        prettyPrint(per.instant)
                        print(")")
                    }
                    Comparison.LT, Comparison.LTE -> {
                        print("before(")
                        prettyPrint(per.instant)
                        print(")")
                    }
                }
            }
            is PerceivableComparison<*> -> {
                when (per.type) {
                    BigDecimal::class.java -> prettyPrint(per.left as Perceivable<BigDecimal>)
                    Instant::class.java -> prettyPrint(per.left as Perceivable<Instant>)
                    Boolean::class.java -> prettyPrint(per.left as Perceivable<Boolean>)
                }
                when (per.cmp) {
                    Comparison.GT -> print(" > ")
                    Comparison.LT -> print(" < ")
                    Comparison.GTE -> print(" >= ")
                    Comparison.LTE -> print(" <= ")
                }
                when (per.type) {
                    BigDecimal::class.java -> prettyPrint(per.right as Perceivable<BigDecimal>)
                    Instant::class.java -> prettyPrint(per.right as Perceivable<Instant>)
                    Boolean::class.java -> prettyPrint(per.right as Perceivable<Boolean>)
                }
            }
            is TerminalEvent -> print("TerminalEvent(${partyMap[per.reference.owningKey]}, \"${per.source}\")")
            is ActorPerceivable -> print("signedBy(${partyMap[per.actor.owningKey]})")
            else -> print(per)
        }
    }

    fun prettyPrint(per: Perceivable<Instant>, x: Instant? = null) {
        when (per) {
            is Const -> print("\"${per.value}\"")
            is StartDate -> print("startDate")
            is EndDate -> print("endDate")
            else -> print(per)
        }
    }

    fun prettyPrint(per: Perceivable<BigDecimal>, x: BigDecimal? = null) {
        when (per) {
            is PerceivableOperation<BigDecimal> -> {
                prettyPrint(per.left)
                when (per.op) {
                    Operation.PLUS -> print(" + ")
                    Operation.MINUS -> print(" - ")
                    Operation.DIV -> print(" / ")
                    Operation.TIMES -> print(" * ")
                    else -> print(per.op)
                }
                prettyPrint(per.right)
            }
            is UnaryPlus -> {
                print("(")
                prettyPrint(per.arg)
                print(".).plus()")
            }
            is Const -> print(per.value)
            is Interest -> {
                print("Interest(")
                prettyPrint(per.amount)
                print(", \"${per.dayCountConvention}\", ")
                prettyPrint(per.amount)
                print(", ")
                prettyPrint(per.start)
                print(", ")
                prettyPrint(per.end)
                print(")")
            }
            is CurrencyCross -> print("${per.foreign}/${per.domestic}")
            else -> println(per)
        }
    }

    fun prettyPrint(arr: Arrangement) {

        when (arr) {
            is Zero -> println("zero")
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
            is Continuation -> println("next()")
            is Obligation -> {
                print("${partyMap[arr.from.owningKey]}.gives( ${partyMap[arr.to.owningKey]}, ")
                prettyPrint(arr.amount)
                println(", ${arr.currency})")
            }
            is Actions -> {
                println("actions {")
                indent {
                    for ((name, condition, arrangement) in arr.actions) {
                        print("\"$name\".givenThat(")
                        prettyPrint(condition)
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

