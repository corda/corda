package net.corda.nodeapi.internal.persistence

import com.codahale.metrics.MetricRegistry
import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import net.corda.core.utilities.contextLogger
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.vault.VaultSchemaV1

class VaultStateMigration : CustomTaskChange {
    companion object {
        private val logger = contextLogger()
    }

    override fun validate(database: Database?): ValidationErrors? {
        return null
    }

    override fun setUp() {
        // No setup required.
    }

    override fun setFileOpener(resourceAccessor: ResourceAccessor?) {
        // No file opener required
    }

    override fun getConfirmationMessage(): String? {
        return null
    }

    private fun createDatabase(jdbcUrl: String): CordaPersistence {
        val metricRegistry = MetricRegistry()
        val cacheFactory = MigrationNamedCacheFactory(metricRegistry, null)
        val configDefaults = DatabaseConfig()
        return CordaPersistence(configDefaults, setOf(NodeSchemaService.NodeCoreV1, VaultSchemaV1), jdbcUrl, cacheFactory)
    }

    override fun execute(database: Database?) {
        val url = (database?.connection as JdbcConnection).url
        val cordaDB = createDatabase(url)
    }
}