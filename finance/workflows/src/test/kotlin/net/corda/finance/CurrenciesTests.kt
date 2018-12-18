package net.corda.finance

import net.corda.core.contracts.Amount
import org.junit.Test
import kotlin.test.assertEquals

class CurrenciesTests {
    @Test
    fun `basic currency`() {
        val expected = 1000L
        val amount = Amount(expected, GBP)
        assertEquals(expected, amount.quantity)
    }

    @Test
    fun parseCurrency() {
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