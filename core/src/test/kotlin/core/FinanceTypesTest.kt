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

class FinanceTypesTest {

    @Test
    fun `make sure Amount has decimal places`() {
        var x = Amount(1, Currency.getInstance("USD"))
        assert("0.01" in x.toString())
    }


    @Test
    fun `schedule generator 1`() {
        var ret = BusinessCalendar.createGenericSchedule(startDate = LocalDate.of(2014, 11, 25), period = Frequency.Monthly, noOfAdditionalPeriods = 3)
        // We know that Jan 25th 2015 is on the weekend -> It should not be in this list returned.
        assert(! (LocalDate.of(2015,1,25) in ret))
        println(ret)
    }

    @Test
    fun `schedule generator 2`() {
        var ret = BusinessCalendar.createGenericSchedule(startDate = LocalDate.of(2015, 11, 25), period = Frequency.Monthly, noOfAdditionalPeriods = 3, calendar = BusinessCalendar.getInstance("London"), dateRollConvention = DateRollConvention.Following)
        // Xmas should not be in the list!
        assert(! (LocalDate.of(2015,12,25) in ret))
        println(ret)
    }


    @Test
    fun `create a UK calendar` () {
        val cal = BusinessCalendar.getInstance("London")
        val holdates = cal.holidayDates
        println(holdates)
        assert(LocalDate.of(2016,12,27) in holdates) // Christmas this year is at the weekend...
    }

    @Test
    fun `create a US UK calendar`() {
        val cal = BusinessCalendar.getInstance("London","NewYork")
        assert(LocalDate.of(2016,7,4) in cal.holidayDates) // The most American of holidays
        assert(LocalDate.of(2016,8,29) in cal.holidayDates) // August Bank Holiday for brits only
        println("Calendar contains both US and UK holidays")
    }

    @Test
    fun `calendar test of modified following` () {
        val ldn = BusinessCalendar.getInstance("London")
        val result = ldn.applyRollConvention(LocalDate.of(2016,12,25),DateRollConvention.ModifiedFollowing)
        assert(result == LocalDate.of(2016,12,28))
    }

    @Test
    fun `calendar test of modified following pt 2` () {
        val ldn = BusinessCalendar.getInstance("London")
        val result = ldn.applyRollConvention(LocalDate.of(2016,12,31),DateRollConvention.ModifiedFollowing)
        assert(result == LocalDate.of(2016,12,30))
    }


    @Test
    fun `calendar test of modified previous` () {
        val ldn = BusinessCalendar.getInstance("London")
        val result = ldn.applyRollConvention(LocalDate.of(2016,1,1),DateRollConvention.ModifiedPrevious)
        assert(result == LocalDate.of(2016,1,4))
    }

    @Test
    fun `calendar test of previous` () {
        val ldn = BusinessCalendar.getInstance("London")
        val result = ldn.applyRollConvention(LocalDate.of(2016,12,25),DateRollConvention.Previous)
        assert(result == LocalDate.of(2016,12,23))
    }

    @Test
    fun `calendar test of following` () {
        val ldn = BusinessCalendar.getInstance("London")
        val result = ldn.applyRollConvention(LocalDate.of(2016,12,25),DateRollConvention.Following)
        assert(result == LocalDate.of(2016,12,28))
    }





}