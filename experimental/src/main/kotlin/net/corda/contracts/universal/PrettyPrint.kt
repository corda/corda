package net.corda.contracts.universal

import java.math.BigDecimal
import java.time.Instant

/**
 * Created by sofusmortensen on 30/12/2016.
 */



class PrettyPrint
{
    val sb = StringBuilder()


    // fun print
}



fun prettyPrintPerInstant(per: Perceivable<Instant>, indentArg : Int) {

    val sb = StringBuilder()

    var indent = indentArg

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

fun prettyPrintPerBD(per: Perceivable<BigDecimal>, indentArg : Int) {
    var indent = indentArg

    fun println(message: Any?)
    {
        repeat(indent, { print(' ')})
        System.out.println(message)
    }

    when (per) {
        is PerceivableOperation<BigDecimal> -> {
            prettyPrintPerBD(per.left, indent + 2)
            when (per.op)
            {
                Operation.PLUS -> print(" + ")
                Operation.MINUS -> print(" - ")
                Operation.DIV -> print(" / ")
                Operation.TIMES -> print(" * ")
                else -> print(per.op)
            }
            prettyPrintPerBD(per.right, indent + 2)
        }
        is Const -> {
            print(per.value)
        }
        is Interest -> {
            println("Interest(")
            prettyPrintPerBD(per.amount, indent + 2)
            print(", \"${per.dayCountConvention}\", ")
            prettyPrintPerBD(per.amount, indent)
            print(", ")
            prettyPrintPerInstant(per.start, indent)
            print(", ")
            prettyPrintPerInstant(per.end, indent)
            print(")")
        }
        else -> println(per)
    }
}

fun prettyPrint(arr: Arrangement, indentArg: Int = 0) {

    var indent = indentArg

    fun println(message: Any?)
    {
        repeat(indent, { print(' ')})
        System.out.println(message)
    }

    when (arr) {
        is Zero -> print("zero")
        is RollOut -> {
            println("rollout(\"${arr.startDate}\", \"${arr.endDate}\", Frequency.${arr.frequency}) { ")
            prettyPrint(arr.template, indent + 2)
            println("}")
        }
        is And -> {
            for (it in arr.arrangements) {
                prettyPrint(it, indent + 2)
            }
        }
        is Continuation -> {
            println("next()")
        }

        is Obligation -> {
            println( "\"${arr.from.name}\".gives( \"${arr.to.name}\", ")
            prettyPrintPerBD(arr.amount, indent)
            println( ", ${arr.currency})" )
        }
        is Actions -> {
            println("actions {")
            indent += 2
            for (it in arr.actions) {
                println( "\"" + it.name + "\" anytime {")
                prettyPrint(it.arrangement, indent + 2)
                println( "}" )
            }
            indent -= 2
            println("}")
        }
        else -> println(arr)
    }
}