package net.corda.client.jfx.model

import net.corda.core.contracts.Amount
import net.corda.finance.CHF
import net.corda.finance.GBP
import net.corda.finance.RUB
import net.corda.finance.USD
import org.assertj.core.api.Assertions
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExchangeRateModelTest {

    companion object {
        private val instance = ExchangeRateModel().exchangeRate.value

        private fun assertEquals(one: Amount<Currency>, another: Amount<Currency>) {
            assertEquals(one.token, another.token)
            assertTrue("$one != $another", { (one.toDecimal() - another.toDecimal()).abs() < BigDecimal(0.01) })
        }
    }

    @Test
    fun `perform fx testing`() {
        val tenSwissies = Amount(10, BigDecimal.ONE, CHF)
        assertEquals(instance.exchangeAmount(tenSwissies, CHF), tenSwissies)

        val tenSwissiesInUsd = Amount(101, BigDecimal.ONE.divide(BigDecimal.TEN), USD)
        assertEquals(instance.exchangeAmount(tenSwissies, USD), tenSwissiesInUsd)

        assertEquals(instance.exchangeAmount(tenSwissiesInUsd, CHF), tenSwissies)

        val tenQuidInSwissies = Amount(1297, BigDecimal.ONE.divide(BigDecimal(100)), CHF)
        val tenQuid = Amount(10, BigDecimal.ONE, GBP)
        assertEquals(instance.exchangeAmount(tenQuid, CHF), tenQuidInSwissies)

        assertEquals(instance.exchangeAmount(tenQuidInSwissies, GBP), tenQuid)

        Assertions.assertThatThrownBy { instance.exchangeAmount(tenQuid, RUB) }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("No exchange rate for RUB")
    }
}