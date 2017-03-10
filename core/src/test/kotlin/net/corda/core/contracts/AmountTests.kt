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

    @Test
    fun parsing() {
        assertEquals(Amount(1234L, GBP), Amount.parseCurrency("£12.34"))
        assertEquals(Amount(1200L, GBP), Amount.parseCurrency("£12"))
        assertEquals(Amount(1000L, USD), Amount.parseCurrency("$10"))
        assertEquals(Amount(5000L, JPY), Amount.parseCurrency("¥5000"))
        assertEquals(Amount(500000L, RUB), Amount.parseCurrency("₽5000"))
        assertEquals(Amount(1500000000L, CHF), Amount.parseCurrency("15,000,000 CHF"))
    }

    @Test
    fun rendering() {
        assertEquals("5000 JPY", Amount.parseCurrency("¥5000").toString())
        assertEquals("50.12 USD", Amount.parseCurrency("$50.12").toString())
    }
}