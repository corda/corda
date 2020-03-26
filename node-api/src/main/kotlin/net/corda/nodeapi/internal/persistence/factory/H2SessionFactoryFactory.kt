package net.corda.nodeapi.internal.persistence.factory

import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.SchemaInitializationType
import org.hibernate.boot.MetadataSources
import org.hibernate.cfg.Configuration

class H2SessionFactoryFactory : BaseSessionFactoryFactory() {
      override fun canHandleDatabase(jdbcUrl: String): Boolean = jdbcUrl.startsWith("jdbc:h2:")
      override val databaseType: String = "H2"

      override fun buildHibernateConfig(databaseConfig: DatabaseConfig, metadataSources: MetadataSources): Configuration {
            val config = super.buildHibernateConfig(databaseConfig, metadataSources)

            val hbm2dll: String =
                    if (databaseConfig.initialiseSchema && databaseConfig.initialiseAppSchema == SchemaInitializationType.UPDATE) {
                          "update"
                    } else if ((!databaseConfig.initialiseSchema && databaseConfig.initialiseAppSchema == SchemaInitializationType.UPDATE)
                            || databaseConfig.initialiseAppSchema == SchemaInitializationType.VALIDATE) {
                          "validate"
                    } else {
                          "none"
                    }
            config.setProperty("hibernate.hbm2ddl.auto", hbm2dll)
            return config
      }
}