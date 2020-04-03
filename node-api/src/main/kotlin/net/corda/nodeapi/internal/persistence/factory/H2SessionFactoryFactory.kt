package net.corda.nodeapi.internal.persistence.factory

import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.SchemaInitializationType
import org.hibernate.boot.MetadataSources
import org.hibernate.cfg.Configuration

class H2SessionFactoryFactory : BaseSessionFactoryFactory() {
      override fun canHandleDatabase(jdbcUrl: String): Boolean = jdbcUrl.startsWith("jdbc:h2:")
      override val databaseType: String = "H2"
}