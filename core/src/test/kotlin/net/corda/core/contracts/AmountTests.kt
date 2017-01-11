package net.corda.core.contracts

import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

/**
 * Tests of the [Amount] class.
 */
class AmountTests {
    @Test
    fun basicCurrency() {
        val expected = 1000L
        val amount = Amount(expected, GBP)
        assertEquals(expected, amount.quantity)
    }

    @Test
    fun decimalConversion() {
        val quantity = 1234L
        val amount = Amount(quantity, GBP)
        val expected = BigDecimal("12.34")
        assertEquals(expected, amount.toDecimal())
        assertEquals(amount, Amount.fromDecimal(amount.toDecimal(), amount.token))
    }
}