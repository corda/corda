package net.corda.core.contracts

import net.corda.core.contracts.Balance.Companion.sumOrZero
import net.corda.finance.*
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests of the [Balance] class.
 *
 */
class BalanceTests {
    @Test
    fun `make sure Balance has decimal places`() {
        val x = Balance(1, Currency.getInstance("USD"))
        assertTrue("0.01" in x.toString())
    }

    @Test
    fun `make a balance with a negative quantity`() {
        val x = Balance(-100, Currency.getInstance("USD"))
        assertTrue("-1" in x.toString())
    }

    @Test
    fun `decimal conversion`() {
        val quantity = 1234L
        val amountGBP = Balance(quantity, GBP)
        val expectedGBP = BigDecimal("12.34")
        assertEquals(expectedGBP, amountGBP.toDecimal())
        assertEquals(amountGBP, Balance.fromDecimal(amountGBP.toDecimal(), amountGBP.token))
        val amountJPY = Balance(quantity, JPY)
        val expectedJPY = BigDecimal("1234")
        assertEquals(expectedJPY, amountJPY.toDecimal())
        assertEquals(amountJPY, Balance.fromDecimal(amountJPY.toDecimal(), amountJPY.token))
        val testAsset = TestAsset("GB0009997999")
        val amountBond = Balance(quantity, testAsset)
        val expectedBond = BigDecimal("123400")
        assertEquals(expectedBond, amountBond.toDecimal())
        assertEquals(amountBond, Balance.fromDecimal(amountBond.toDecimal(), amountBond.token))
    }

    data class TestAsset(val name: String) : TokenizableAssetInfo {
        override val displayTokenSize: BigDecimal = BigDecimal("100")
        override fun toString(): String = name
    }

    @Test
    fun split() {
        for (baseQuantity in 0..1000) {
            val baseBalance = Balance(baseQuantity.toLong(), GBP)
            for (partitionCount in 1..100) {
                val splits = baseBalance.splitEvenly(partitionCount)
                assertEquals(partitionCount, splits.size)
                assertEquals(baseBalance, splits.sumOrZero(baseBalance.token))
                val min = splits.min()!!
                val max = splits.max()!!
                assertTrue(max.quantity - min.quantity <= 1L, "Balance quantities should differ by at most one token")
            }
        }
    }
}