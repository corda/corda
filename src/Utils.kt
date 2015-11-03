import java.math.BigInteger
import java.security.PublicKey
import java.util.*
import kotlin.test.assertTrue
import kotlin.test.fail


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// REQUIREMENTS

fun requireThat(message: String, expression: Boolean) {
    if (!expression) throw IllegalArgumentException(message)
}

// To understand how requireThat works, read the section "type safe builders" on the Kotlin website:
//
//   https://kotlinlang.org/docs/reference/type-safe-builders.html
object Requirements {
    infix fun String.by(expr: Boolean) {
        if (!expr) throw IllegalArgumentException("Failed requirement: $this")
    }
}
fun requireThat(body: Requirements.() -> Unit) {
    Requirements.body()
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// CURRENCIES   (convenience accessors)

val USD = Currency.getInstance("USD")
val GBP = Currency.getInstance("GBP")
val CHF = Currency.getInstance("CHF")

val Int.DOLLARS: Amount get() = Amount(this, USD)
val Int.POUNDS: Amount get() = Amount(this, GBP)
val Int.SWISS_FRANCS: Amount get() = Amount(this, CHF)