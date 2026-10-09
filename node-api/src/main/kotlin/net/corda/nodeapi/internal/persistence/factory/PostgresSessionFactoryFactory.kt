package net.corda.nodeapi.internal.persistence.factory

class PostgresSessionFactoryFactory : BaseSessionFactoryFactory() {
    override fun canHandleDatabase(jdbcUrl: String): Boolean = jdbcUrl.contains(":postgresql:")

    // Hibernate 5 stored UUIDs in a native uuid column on PostgreSQL.
    override val preferredUuidJdbcType: String = "UUID"

    override val databaseType: String = "PostgreSQL"
}
