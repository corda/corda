package net.corda.nodeapi.internal.persistence.factory

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.dialect.OracleDialect
import org.junit.Test

class HibernateCompatibilityTest {
    private companion object {
        const val ORACLE_URL = "jdbc:oracle:thin:@localhost:1521:xe"
        const val POSTGRESQL_URL = "jdbc:postgresql://localhost:5432/corda"
    }

    @Test(timeout = 300_000)
    fun `uuids are stored natively on PostgreSQL and as binary elsewhere`() {
        assertThat(HibernateCompatibility.uuidJdbcTypeFor("jdbc:postgresql://localhost:5432/corda")).isEqualTo("UUID")
        assertThat(HibernateCompatibility.uuidJdbcTypeFor("jdbc:sqlserver://localhost:1433;databaseName=corda")).isEqualTo("BINARY")
        assertThat(HibernateCompatibility.uuidJdbcTypeFor("jdbc:oracle:thin:@localhost:1521:xe")).isEqualTo("BINARY")
        assertThat(HibernateCompatibility.uuidJdbcTypeFor("jdbc:h2:mem:test")).isEqualTo("BINARY")
    }

    @Test(timeout = 300_000)
    fun `the uuid type is chosen for the same databases as the session factory factory that the node uses`() {
        listOf("jdbc:postgresql://localhost:5432/corda", "jdbc:h2:mem:test", "jdbc:sqlserver://localhost:1433").forEach { jdbcUrl ->
            val expected = if (PostgresSessionFactoryFactory().canHandleDatabase(jdbcUrl)) {
                HibernateCompatibility.POSTGRESQL_UUID_JDBC_TYPE
            } else {
                HibernateCompatibility.DEFAULT_UUID_JDBC_TYPE
            }
            assertThat(HibernateCompatibility.uuidJdbcTypeFor(jdbcUrl)).isEqualTo(expected)
        }
    }

    @Test(timeout = 300_000)
    fun `booleans are stored as number(1,0) on Oracle and with the dialect's default elsewhere`() {
        assertThat(HibernateCompatibility.booleanJdbcTypeFor(ORACLE_URL)).isEqualTo("BIT")
        assertThat(HibernateCompatibility.booleanJdbcTypeFor(POSTGRESQL_URL)).isNull()
        assertThat(HibernateCompatibility.booleanJdbcTypeFor("jdbc:sqlserver://localhost:1433;databaseName=corda")).isNull()
        assertThat(HibernateCompatibility.booleanJdbcTypeFor("jdbc:h2:mem:test")).isNull()
    }

    @Test(timeout = 300_000)
    fun `the settings for a JDBC URL include the types for that database`() {
        assertThat(HibernateCompatibility.settingsFor(ORACLE_URL))
                .containsEntry("hibernate.type.preferred_uuid_jdbc_type", "BINARY")
                .containsEntry("hibernate.type.preferred_boolean_jdbc_type", "BIT")
        assertThat(HibernateCompatibility.settingsFor(POSTGRESQL_URL))
                .containsEntry("hibernate.type.preferred_uuid_jdbc_type", "UUID")
                .doesNotContainKey("hibernate.type.preferred_boolean_jdbc_type")
    }

    @Test(timeout = 300_000)
    fun `booleans match the NUMBER(1) columns that Liquibase created on every Oracle version`() {
        // This is what the setting prevents: from Oracle 23, Hibernate 7 expects a native boolean column, and validation would fail.
        assertThat(oracleBooleanColumnType(23, HibernateCompatibility.settings(HibernateCompatibility.DEFAULT_UUID_JDBC_TYPE)))
                .isEqualTo("boolean")
        listOf(19, 21, 23, 26).forEach { version ->
            assertThat(oracleBooleanColumnType(version, HibernateCompatibility.settingsFor(ORACLE_URL)))
                    .describedAs("Oracle $version")
                    .isEqualTo("number(1,0)")
        }
    }

    @Test(timeout = 300_000)
    fun `the settings that retain the Hibernate 5 behaviour are all present`() {
        assertThat(HibernateCompatibility.settings("BINARY")).containsExactlyInAnyOrderEntriesOf(mapOf(
                "hibernate.id.db_structure_naming_strategy" to "legacy",
                "hibernate.id.sequence.increment_size_mismatch_strategy" to "FIX",
                "hibernate.type.preferred_instant_jdbc_type" to "TIMESTAMP",
                "hibernate.type.preferred_uuid_jdbc_type" to "BINARY",
                "hibernate.dialect.oracle.use_binary_floats" to "false",
                "hibernate.schema_management_tool" to CordaSchemaManagementTool::class.java.name
        ))
    }

    /** The column type that Hibernate expects for a [Boolean] on the given major version of Oracle. No database is needed for this. */
    private fun oracleBooleanColumnType(oracleMajorVersion: Int, settings: Map<String, String>): String {
        val registry = StandardServiceRegistryBuilder()
                .applySettings(settings)
                .applySetting("hibernate.dialect", OracleDialect::class.java.name)
                .applySetting("jakarta.persistence.database-product-name", "Oracle")
                .applySetting("jakarta.persistence.database-major-version", oracleMajorVersion.toString())
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build()
        try {
            val metadata = MetadataSources(registry).addAnnotatedClass(EntityWithBoolean::class.java).buildMetadata()
            val column = metadata.getEntityBinding(EntityWithBoolean::class.java.name).getProperty("flag").columns.single()
            return column.getSqlType(metadata)
        } finally {
            StandardServiceRegistryBuilder.destroy(registry)
        }
    }

    @Entity
    @Table(name = "entity_with_boolean")
    class EntityWithBoolean(
            @Id
            @Column(name = "id")
            val id: String = "",

            @Column(name = "flag", nullable = false)
            val flag: Boolean = false
    )
}
