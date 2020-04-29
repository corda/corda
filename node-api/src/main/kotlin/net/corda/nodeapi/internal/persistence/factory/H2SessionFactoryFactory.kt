package net.corda.nodeapi.internal.persistence.factory

class H2SessionFactoryFactory : BaseSessionFactoryFactory() {
      override fun canHandleDatabase(jdbcUrl: String): Boolean = jdbcUrl.startsWith("jdbc:h2:")
      override val databaseType: String = "H2"
}