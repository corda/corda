package net.corda.core.schemas.requery.converters

import io.requery.Converter

import java.sql.*
import java.time.*

/**
 * Converts from a [Instant] to a [java.sql.Timestamp] for Java 8. Note that
 * when converting between the time type and the database type all times will be converted to the
 * UTC zone offset.
 */
class InstantConverter : Converter<Instant, Timestamp> {

    override fun getMappedType(): Class<Instant> { return Instant::class.java }

    override fun getPersistedType(): Class<Timestamp> { return Timestamp::class.java }

    override fun getPersistedSize(): Int? { return null }

    override fun convertToPersisted(value: Instant?): Timestamp? {
        if (value == null) { return null }
        return Timestamp.from(value)
    }

    override fun convertToMapped(type: Class<out Instant>, value: Timestamp?): Instant? {
        if (value == null) { return null }
        return value.toInstant()
    }
}