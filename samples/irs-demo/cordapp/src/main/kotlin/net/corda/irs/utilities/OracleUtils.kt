package net.corda.irs.utilities

import net.corda.core.contracts.TimeWindow
import net.corda.core.utilities.hours
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * This whole file exists as short cuts to get demos working.  In reality we'd have static data and/or rules engine
 * defining things like this.  It currently resides in the core module because it needs to be visible to the IRS
 * contract.
 */
// We at some future point may implement more than just this constant announcement window and thus use the params.
@Suppress("UNUSED_PARAMETER")
fun suggestInterestRateAnnouncementTimeWindow(index: String, source: String, date: LocalDate): TimeWindow {
    // TODO: we would ordinarily convert clock to same time zone as the index/source would announce in
    //       and suggest an announcement time for the interest rate
    // Here we apply a blanket announcement time of 11:45 London irrespective of source or index
    val time = LocalTime.of(11, 45)
    val zoneId = ZoneId.of("Europe/London")
    return TimeWindow.fromStartAndDuration(ZonedDateTime.of(date, time, zoneId).toInstant(), 24.hours)
}
