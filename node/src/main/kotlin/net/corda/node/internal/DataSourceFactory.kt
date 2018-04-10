/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.util.PropertyElf
import net.corda.core.internal.declaredField
import org.h2.engine.Database
import org.h2.engine.Engine
import org.slf4j.LoggerFactory
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
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

    fun createDataSource(hikariProperties: Properties, pool: Boolean = true): DataSource {
        val config = HikariConfig(hikariProperties)
        return if (pool) {
            HikariDataSource(config)
        } else {
            // Basic init for the one test that wants to go via this API but without starting a HikariPool:
            (Class.forName(hikariProperties.getProperty("dataSourceClassName")).newInstance() as DataSource).also {
                PropertyElf.setTargetFromProperties(it, config.dataSourceProperties)
            }
        }
    }

    fun createDatasourceFromDriverJarFolders(dataSourceProperties: Properties, baseClassLoader: ClassLoader, driverJarsPath: List<Path>): DataSource {
        return URLClassLoader(driverJarsPath.flatMap { Files.newDirectoryStream(it, "*.jar") }.map { it.toUri().toURL() }.toTypedArray(), baseClassLoader).use { driversClassLoader ->
            val dataSourceClassName = dataSourceProperties["dataSourceClassName"] as String?
            val dataSourceClass = driversClassLoader.loadClass(dataSourceClassName)
            val dataSourceInstance = dataSourceClass.newInstance() as DataSource

            val props = Properties().also {
                it.putAll(dataSourceProperties.propertyNames().toList()
                        .filter { name -> (name as String).startsWith("dataSource.") }
                        .map { name -> (name as String).substring("dataSource.".length) to (dataSourceProperties[name]) }.toMap())
            }
            PropertyElf.setTargetFromProperties(dataSourceInstance, props)

            dataSourceInstance
        }
    }

    fun createHikariDatasourceFromDriverJarFolders(dataSourceProperties: Properties, baseClassLoader: ClassLoader, driverJarsPath: List<Path>): DataSource {
        val dataSource = createDatasourceFromDriverJarFolders(dataSourceProperties, baseClassLoader, driverJarsPath)
        val cfg = HikariConfig(dataSourceProperties)
        cfg.dataSource = dataSource
        return HikariDataSource(cfg)
    }
}