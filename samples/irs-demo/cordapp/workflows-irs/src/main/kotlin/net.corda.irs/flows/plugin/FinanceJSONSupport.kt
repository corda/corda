@file:JvmName("FinanceJSONSupport")

package net.corda.irs.flows.plugin

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.std.StringArrayDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.finance.contracts.BusinessCalendar
import net.corda.finance.contracts.Expression
import net.corda.finance.workflows.utils.TEST_CALENDAR_NAMES
import net.corda.finance.workflows.utils.loadTestCalendar
import java.time.LocalDate
import java.util.*

fun registerFinanceJSONMappers(objectMapper: ObjectMapper) {
    val financeModule = SimpleModule("finance").apply {
        addSerializer(BusinessCalendar::class.java, CalendarSerializer)
        addDeserializer(BusinessCalendar::class.java, CalendarDeserializer)
        addSerializer(Expression::class.java, ExpressionSerializer)
        addDeserializer(Expression::class.java, ExpressionDeserializer)
    }
    objectMapper.registerModule(financeModule)
}

data class BusinessCalendarWrapper(val holidayDates: SortedSet<LocalDate>) {
    fun toCalendar() = BusinessCalendar(holidayDates)
}

object CalendarSerializer : JsonSerializer<BusinessCalendar>() {
    override fun serialize(obj: BusinessCalendar, generator: JsonGenerator, context: SerializerProvider) {
        val calendarName = TEST_CALENDAR_NAMES.find { loadTestCalendar(it) == obj }
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
                StringArrayDeserializer.instance.deserialize(parser, context).fold(BusinessCalendar.EMPTY) { acc, name -> acc + loadTestCalendar(name) }
            } catch (e: Exception) {
                parser.readValueAs(BusinessCalendarWrapper::class.java).toCalendar()
            }
        } catch (e: Exception) {
            throw JsonParseException(parser, "Invalid calendar(s) ${parser.text}: ${e.message}")
        }
    }
}

object ExpressionSerializer : JsonSerializer<Expression>() {
    override fun serialize(expr: Expression, generator: JsonGenerator, provider: SerializerProvider) = generator.writeString(expr.expr)
}

object ExpressionDeserializer : JsonDeserializer<Expression>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Expression = Expression(parser.text)
}
