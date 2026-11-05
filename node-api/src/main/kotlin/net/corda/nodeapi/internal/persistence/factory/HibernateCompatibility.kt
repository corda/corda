package net.corda.nodeapi.internal.persistence.factory

/**
 * Settings that retain the behaviour that Hibernate 5 had, so that existing databases (and the tables of CorDapps) continue to work
 * without any changes. Hibernate 6 and later changed these defaults. This is used by every place that creates a Hibernate session factory
 * or entity manager factory, so that they all behave in the same way.
 */
object HibernateCompatibility {
    /** The JDBC type that Hibernate 5 used for [java.util.UUID]s, apart from on PostgreSQL. A `SqlTypes` constant name. */
    const val DEFAULT_UUID_JDBC_TYPE = "BINARY"

    /** The JDBC type that Hibernate 5 used for [java.util.UUID]s on PostgreSQL, i.e. its native uuid type. */
    const val POSTGRESQL_UUID_JDBC_TYPE = "UUID"

    /** The JDBC type that Hibernate 5 used for [Boolean]s on Oracle, which Hibernate renders as `number(1,0)`. */
    const val ORACLE_BOOLEAN_JDBC_TYPE = "BIT"

    /**
     * @param preferredUuidJdbcType the JDBC type used to store [java.util.UUID]s that have no explicit mapping, as a `SqlTypes` constant
     * name. See [uuidJdbcTypeFor].
     * @param preferredBooleanJdbcType the JDBC type used to store [Boolean]s, as a `SqlTypes` constant name, or null to use the dialect's
     * default. See [booleanJdbcTypeFor].
     */
    fun settings(preferredUuidJdbcType: String, preferredBooleanJdbcType: String? = null): Map<String, String> {
        val settings = mapOf(
                // Entities with a plain @GeneratedValue used a single sequence called "hibernate_sequence" with an increment of 1.
                // Hibernate 6+ gives each entity its own sequence (e.g. node_infos_SEQ) with an increment of 50. The "legacy" naming
                // strategy restores both the single sequence and the increment of 1. "FIX" only matters if the increment of a sequence
                // differs from the mapping, which cannot happen with "legacy", and is set as a safeguard.
                "hibernate.id.db_structure_naming_strategy" to "legacy",
                "hibernate.id.sequence.increment_size_mismatch_strategy" to "FIX",
                // Instants were stored as a plain TIMESTAMP, normalised to UTC using "hibernate.jdbc.time_zone". Hibernate 6+ uses
                // TIMESTAMP WITH TIME ZONE, which is read and written using the session time zone of the database.
                "hibernate.type.preferred_instant_jdbc_type" to "TIMESTAMP",
                // UUIDs (without an explicit mapping) were stored as BINARY(16) apart from on PostgreSQL, where they were stored as a uuid.
                // Hibernate 6+ uses the database's native uuid type where there is one.
                "hibernate.type.preferred_uuid_jdbc_type" to preferredUuidJdbcType,
                // Doubles and floats on Oracle were stored as FLOAT. Hibernate 6+ uses BINARY_FLOAT and BINARY_DOUBLE.
                "hibernate.dialect.oracle.use_binary_floats" to "false",
                // Hibernate 6+ alters existing columns in "update" mode. Only create what is missing as before.
                "hibernate.schema_management_tool" to CordaSchemaManagementTool::class.java.name
        )
        // Booleans on Oracle were stored as number(1,0). From Oracle 23, Hibernate 7 uses the native boolean type instead, and the node
        // would then fail to validate the existing NUMBER(1) columns. Before Oracle 23, number(1,0) is still Hibernate 7's default.
        return if (preferredBooleanJdbcType != null) {
            settings + ("hibernate.type.preferred_boolean_jdbc_type" to preferredBooleanJdbcType)
        } else {
            settings
        }
    }

    /**
     * The settings for the database with the given JDBC URL. This is for the places that do not go through a [CordaSessionFactoryFactory].
     */
    fun settingsFor(jdbcUrl: String): Map<String, String> = settings(uuidJdbcTypeFor(jdbcUrl), booleanJdbcTypeFor(jdbcUrl))

    /**
     * The JDBC type that Hibernate 5 used for [java.util.UUID]s on the database with the given JDBC URL. This is for the places that do
     * not go through a [CordaSessionFactoryFactory]. The factories use the same values (see [PostgresSessionFactoryFactory]).
     */
    fun uuidJdbcTypeFor(jdbcUrl: String): String {
        return if (PostgresSessionFactoryFactory.isPostgreSql(jdbcUrl)) POSTGRESQL_UUID_JDBC_TYPE else DEFAULT_UUID_JDBC_TYPE
    }

    /**
     * The JDBC type that Hibernate 5 used for [Boolean]s on the database with the given JDBC URL, if Hibernate 7 might use a different one.
     * The Oracle session factory factory of Corda Enterprise uses the same value.
     */
    fun booleanJdbcTypeFor(jdbcUrl: String): String? = if (isOracle(jdbcUrl)) ORACLE_BOOLEAN_JDBC_TYPE else null

    /** Whether the JDBC URL is for Oracle. */
    fun isOracle(jdbcUrl: String): Boolean = jdbcUrl.contains(":oracle")
}
