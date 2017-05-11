package net.corda.core.schemas.requery.converters

import io.requery.Converter
import java.sql.Timestamp
import java.time.Instant

/**
 * Converts from a [Instant] to a [java.sql.Timestamp] for Java 8. Note that
 * when converting between the time type and the database type all times will be converted to the
 * UTC zone offset.
 */
class InstantConverter : Converter<Instant, Timestamp> {
    override fun getMappedType() = Instant::class.java

    override fun getPersistedType() = Timestamp::class.java

    override fun getPersistedSize() = null

    override fun convertToPersisted(value: Instant?) = value?.let { Timestamp.from(it) }

    override fun convertToMapped(type: Class<out Instant>, value: Timestamp?) = value?.toInstant()
}