/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("FinanceJSONSupport")

package net.corda.finance.plugin

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.std.StringArrayDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.finance.contracts.BusinessCalendar
import java.time.LocalDate

fun registerFinanceJSONMappers(objectMapper: ObjectMapper) {
    val financeModule = SimpleModule("finance").apply {
        addSerializer(BusinessCalendar::class.java, CalendarSerializer)
        addDeserializer(BusinessCalendar::class.java, CalendarDeserializer)
    }
    objectMapper.registerModule(financeModule)
}

data class BusinessCalendarWrapper(val holidayDates: List<LocalDate>) {
    fun toCalendar() = BusinessCalendar(holidayDates)
}

object CalendarSerializer : JsonSerializer<BusinessCalendar>() {
    override fun serialize(obj: BusinessCalendar, generator: JsonGenerator, context: SerializerProvider) {
        val calendarName = BusinessCalendar.calendars.find { BusinessCalendar.getInstance(it) == obj }
        if (calendarName != null) {
            generator.writeString(calendarName)
        } else {
            generator.writeObject(BusinessCalendarWrapper(obj.holidayDates))
        }
    }
}

object CalendarDeserializer : JsonDeserializer<BusinessCalendar>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): BusinessCalendar {
        return try {
            try {
                val array = StringArrayDeserializer.instance.deserialize(parser, context)
                BusinessCalendar.getInstance(*array)
            } catch (e: Exception) {
                parser.readValueAs(BusinessCalendarWrapper::class.java).toCalendar()
            }
        } catch (e: Exception) {
            throw JsonParseException(parser, "Invalid calendar(s) ${parser.text}: ${e.message}")
        }
    }
}
