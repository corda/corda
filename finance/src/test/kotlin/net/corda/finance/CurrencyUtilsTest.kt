package net.corda.finance

import net.corda.core.contracts.*
import org.junit.Test
import kotlin.test.assertEquals

class CurrencyUtilsTest {
    @Test
    fun `basic currency`() {
        val expected = 1000L
        val amount = Amount(expected, GBP)
        assertEquals(expected, amount.quantity)
    }

    @Test
    fun parseCurrency() {
        assertEquals(Amount(1234L, GBP), parseCurrency("£12.34"))
        assertEquals(Amount(1200L, GBP), parseCurrency("£12"))
        assertEquals(Amount(1000L, USD), parseCurrency("$10"))
        assertEquals(Amount(5000L, JPY), parseCurrency("¥5000"))
        assertEquals(Amount(500000L, RUB), parseCurrency("₽5000"))
        assertEquals(Amount(1500000000L, CHF), parseCurrency("15,000,000 CHF"))
    }

    @Test
    fun rendering() {
        assertEquals("5000 JPY", parseCurrency("¥5000").toString())
        assertEquals("50.12 USD", parseCurrency("$50.12").toString())
    }
}