
package net.corda.core.crypto.internal

import java.math.BigInteger
import java.math.BigInteger.TWO
import java.security.spec.EdECPoint

/**
 * Parameters for Curve25519, as defined in https://www.rfc-editor.org/rfc/rfc7748#section-4.1.
 */
@Suppress("MagicNumber")
object Curve25519 {
    val p = TWO.pow(255) - 19.toBigInteger()  // 2^255 - 19
    val d = ModP(BigInteger("37095705934669439343138083508754565189542113879843219016388785533085940283555"))

    val EdECPoint.isOnCurve25519: Boolean
        // https://www.rfc-editor.org/rfc/rfc8032.html#section-5.1.3
        get() {
            if (y >= p) return false
            val ySquared = ModP(y).pow(TWO)
            val u = ySquared - 1  // y^2 - 1 (mod p)
            val v = d * ySquared + 1 // dy^2 + 1 (mod p)
            val x = (u / v).pow((p + 3.toBigInteger()).shiftRight(3))  // (u/v)^((p+3)/8) (mod p)
            val vxSquared = v * x.pow(TWO)
            return vxSquared == u || vxSquared == -u
        }

    fun BigInteger.modP(): ModP = ModP(mod(p))

    private fun BigInteger.additiveInverse(): BigInteger = p - this

    data class ModP(val value: BigInteger) : Comparable<ModP> {
        fun pow(exponent: BigInteger): ModP = ModP(value.modPow(exponent, p))

        operator fun unaryMinus(): ModP = ModP(value.additiveInverse())
        operator fun plus(other: ModP): ModP = (this.value + other.value).modP()
        operator fun plus(other: Int): ModP = (this.value + other.toBigInteger()).modP()
        operator fun minus(other: ModP): ModP = (this.value + other.value.additiveInverse()).modP()
        operator fun minus(other: Int): ModP = (this.value + other.toBigInteger().additiveInverse()).modP()
        operator fun times(other: ModP): ModP = (this.value * other.value).modP()
        operator fun div(other: ModP): ModP = (this.value * other.value.modInverse(p)).modP()

        override fun compareTo(other: ModP): Int = this.value.compareTo(other.value)
        override fun toString(): String = "$value (mod Curve25519 p)"
    }
}
