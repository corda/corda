package net.corda.nodeapi.internal.persistence.factory

import net.corda.nodeapi.internal.persistence.DatabaseConfig
import org.hibernate.boot.MetadataSources
import org.hibernate.cfg.Configuration

class H2SessionFactoryFactory : BaseSessionFactoryFactory() {

    override fun buildHibernateConfig(databaseConfig: DatabaseConfig, metadataSources: MetadataSources, allowHibernateToManageAppSchema: Boolean): Configuration {
        val config = super.buildHibernateConfig(databaseConfig, metadataSources, false)
        if (allowHibernateToManageAppSchema) {
            config.setProperty("hibernate.hbm2ddl.auto", "update")
        } else {
            config.setProperty("hibernate.hbm2ddl.auto", "validate")
        }
        return config
    }

    override fun canHandleDatabase(jdbcUrl: String): Boolean = jdbcUrl.startsWith("jdbc:h2:")
    override val databaseType: String = "H2"
}