package net.corda.finance.contracts

import net.corda.core.serialization.CordaSerializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.Collections.emptySortedSet

/**
 * A business calendar performs date calculations that take into account national holidays and weekends. This is a
 * typical feature of financial contracts, in which a business may not want a payment event to fall on a day when
 * no staff are around to handle problems.
 */
@CordaSerializable
open class BusinessCalendar(val holidayDates: SortedSet<LocalDate>) {
    companion object {
        @JvmField
        val EMPTY = BusinessCalendar(emptySortedSet())

        @JvmStatic
        fun calculateDaysBetween(startDate: LocalDate,
                                 endDate: LocalDate,
                                 dcbYear: DayCountBasisYear,
                                 dcbDay: DayCountBasisDay): Int {
            // Right now we are only considering Actual/360 and 30/360 .. We'll do the rest later.
            // TODO: The rest.
            return when {
                dcbDay == DayCountBasisDay.DActual -> (endDate.toEpochDay() - startDate.toEpochDay()).toInt()
                dcbDay == DayCountBasisDay.D30 && dcbYear == DayCountBasisYear.Y360 -> ((endDate.year - startDate.year) * 360.0 + (endDate.monthValue - startDate.monthValue) * 30.0 + endDate.dayOfMonth - startDate.dayOfMonth).toInt()
                else -> TODO("Can't calculate days using convention $dcbDay / $dcbYear")
            }
        }

        /** Parses a date of the form YYYY-MM-DD, like 2016-01-10 for 10th Jan. */
        @JvmStatic
        fun parseDateFromString(it: String): LocalDate = LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)

        /** Calculates an event schedule that moves events around to ensure they fall on working days. */
        @JvmStatic
        fun createGenericSchedule(startDate: LocalDate,
                                  period: Frequency,
                                  calendar: BusinessCalendar = EMPTY,
                                  dateRollConvention: DateRollConvention = DateRollConvention.Following,
                                  noOfAdditionalPeriods: Int = Integer.MAX_VALUE,
                                  endDate: LocalDate? = null,
                                  periodOffset: Int? = null): List<LocalDate> {
            val ret = ArrayList<LocalDate>()
            var ctr = 0
            var currentDate = startDate

            while (true) {
                currentDate = getOffsetDate(currentDate, period)
                if (periodOffset == null || periodOffset <= ctr)
                    ret.add(calendar.applyRollConvention(currentDate, dateRollConvention))
                ctr += 1
                // TODO: Fix addl period logic
                if ((ctr > noOfAdditionalPeriods) || (currentDate >= endDate ?: currentDate))
                    break
            }
            return ret
        }

        /**
         * Calculates the date from @startDate moving forward 'steps' of time size 'period'. Does not apply calendar
         * logic / roll conventions.
         */
        @JvmStatic
        fun getOffsetDate(startDate: LocalDate, period: Frequency, steps: Int = 1): LocalDate {
            if (steps == 0) return startDate
            return period.offset(startDate, steps.toLong())
        }
    }

    operator fun plus(other: BusinessCalendar): BusinessCalendar = BusinessCalendar((this.holidayDates + other.holidayDates).toSortedSet())

    override fun equals(other: Any?): Boolean = other is BusinessCalendar && this.holidayDates == other.holidayDates

    override fun hashCode(): Int = holidayDates.hashCode()

    open fun isWorkingDay(date: LocalDate): Boolean =
            when {
                date.dayOfWeek == DayOfWeek.SATURDAY -> false
                date.dayOfWeek == DayOfWeek.SUNDAY -> false
                holidayDates.contains(date) -> false
                else -> true
            }

    open fun applyRollConvention(testDate: LocalDate, dateRollConvention: DateRollConvention): LocalDate {
        if (dateRollConvention == DateRollConvention.Actual) return testDate

        var direction = dateRollConvention.direction().value
        var trialDate = testDate
        while (!isWorkingDay(trialDate)) {
            trialDate = trialDate.plusDays(direction)
        }

        // We've moved to the next working day in the right direction, but if we're using the "modified" date roll
        // convention and we've crossed into another month, reverse the direction instead to stay within the month.
        // Probably better explained here: http://www.investopedia.com/terms/m/modifiedfollowing.asp

        if (dateRollConvention.isModified && testDate.month != trialDate.month) {
            direction = -direction
            trialDate = testDate
            while (!isWorkingDay(trialDate)) {
                trialDate = trialDate.plusDays(direction)
            }
        }
        return trialDate
    }

    /**
     * Returns a date which is the inbound date plus/minus a given number of business days.
     * TODO: Make more efficient if necessary
     */
    fun moveBusinessDays(date: LocalDate, direction: DateRollDirection, i: Int): LocalDate {
        require(i >= 0){"Days to add/subtract must be positive"}
        if (i == 0) return date
        var retDate = date
        var ctr = 0
        while (ctr < i) {
            retDate = retDate.plusDays(direction.value)
            if (isWorkingDay(retDate)) ctr++
        }
        return retDate
    }

    override fun toString(): String = "BusinessCalendar($holidayDates)"
}
