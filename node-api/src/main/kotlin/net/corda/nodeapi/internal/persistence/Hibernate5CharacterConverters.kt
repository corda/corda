package net.corda.nodeapi.internal.persistence

import jakarta.persistence.AttributeConverter

/**
 * Reads a single-character `CHAR` column the way Hibernate 5 did: the first non-blank character, so that a column wider than one
 * character (Hibernate 5's own DDL made a `Character` or `yes_no` column `CHAR(255)` on H2, which comes back space-padded) reads as the
 * character it holds. Hibernate 7's own `Character` mapping rejects such a value (`CoercionException: value contains more than one
 * character`), and its `YesNoConverter` and `TrueFalseConverter` have the same `Character` on the database side, and besides become `NCHAR`
 * under the nationalised character data setting the node uses on SQL Server, where a 4.14 node created `char(1)`.
 *
 * These converters keep the database side a `String`, and the transformer pairs them with `@JdbcTypeCode(SqlTypes.CHAR)` so that the
 * column type stays `CHAR` on every database. They are the mapping a CorDapp built for Corda 4.14 or earlier gets for
 * `@Type(type = "yes_no")`, `"true_false"` and `"character"` when the node converts it for Hibernate 7 (see
 * [JavaxPersistenceTransformer.convertedHibernate5Types] and [JavaxPersistenceTransformer.hibernate5BasicTypeClasses]).
 */
object SingleCharacterColumn {
    /** The character a `CHAR` column holds, or null for a null or blank value. */
    fun read(dbData: String?): Char? = dbData?.trim()?.firstOrNull()
}

/** A `Character` in a `CHAR` column, as Hibernate 5's `character` type stored it. */
class SingleCharacterConverter : AttributeConverter<Char?, String?> {
    override fun convertToDatabaseColumn(attribute: Char?): String? = attribute?.toString()
    override fun convertToEntityAttribute(dbData: String?): Char? = SingleCharacterColumn.read(dbData)
}

/** A `Boolean` as one of two characters in a `CHAR` column, compared case-insensitively on the way in, as Hibernate 5's types did. */
abstract class SingleCharacterBooleanConverter(private val trueValue: Char, private val falseValue: Char) : AttributeConverter<Boolean?, String?> {
    override fun convertToDatabaseColumn(attribute: Boolean?): String? = attribute?.let { (if (it) trueValue else falseValue).toString() }
    override fun convertToEntityAttribute(dbData: String?): Boolean? = SingleCharacterColumn.read(dbData)?.equals(trueValue, ignoreCase = true)
}

/** 'Y' or 'N', as Hibernate 5's `yes_no` type stored a `Boolean`. */
class YesNoBooleanConverter : SingleCharacterBooleanConverter('Y', 'N')

/** 'T' or 'F', as Hibernate 5's `true_false` type stored a `Boolean`. */
class TrueFalseBooleanConverter : SingleCharacterBooleanConverter('T', 'F')
