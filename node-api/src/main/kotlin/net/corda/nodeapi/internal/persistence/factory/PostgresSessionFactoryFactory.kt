package net.corda.nodeapi.internal.persistence.factory

class PostgresSessionFactoryFactory : BaseSessionFactoryFactory() {
    companion object {
        /** Whether the JDBC URL is for PostgreSQL, i.e. whether the node uses this factory for it. */
        fun isPostgreSql(jdbcUrl: String): Boolean = jdbcUrl.contains(":postgresql:")
    }

    override fun canHandleDatabase(jdbcUrl: String): Boolean = isPostgreSql(jdbcUrl)

    // Hibernate 5 stored UUIDs in a native uuid column on PostgreSQL.
    override val preferredUuidJdbcType: String = HibernateCompatibility.POSTGRESQL_UUID_JDBC_TYPE

    override val databaseType: String = "PostgreSQL"
}
