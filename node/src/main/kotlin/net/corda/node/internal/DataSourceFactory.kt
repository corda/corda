package net.corda.node.internal

import com.codahale.metrics.MetricRegistry
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.util.PropertyElf
import net.corda.core.internal.declaredField
import org.h2.engine.Database
import org.h2.engine.Engine
import org.slf4j.LoggerFactory
import java.lang.reflect.Modifier
import java.util.*
import javax.sql.DataSource

object DataSourceFactory {
    /** H2 only uses get/put/remove on [Engine.DATABASES], and it has to be a [HashMap]. */
    private class SynchronizedGetPutRemove<K, V> : HashMap<K, V>() {
        @Synchronized
        override fun get(key: K) = super.get(key)

        @Synchronized
        override fun put(key: K, value: V) = super.put(key, value)

        @Synchronized
        override fun remove(key: K) = super.remove(key)
    }

    init {
        LoggerFactory.getLogger(javaClass).debug("Applying H2 fix.") // See CORDA-924.
        Engine::class.java.getDeclaredField("DATABASES").apply {
            isAccessible = true
            declaredField<Int>("modifiers").apply { value = value and Modifier.FINAL.inv() }
        }.set(null, SynchronizedGetPutRemove<String, Database>())
    }

    fun createDataSource(hikariProperties: Properties, pool: Boolean = true, metricRegistry: MetricRegistry? = null): DataSource {
        val config = HikariConfig(hikariProperties)
        return if (pool) {
            val dataSource = HikariDataSource(config)
            if (metricRegistry != null) {
                dataSource.metricRegistry = metricRegistry
            }
            dataSource
        } else {
            // Basic init for the one test that wants to go via this API but without starting a HikariPool:
            (Class.forName(hikariProperties.getProperty("dataSourceClassName")).newInstance() as DataSource).also {
                PropertyElf.setTargetFromProperties(it, config.dataSourceProperties)
            }
        }
    }
}
