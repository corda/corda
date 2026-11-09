package net.corda.nodeapi.internal.persistence

import jakarta.persistence.AttributeConverter

/**
 * Stores a `Boolean` as the single character Hibernate 5's `yes_no` and `true_false` types stored, in a column of JDBC type `CHAR`: the
 * mapping a CorDapp built for Corda 4.14 or earlier gets for `@Type(type = "yes_no")` and `@Type(type = "true_false")` when the node
 * converts it for Hibernate 7 (see [JavaxPersistenceTransformer.convertedHibernate5Types]).
 *
 * Hibernate 7 ships `YesNoConverter` and `TrueFalseConverter` for the same formats, but their database-side type is a `Character`, which
 * Hibernate 7 maps and reads differently from the Hibernate 5 types: it becomes `NCHAR` under the nationalised character data setting the
 * node uses on SQL Server, so schema validation rejects the `char(1)` column a 4.14 node created, and a value read from a `CHAR(n)` column
 * wider than one character (which Hibernate 5's own DDL produced on H2) fails to coerce. This converter keeps the database side a
 * `String`, written as the single character and read by its first non-blank character, case-insensitively, as Hibernate 5 read it, and
 * the transformer pairs it with `@JdbcTypeCode(SqlTypes.CHAR)` so that the column type stays `CHAR` on every database.
 */
abstract class SingleCharacterBooleanConverter(private val trueValue: Char, private val falseValue: Char) : AttributeConverter<Boolean?, String?> {
    override fun convertToDatabaseColumn(attribute: Boolean?): String? = attribute?.let { (if (it) trueValue else falseValue).toString() }

    override fun convertToEntityAttribute(dbData: String?): Boolean? {
        val value = dbData?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return value[0].equals(trueValue, ignoreCase = true)
    }
}

/** 'Y' or 'N', as Hibernate 5's `yes_no` type stored a `Boolean`. */
class YesNoBooleanConverter : SingleCharacterBooleanConverter('Y', 'N')

/** 'T' or 'F', as Hibernate 5's `true_false` type stored a `Boolean`. */
class TrueFalseBooleanConverter : SingleCharacterBooleanConverter('T', 'F')
