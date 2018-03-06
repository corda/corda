/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.contracts

import net.corda.finance.*
import net.corda.core.contracts.Amount.Companion.sumOrZero
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import java.util.stream.Collectors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests of the [Amount] class.
 */
class AmountTests {
    @Test
    fun `make sure Amount has decimal places`() {
        val x = Amount(1, Currency.getInstance("USD"))
        assertTrue("0.01" in x.toString())
    }

    @Test
    fun `decimal conversion`() {
        val quantity = 1234L
        val amountGBP = Amount(quantity, GBP)
        val expectedGBP = BigDecimal("12.34")
        assertEquals(expectedGBP, amountGBP.toDecimal())
        assertEquals(amountGBP, Amount.fromDecimal(amountGBP.toDecimal(), amountGBP.token))
        val amountJPY = Amount(quantity, JPY)
        val expectedJPY = BigDecimal("1234")
        assertEquals(expectedJPY, amountJPY.toDecimal())
        assertEquals(amountJPY, Amount.fromDecimal(amountJPY.toDecimal(), amountJPY.token))
        val testAsset = TestAsset("GB0009997999")
        val amountBond = Amount(quantity, testAsset)
        val expectedBond = BigDecimal("123400")
        assertEquals(expectedBond, amountBond.toDecimal())
        assertEquals(amountBond, Amount.fromDecimal(amountBond.toDecimal(), amountBond.token))
    }

    data class TestAsset(val name: String) : TokenizableAssetInfo {
        override val displayTokenSize: BigDecimal = BigDecimal("100")
        override fun toString(): String = name
    }

    @Test
    fun split() {
        for (baseQuantity in 0..1000) {
            val baseAmount = Amount(baseQuantity.toLong(), GBP)
            for (partitionCount in 1..100) {
                val splits = baseAmount.splitEvenly(partitionCount)
                assertEquals(partitionCount, splits.size)
                assertEquals(baseAmount, splits.sumOrZero(baseAmount.token))
                val min = splits.min()!!
                val max = splits.max()!!
                assertTrue(max.quantity - min.quantity <= 1L, "Amount quantities should differ by at most one token")
            }
        }
    }

    @Test
    fun `amount transfers equality`() {
        val partyA = "A"
        val partyB = "B"
        val partyC = "C"
        val baseSize = BigDecimal("123.45")
        val transferA = AmountTransfer.fromDecimal(baseSize, GBP, partyA, partyB)
        assertEquals(baseSize, transferA.toDecimal())
        val transferB = AmountTransfer.fromDecimal(baseSize.negate(), GBP, partyB, partyA)
        assertEquals(baseSize.negate(), transferB.toDecimal())
        val transferC = AmountTransfer.fromDecimal(BigDecimal("123.40"), GBP, partyA, partyB)
        val transferD = AmountTransfer.fromDecimal(baseSize, USD, partyA, partyB)
        val transferE = AmountTransfer.fromDecimal(baseSize, GBP, partyA, partyC)
        assertEquals(transferA, transferA)
        assertEquals(transferA.hashCode(), transferA.hashCode())
        assertEquals(transferA, transferB)
        assertEquals(transferA.hashCode(), transferB.hashCode())
        assertNotEquals(transferC, transferA)
        assertNotEquals(transferC.hashCode(), transferA.hashCode())
        assertNotEquals(transferD, transferA)
        assertNotEquals(transferD.hashCode(), transferA.hashCode())
        assertNotEquals(transferE, transferA)
        assertNotEquals(transferE.hashCode(), transferA.hashCode())
    }

    @Test
    fun `amount transfer aggregation`() {
        val partyA = "A"
        val partyB = "B"
        val partyC = "C"
        val baseSize = BigDecimal("123.45")
        val simpleTransfer = AmountTransfer.fromDecimal(baseSize, GBP, partyA, partyB)
        val flippedTransfer = AmountTransfer.fromDecimal(baseSize.negate(), GBP, partyB, partyA)
        val doubleSizeTransfer = AmountTransfer.fromDecimal(baseSize.multiply(BigDecimal("2")), GBP, partyA, partyB)
        val differentTokenTransfer = AmountTransfer.fromDecimal(baseSize, USD, partyA, partyB)
        val differentPartyTransfer = AmountTransfer.fromDecimal(baseSize, GBP, partyA, partyC)
        val negativeTransfer = AmountTransfer.fromDecimal(baseSize.negate(), GBP, partyA, partyB)
        val zeroTransfer = AmountTransfer.zero(GBP, partyA, partyB)
        val sumFlipped1 = simpleTransfer + flippedTransfer
        val sumFlipped2 = flippedTransfer + simpleTransfer
        assertEquals(doubleSizeTransfer, sumFlipped1)
        assertEquals(doubleSizeTransfer, sumFlipped2)
        assertFailsWith(IllegalArgumentException::class) {
            simpleTransfer + differentTokenTransfer
        }
        assertFailsWith(IllegalArgumentException::class) {
            simpleTransfer + differentPartyTransfer
        }
        val sumsToZero = simpleTransfer + negativeTransfer
        assertEquals(zeroTransfer, sumsToZero)
        val sumFlippedToZero = flippedTransfer + negativeTransfer
        assertEquals(zeroTransfer, sumFlippedToZero)
        val sumUntilNegative = (flippedTransfer + negativeTransfer) + negativeTransfer
        assertEquals(negativeTransfer, sumUntilNegative)
    }

    @Test
    fun `amount transfer apply`() {
        val partyA = "A"
        val partyB = "B"
        val partyC = "C"
        val sourceAccounts = listOf(
                SourceAndAmount(partyA, DOLLARS(123), 1),
                SourceAndAmount(partyB, DOLLARS(100), 2),
                SourceAndAmount(partyC, DOLLARS(123), 3),
                SourceAndAmount(partyB, DOLLARS(100), 4),
                SourceAndAmount(partyA, POUNDS(256), 5),
                SourceAndAmount(partyB, POUNDS(256), 6)
        )
        val collector = Collectors.toMap<SourceAndAmount<Currency, String>, Pair<String, Currency>, BigDecimal>({ Pair(it.source, it.amount.token) }, { it.amount.toDecimal() }, { x, y -> x + y })
        val originalTotals = sourceAccounts.stream().collect(collector)

        val smallTransfer = AmountTransfer.fromDecimal(BigDecimal("10"), USD, partyA, partyB)
        val accountsAfterSmallTransfer = smallTransfer.apply(sourceAccounts, 10)
        val newTotals = accountsAfterSmallTransfer.stream().collect(collector)
        assertEquals(originalTotals[Pair(partyA, USD)]!! - BigDecimal("10.00"), newTotals[Pair(partyA, USD)])
        assertEquals(originalTotals[Pair(partyB, USD)]!! + BigDecimal("10.00"), newTotals[Pair(partyB, USD)])
        assertEquals(originalTotals[Pair(partyC, USD)], newTotals[Pair(partyC, USD)])
        assertEquals(originalTotals[Pair(partyA, GBP)], newTotals[Pair(partyA, GBP)])
        assertEquals(originalTotals[Pair(partyB, GBP)], newTotals[Pair(partyB, GBP)])

        val largeTransfer = AmountTransfer.fromDecimal(BigDecimal("150"), USD, partyB, partyC)
        val accountsAfterLargeTransfer = largeTransfer.apply(sourceAccounts, 10)
        val newTotals2 = accountsAfterLargeTransfer.stream().collect(collector)
        assertEquals(originalTotals[Pair(partyA, USD)], newTotals2[Pair(partyA, USD)])
        assertEquals(originalTotals[Pair(partyB, USD)]!! - BigDecimal("150.00"), newTotals2[Pair(partyB, USD)])
        assertEquals(originalTotals[Pair(partyC, USD)]!! + BigDecimal("150.00"), newTotals2[Pair(partyC, USD)])
        assertEquals(originalTotals[Pair(partyA, GBP)], newTotals2[Pair(partyA, GBP)])
        assertEquals(originalTotals[Pair(partyB, GBP)], newTotals2[Pair(partyB, GBP)])

        val tooLargeTransfer = AmountTransfer.fromDecimal(BigDecimal("150"), USD, partyA, partyB)
        assertFailsWith(IllegalArgumentException::class) {
            tooLargeTransfer.apply(sourceAccounts)
        }
        val emptyingTransfer = AmountTransfer.fromDecimal(BigDecimal("123"), USD, partyA, partyB)
        val accountsAfterEmptyingTransfer = emptyingTransfer.apply(sourceAccounts, 10)
        val newTotals3 = accountsAfterEmptyingTransfer.stream().collect(collector)
        assertEquals(null, newTotals3[Pair(partyA, USD)])
        assertEquals(originalTotals[Pair(partyB, USD)]!! + BigDecimal("123.00"), newTotals3[Pair(partyB, USD)])
        assertEquals(originalTotals[Pair(partyC, USD)], newTotals3[Pair(partyC, USD)])
        assertEquals(originalTotals[Pair(partyA, GBP)], newTotals3[Pair(partyA, GBP)])
        assertEquals(originalTotals[Pair(partyB, GBP)], newTotals3[Pair(partyB, GBP)])
    }
}