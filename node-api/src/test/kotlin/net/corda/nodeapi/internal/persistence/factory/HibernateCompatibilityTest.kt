package net.corda.nodeapi.internal.persistence.factory

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class HibernateCompatibilityTest {
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
}
