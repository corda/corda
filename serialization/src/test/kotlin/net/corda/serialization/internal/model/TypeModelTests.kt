package net.corda.serialization.internal.model

import org.junit.Test

class TypeModelTests {

    data class Address(
            val addressLines: List<String>,
            val postcode: String
    )

    enum class TelephoneType { HOME, WORK, MOBILE }

    data class Person(
            val name: String,
            val age: Int,
            val address: Address,
            val telephone: Map<TelephoneType, String>)

    sealed class Currency {
        class USD : Currency()
        class EUR : Currency()
        class GBP : Currency()
    }

    data class CurrencyAmount<C: Currency>(val amount: Int)

    interface Salaried<C: Currency> {
        val baseSalary: CurrencyAmount<C>
    }

    enum class UKPayGrade constructor(baseSalaryGBP: Int): Salaried<Currency.GBP> {
        BAND_A(15000),
        BAND_B(30000),
        BAND_C(45000);

        override val baseSalary = CurrencyAmount<Currency.GBP>(baseSalaryGBP)
    }

    class Employee<C: Currency, R: Salaried<C>>(val person: Person, val payGrade: R, val currency: C)

    class PointedSet<T>(
            val leader: T,
            val followers: Array<T>)

    open class Team<C: Currency>(
            val name: String,
            val members: PointedSet<out Employee<C, *>>,
            val salaries: Array<out Salaried<C>>)

    class UKTeam(
            name: String,
            members: PointedSet<Employee<Currency.GBP, UKPayGrade>>): Team<Currency.GBP>(name, members, members.followers.map { it.payGrade }.toTypedArray())

    @Test
    fun describeComplexType() {
        val interpreter = LocalTypeInterpreter()
        val info = interpreter.interpret(UKTeam::class.java)
        println(info.prettyPrint())
    }

    data class ValueHolder<T>(val value: T)

    interface WithArray<V> {
        val values: Array<ValueHolder<V>>
    }

    class ConcreteClass(override val values: Array<ValueHolder<String>>) : WithArray<String>

    @Test
    fun arrayTypeResolution() {
        val interpreter = LocalTypeInterpreter()
        val info = interpreter.interpret(ConcreteClass::class.java)
        println(info.prettyPrint())
    }

    fun LocalTypeInformation.prettyPrint(): String {
        val sb = StringBuilder()
        var indent = 0
        for (c in this.toString().replace(", ", ",").replace("[]", "EMPTYLIST").replace("=", ": ")) {
            when (c) {
                '(', '[' -> {
                    indent += 1
                    sb.append(c).append("\n").append("  ".repeat(indent))
                }
                ')', ']' -> {
                    indent -=1
                    sb.append("\n").append("  ".repeat(indent)).append(c)
                }
                '{' -> {
                    indent += 1
                    sb.append("\n").append("  ".repeat(indent))
                }
                '}' -> {
                    indent -= 1
                }
                ',' -> {
                    sb.append("\n").append("  ".repeat(indent))
                }
                else -> sb.append(c)
            }
        }
        return sb.toString().replace("EMPTYLIST", "[]")
    }
}