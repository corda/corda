package net.corda.nodeapi.internal.persistence

import jakarta.persistence.AttributeConverter

/**
 * Reads a single-character `CHAR` column the way Hibernate 5 did: its first character, the rest being padding, so that a column wider than
 * one character (Hibernate 5's own DDL made a `Character` or `yes_no` column `CHAR(255)` on H2, which comes back space-padded) reads as the
 * character it holds, and a stored space reads as a space. Hibernate 7's own `Character` mapping rejects such a value (`CoercionException:
 * value contains more than one character`), and its `YesNoConverter` and `TrueFalseConverter` have the same `Character` on the database
 * side, and besides become `NCHAR` under the nationalised character data setting the node uses on SQL Server, where a 4.14 node created
 * `char(1)`.
 *
 * These converters keep the database side a `String`, and the transformer pairs them with `@JdbcTypeCode(SqlTypes.CHAR)` so that the
 * column type stays `CHAR` on every database. They are the mapping a CorDapp built for Corda 4.14 or earlier gets for
 * `@Type(type = "yes_no")`, `"true_false"` and `"character"` when the node converts it for Hibernate 7 (see
 * [JavaxPersistenceTransformer.convertedHibernate5Types] and [JavaxPersistenceTransformer.hibernate5BasicTypeClasses]).
 */
object SingleCharacterColumn {
    /**
     * The character a `CHAR` column holds (its first; the rest is padding), or null for a null value, as Hibernate 5 read it: a stored
     * space is a space, not null, so trimming would be wrong. An empty string, which only a `VARCHAR` column can hold (`CHAR` pads it,
     * Oracle stores it as null) and which schema validation refuses for these types, reads as null, where Hibernate 5 failed with a
     * `StringIndexOutOfBoundsException`; the boolean converters read it as false, as Hibernate 5 did.
     */
    fun read(dbData: String?): Char? = dbData?.firstOrNull()
}

/** A `Character` in a `CHAR` column, as Hibernate 5's `character` type stored it. */
class SingleCharacterConverter : AttributeConverter<Char?, String?> {
    override fun convertToDatabaseColumn(attribute: Char?): String? = attribute?.toString()
    override fun convertToEntityAttribute(dbData: String?): Char? = SingleCharacterColumn.read(dbData)
}

/**
 * A `Boolean` as one of two characters in a `CHAR` column, compared case-insensitively on the way in, as Hibernate 5's types did: anything
 * but the true character, a blank or an empty string included, reads as false; only null reads as null.
 */
abstract class SingleCharacterBooleanConverter(private val trueValue: Char, private val falseValue: Char) : AttributeConverter<Boolean?, String?> {
    override fun convertToDatabaseColumn(attribute: Boolean?): String? = attribute?.let { (if (it) trueValue else falseValue).toString() }
    override fun convertToEntityAttribute(dbData: String?): Boolean? {
        return dbData?.let { SingleCharacterColumn.read(it)?.equals(trueValue, ignoreCase = true) ?: false }
    }
}

/** 'Y' or 'N', as Hibernate 5's `yes_no` type stored a `Boolean`. */
class YesNoBooleanConverter : SingleCharacterBooleanConverter('Y', 'N')

/** 'T' or 'F', as Hibernate 5's `true_false` type stored a `Boolean`. */
class TrueFalseBooleanConverter : SingleCharacterBooleanConverter('T', 'F')
