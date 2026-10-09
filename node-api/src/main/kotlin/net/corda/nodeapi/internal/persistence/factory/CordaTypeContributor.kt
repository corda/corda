package net.corda.nodeapi.internal.persistence.factory

import net.corda.core.utilities.toHexString
import org.hibernate.boot.model.TypeContributions
import org.hibernate.boot.model.TypeContributor
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.engine.jdbc.spi.JdbcServices
import org.hibernate.service.ServiceRegistry
import org.hibernate.type.SqlTypes
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
