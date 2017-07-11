package net.corda.node.utilities

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.parsePublicKeyBase58
import net.corda.core.crypto.toBase58String
import org.bouncycastle.cert.X509CertificateHolder
import org.h2.jdbc.JdbcBlob
import org.jetbrains.exposed.sql.*
import java.io.ByteArrayInputStream
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*


/**
 * Table prefix for all tables owned by the node module.
 */
const val NODE_DATABASE_PREFIX = "node_"

// Composite columns for use with below Exposed helpers.
data class PartyColumns(val name: Column<String>, val owningKey: Column<PublicKey>)
data class PartyAndCertificateColumns(val name: Column<String>, val owningKey: Column<PublicKey>,
                                      val certificate: Column<X509CertificateHolder>, val certPath: Column<CertPath>)
data class StateRefColumns(val txId: Column<SecureHash>, val index: Column<Int>)
data class TxnNoteColumns(val txId: Column<SecureHash>, val note: Column<String>)

/**
 * [Table] column helpers for use with Exposed, as per [varchar] etc.
 */
fun Table.certificate(name: String) = this.registerColumn<X509CertificateHolder>(name, X509CertificateColumnType)
fun Table.certificatePath(name: String) = this.registerColumn<CertPath>(name, CertPathColumnType)
fun Table.publicKey(name: String) = this.registerColumn<PublicKey>(name, PublicKeyColumnType)
fun Table.secureHash(name: String) = this.registerColumn<SecureHash>(name, SecureHashColumnType)
fun Table.party(nameColumnName: String,
                keyColumnName: String) = PartyColumns(this.varchar(nameColumnName, length = 255), this.publicKey(keyColumnName))
fun Table.partyAndCertificate(nameColumnName: String,
                              keyColumnName: String,
                              certificateColumnName: String,
                              pathColumnName: String) = PartyAndCertificateColumns(this.varchar(nameColumnName, length = 255), this.publicKey(keyColumnName),
        this.certificate(certificateColumnName), this.certificatePath(pathColumnName))
fun Table.uuidString(name: String) = this.registerColumn<UUID>(name, UUIDStringColumnType)
fun Table.localDate(name: String) = this.registerColumn<LocalDate>(name, LocalDateColumnType)
fun Table.localDateTime(name: String) = this.registerColumn<LocalDateTime>(name, LocalDateTimeColumnType)
fun Table.instant(name: String) = this.registerColumn<Instant>(name, InstantColumnType)
fun Table.stateRef(txIdColumnName: String, indexColumnName: String) = StateRefColumns(this.secureHash(txIdColumnName), this.integer(indexColumnName))
fun Table.txnNote(txIdColumnName: String, txnNoteColumnName: String) = TxnNoteColumns(this.secureHash(txIdColumnName), this.text(txnNoteColumnName))

/**
 * [ColumnType] for marshalling to/from database on behalf of [X509CertificateHolder].
 */
object X509CertificateColumnType : ColumnType() {
    override fun sqlType(): String = "BLOB"

    override fun valueFromDB(value: Any): Any {
        val blob = value as JdbcBlob
        return X509CertificateHolder(blob.getBytes(0, blob.length().toInt()))
    }

    override fun notNullValueToDB(value: Any): Any = (value as X509CertificateHolder).encoded
}

/**
 * [ColumnType] for marshalling to/from database on behalf of [CertPath].
 */
object CertPathColumnType : ColumnType() {
    private val factory = CertificateFactory.getInstance("X.509")
    override fun sqlType(): String = "BLOB"

    override fun valueFromDB(value: Any): Any {
        val blob = value as JdbcBlob
        return factory.generateCertPath(ByteArrayInputStream(blob.getBytes(0, blob.length().toInt())))
    }

    override fun notNullValueToDB(value: Any): Any = (value as CertPath).encoded
}

/**
 * [ColumnType] for marshalling to/from database on behalf of [PublicKey].
 */
// TODO Rethink how we store CompositeKeys in db. Currently they are stored as Base58 strings and as we don't know the size
//  of a CompositeKey they could be CLOB fields. Given the time to fetch these types and that they are unsuitable as table keys,
//  having a shorter primary key (such as SHA256 hash or a UUID generated on demand) that references a common composite key table may make more sense.
object PublicKeyColumnType : ColumnType() {
    override fun sqlType(): String = "VARCHAR"

    override fun valueFromDB(value: Any): Any = parsePublicKeyBase58(value.toString())

    override fun notNullValueToDB(value: Any): Any = (value as? PublicKey)?.toBase58String() ?: value
}

/**
 * [ColumnType] for marshalling to/from database on behalf of [SecureHash].
 */
object SecureHashColumnType : ColumnType() {
    override fun sqlType(): String = "VARCHAR(64)"

    override fun valueFromDB(value: Any): Any = SecureHash.parse(value.toString())

    override fun notNullValueToDB(value: Any): Any = (value as? SecureHash)?.toString() ?: value
}

/**
 * [ColumnType] for marshalling to/from database on behalf of [UUID], always using a string representation.
 */
object UUIDStringColumnType : ColumnType() {
    override fun sqlType(): String = "VARCHAR(36)"

    override fun valueFromDB(value: Any): Any = UUID.fromString(value.toString())

    override fun notNullValueToDB(value: Any): Any = (value as? UUID)?.toString() ?: value
}

/**
 * [ColumnType] for marshalling to/from database on behalf of [java.time.LocalDate].
 */
object LocalDateColumnType : ColumnType() {
    override fun sqlType(): String = "DATE"

    override fun nonNullValueToString(value: Any): String {
        if (value is String) return value

        val localDate = when (value) {
            is LocalDate -> value
            is java.sql.Date -> value.toLocalDate()
            is java.sql.Timestamp -> value.toLocalDateTime().toLocalDate()
            else -> error("Unexpected value: $value")
        }
        return "'$localDate'"
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is java.sql.Date -> value.toLocalDate()
        is java.sql.Timestamp -> value.toLocalDateTime().toLocalDate()
        is Long -> LocalDate.from(Instant.ofEpochMilli(value))
        else -> value
    }

    override fun notNullValueToDB(value: Any): Any = if (value is LocalDate) {
        java.sql.Date(value.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    } else value
}


/**
 * [ColumnType] for marshalling to/from database on behalf of [java.time.LocalDateTime].
 */
object LocalDateTimeColumnType : ColumnType() {
    private val sqlType = DateColumnType(time = true).sqlType()
    override fun sqlType(): String = sqlType

    override fun nonNullValueToString(value: Any): String {
        if (value is String) return value

        val localDateTime = when (value) {
            is LocalDateTime -> value
            is java.sql.Date -> value.toLocalDate().atStartOfDay()
            is java.sql.Timestamp -> value.toLocalDateTime()
            else -> error("Unexpected value: $value")
        }
        return "'$localDateTime'"
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is java.sql.Date -> value.toLocalDate().atStartOfDay()
        is java.sql.Timestamp -> value.toLocalDateTime()
        is Long -> LocalDateTime.from(Instant.ofEpochMilli(value))
        else -> value
    }

    override fun notNullValueToDB(value: Any): Any = if (value is LocalDateTime) {
        java.sql.Timestamp(value.toInstant(ZoneOffset.UTC).toEpochMilli())
    } else value
}


/**
 * [ColumnType] for marshalling to/from database on behalf of [java.time.Instant].
 */
object InstantColumnType : ColumnType() {
    private val sqlType = DateColumnType(time = true).sqlType()
    override fun sqlType(): String = sqlType

    override fun nonNullValueToString(value: Any): String {
        if (value is String) return value

        val localDateTime = when (value) {
            is Instant -> value
            is java.sql.Date -> value.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC)
            is java.sql.Timestamp -> value.toLocalDateTime().toInstant(ZoneOffset.UTC)
            else -> error("Unexpected value: $value")
        }
        return "'$localDateTime'"
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is java.sql.Date -> value.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC)
        is java.sql.Timestamp -> value.toLocalDateTime().toInstant(ZoneOffset.UTC)
        is Long -> LocalDateTime.from(Instant.ofEpochMilli(value)).toInstant(ZoneOffset.UTC)
        else -> value
    }

    override fun notNullValueToDB(value: Any): Any = if (value is Instant) {
        java.sql.Timestamp(value.toEpochMilli())
    } else value
}
