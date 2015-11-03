import com.google.common.io.BaseEncoding
import java.util.*

/** A simple class that wraps a byte array and makes the equals/hashCode/toString methods work as you actually expect */
open class OpaqueBytes(val bits: ByteArray) {
    companion object {
        fun of(vararg b: Byte) = OpaqueBytes(byteArrayOf(*b))
    }

    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other !is OpaqueBytes) return false
        return Arrays.equals(bits, other.bits)
    }

    override fun hashCode() = Arrays.hashCode(bits)

    override fun toString() = "[" + BaseEncoding.base16().encode(bits) + "]"
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// REQUIREMENTS
//
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