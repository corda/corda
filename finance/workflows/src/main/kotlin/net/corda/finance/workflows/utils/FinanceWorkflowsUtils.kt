package net.corda.finance.workflows.utils

import net.corda.core.flows.FlowException
import net.corda.core.serialization.CordaSerializable
import net.corda.finance.contracts.BusinessCalendar

val TEST_CALENDAR_NAMES = listOf("London", "NewYork")

fun loadTestCalendar(name: String): BusinessCalendar {
    val stream = UnknownCalendar::class.java.getResourceAsStream("/net/corda/finance/workflows/utils/${name}HolidayCalendar.txt") ?: throw UnknownCalendar(name)
    return stream.use {
        BusinessCalendar(stream.reader().readText().split(",").map { BusinessCalendar.parseDateFromString(it) }.toSortedSet())
    }
}

@CordaSerializable
class UnknownCalendar(name: String) : FlowException("Calendar $name not found")
