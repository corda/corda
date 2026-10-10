package net.corda.nodeapi.internal.persistence.factory

import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.toHexString
import org.hibernate.boot.model.TypeContributions
import org.hibernate.boot.model.TypeContributor
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.engine.jdbc.spi.JdbcServices
import org.hibernate.service.ServiceRegistry
import org.hibernate.type.SqlTypes
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.type.descriptor.java.AbstractClassJavaType
import org.hibernate.type.descriptor.java.PrimitiveByteArrayJavaType
import org.hibernate.type.descriptor.jdbc.VarbinaryJdbcType

/**
 * JDBC type codes private to Corda. Use with [org.hibernate.annotations.JdbcTypeCode].
 */
object CordaSqlTypes {
    /**
     * A `byte[]` stored as a regular BLOB on every database except PostgreSQL, where it is stored as `bytea`.
     * This is required for the checkpoint tables, as a workaround for the issue that PostgreSQL has on Azure with large objects.
     */
    const val CORDA_BLOB = 100_001
}

/**
 * Registers Corda's custom Hibernate types. This is discovered by Hibernate via [java.util.ServiceLoader], which runs it
 * after the [org.hibernate.dialect.Dialect] has contributed its own types, so that the dialect's mappings can be reused.
 */
class CordaTypeContributor : TypeContributor {
    override fun contribute(typeContributions: TypeContributions, serviceRegistry: ServiceRegistry) {
        val jdbcTypeRegistry = typeContributions.typeConfiguration.jdbcTypeRegistry
        val dialect = serviceRegistry.requireService(JdbcServices::class.java).dialect
        val blobType = if (dialect is PostgreSQLDialect) {
            VarbinaryJdbcType.INSTANCE
        } else {
            jdbcTypeRegistry.getDescriptor(SqlTypes.BLOB)
        }
        jdbcTypeRegistry.addDescriptor(CordaSqlTypes.CORDA_BLOB, blobType)
        // Truncate logged byte arrays to avoid OOM when large blobs might get logged.
        typeContributions.contributeJavaType(CordaPrimitiveByteArrayJavaType)
        typeContributions.contributeJavaType(AbstractPartyJavaType)
    }

    /**
     * Makes [AbstractParty] an immutable type for Hibernate.
     *
     * An [AbstractParty] attribute is mapped using an attribute converter, which has to look the party up (in the identity service) when
     * it converts the database value. Hibernate treats a converted attribute as mutable unless its Java type is immutable, and it copies
     * the value of a mutable one by passing it through the converter in both directions. That happens every time an entity is loaded
     * (to take a snapshot for dirty checking) and when an entity is merged, which would cost an extra identity lookup each time, and
     * could even cause the database to be accessed in the middle of a flush.
     *
     * The conversion itself is still done by the attribute converter, so this only needs to provide the Java type.
     */
    object AbstractPartyJavaType : AbstractClassJavaType<AbstractParty>(AbstractParty::class.java) {
        override fun fromString(string: CharSequence?): AbstractParty? {
            throw UnsupportedOperationException("An AbstractParty is converted by its attribute converter")
        }

        override fun <X : Any?> wrap(value: X?, options: WrapperOptions?): AbstractParty? {
            if (value == null) {
                return null
            }
            return value as? AbstractParty ?: throw unknownWrap(value.javaClass)
        }

        override fun <X : Any?> unwrap(value: AbstractParty?, type: Class<X>?, options: WrapperOptions?): X? {
            return when {
                value == null -> null
                type != null && type.isAssignableFrom(AbstractParty::class.java) -> type.cast(value)
                else -> throw unknownUnwrap(type)
            }
        }
    }

    // A tweaked version of `org.hibernate.type.descriptor.java.PrimitiveByteArrayJavaType` that truncates logged messages. Also logs in hex.
    object CordaPrimitiveByteArrayJavaType : PrimitiveByteArrayJavaType() {
        private const val LOG_SIZE_LIMIT = 1024

        override fun extractLoggableRepresentation(value: ByteArray?): String {
            return if (value == null) {
                super.extractLoggableRepresentation(value)
            } else {
                if (value.size <= LOG_SIZE_LIMIT) {
                    "[size=${value.size}, value=${value.toHexString()}]"
                } else {
                    "[size=${value.size}, value=${value.copyOfRange(0, LOG_SIZE_LIMIT).toHexString()}...truncated...]"
                }
            }
        }
    }
}
