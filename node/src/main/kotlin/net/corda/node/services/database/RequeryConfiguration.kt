package net.corda.node.services.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.requery.Persistable
import io.requery.meta.EntityModel
import io.requery.sql.KotlinEntityDataStore
import io.requery.sql.SchemaModifier
import io.requery.sql.TableCreationMode
import net.corda.core.utilities.loggerFor
import net.corda.node.utilities.DatabaseTransactionManager
import java.sql.Connection
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class RequeryConfiguration(val properties: Properties, val useDefaultLogging: Boolean = false) {

    companion object {
        val logger = loggerFor<RequeryConfiguration>()
    }

    // TODO:
    // 1. schemaService schemaOptions needs to be applied: eg. default schema, table prefix
    // 2. set other generic database configuration options: show_sql, format_sql
    // 3. Configure Requery Database platform specific features (see http://requery.github.io/javadoc/io/requery/sql/Platform.html)
    // 4. Configure Cache Manager and Cache Provider and set in Requery Configuration (see http://requery.github.io/javadoc/io/requery/EntityCache.html)
    // 5. Consider database schema deployment/upgrade strategies to replace dynamic table creation.

    // Note: Annotations are pre-processed using (kapt) so no need to register dynamically
    val config = HikariConfig(properties)
    val dataSource = HikariDataSource(config)

    // TODO: make this a guava cache or similar to limit ability for this to grow forever.
    private val sessionFactories = ConcurrentHashMap<EntityModel, KotlinEntityDataStore<Persistable>>()

    fun sessionForModel(model: EntityModel): KotlinEntityDataStore<Persistable> {
        return sessionFactories.computeIfAbsent(model, { makeSessionFactoryForModel(it) })
    }

    fun makeSessionFactoryForModel(model: EntityModel): KotlinEntityDataStore<Persistable> {
        val configuration = KotlinConfigurationTransactionWrapper(model, dataSource, useDefaultLogging = this.useDefaultLogging)
        val tables = SchemaModifier(configuration)
        val mode = TableCreationMode.CREATE_NOT_EXISTS
        tables.createTables(mode)
        return KotlinEntityDataStore(configuration)
    }

    // TODO: remove once Requery supports QUERY WITH COMPOSITE_KEY IN
    fun jdbcSession(): Connection {
        val ctx = DatabaseTransactionManager.currentOrNull()
        return ctx?.connection ?: throw IllegalStateException("Was expecting to find database transaction: must wrap calling code within a transaction.")
    }
}

