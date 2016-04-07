/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

import org.junit.Test
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FinanceTypesTest {

    @Test
    fun `make sure Amount has decimal places`() {
        var x = Amount(1, Currency.getInstance("USD"))
        assert("0.01" in x.toString())
    }

    @Test
    fun `valid tenor tests`() {
        val exampleTenors = ("ON,1D,2D,3D,4D,5D,6D,7D,1W,2W,3W,1M,3M,6M,1Y,2Y,3Y,5Y,10Y,12Y,20Y").split(",")
        exampleTenors.all { Tenor(it).name.length > 0 } // Slightly obtuse way of ensuring no exception thrown in construction.
    }

    @Test
    fun `invalid tenor tests`() {
        val exampleTenors = ("W,M,D,Z,2Q,p0,W1").split(",")
        for (t in exampleTenors) {
            assertFailsWith<java.lang.IllegalArgumentException> { Tenor(t) }
        }
    }

    @Test
    fun `schedule generator 1`() {
        var ret = BusinessCalendar.createGenericSchedule(startDate = LocalDate.of(2014, 11, 25), period = Frequency.Monthly, noOfAdditionalPeriods = 3)
        // We know that Jan 25th 2015 is on the weekend -> It should not be in this list returned.
        assert(!(LocalDate.of(2015, 1, 25) in ret))
        println(ret)
    }

    @Test
    fun `schedule generator 2`() {
        var ret = BusinessCalendar.createGenericSchedule(startDate = LocalDate.of(2015, 11, 25), period = Frequency.Monthly, noOfAdditionalPeriods = 3, calendar = BusinessCalendar.getInstance("London"), dateRollConvention = DateRollConvention.Following)
        // Xmas should not be in the list!
        assert(!(LocalDate.of(2015, 12, 25) in ret))
        println(ret)
    }


    @Test
    fun `create a UK calendar`() {
        val cal = BusinessCalendar.getInstance("London")
        val holdates = cal.holidayDates
        println(holdates)
        assert(LocalDate.of(2016, 12, 27) in holdates) // Christmas this year is at the weekend...
    }

    @Test
    fun `create a US UK calendar`() {
        val cal = BusinessCalendar.getInstance("London", "NewYork")
        assert(LocalDate.of(2016, 7, 4) in cal.holidayDates) // The most American of holidays
        assert(LocalDate.of(2016, 8, 29) in cal.holidayDates) // August Bank Holiday for brits only
        println("Calendar contains both US and UK holidays")
    }

    @Test
    fun `calendar test of modified following`() {
        val ldn = BusinessCalendar.getInstance("London")
        val result = ldn.applyRollConvention(LocalDate.of(2016, 12, 25), DateRollConvention.ModifiedFollowing)
        assert(result == LocalDate.of(2016, 12, 28))
    }

    @Test
    fun `calendar test of modified following pt 2`() {
        val ldn = BusinessCalendar.getInstance("London")
        val result = ldn.applyRollConvention(LocalDate.of(2016, 12, 31), DateRollConvention.ModifiedFollowing)
        assert(result == LocalDate.of(2016, 12, 30))
    }


    @Test
    fun `calendar test of modified previous`() {
        val ldn = BusinessCalendar.getInstance("London")
        val result = ldn.applyRollConvention(LocalDate.of(2016, 1, 1), DateRollConvention.ModifiedPrevious)
        assert(result == LocalDate.of(2016, 1, 4))
    }

    @Test
    fun `calendar test of previous`() {
        val ldn = BusinessCalendar.getInstance("London")
        val result = ldn.applyRollConvention(LocalDate.of(2016, 12, 25), DateRollConvention.Previous)
        assert(result == LocalDate.of(2016, 12, 23))
    }

    @Test
    fun `calendar test of following`() {
        val ldn = BusinessCalendar.getInstance("London")
        val result = ldn.applyRollConvention(LocalDate.of(2016, 12, 25), DateRollConvention.Following)
        assert(result == LocalDate.of(2016, 12, 28))
    }

    @Test
    fun `calendar date advancing`() {
        val ldn = BusinessCalendar.getInstance("London")
        val firstDay = LocalDate.of(2015, 12, 20)
        val expected = mapOf(0 to firstDay,
                1 to LocalDate.of(2015, 12, 21),
                2 to LocalDate.of(2015, 12, 22),
                3 to LocalDate.of(2015, 12, 23),
                4 to LocalDate.of(2015, 12, 24),
                5 to LocalDate.of(2015, 12, 29),
                6 to LocalDate.of(2015, 12, 30),
                7 to LocalDate.of(2015, 12, 31)
        )

        for ((inc, exp) in expected) {
            var result = ldn.moveBusinessDays(firstDay, DateRollDirection.FORWARD, inc)
            assertEquals(exp, result)
        }
    }

    @Test
    fun `calendar date preceeding`() {
        val ldn = BusinessCalendar.getInstance("London")
        val firstDay = LocalDate.of(2015, 12, 31)
        val expected = mapOf(0 to firstDay,
                1 to LocalDate.of(2015, 12, 30),
                2 to LocalDate.of(2015, 12, 29),
                3 to LocalDate.of(2015, 12, 24),
                4 to LocalDate.of(2015, 12, 23),
                5 to LocalDate.of(2015, 12, 22),
                6 to LocalDate.of(2015, 12, 21),
                7 to LocalDate.of(2015, 12, 18)
        )

        for ((inc, exp) in expected) {
            var result = ldn.moveBusinessDays(firstDay, DateRollDirection.BACKWARD, inc)
            assertEquals(exp, result)
        }

    }


}