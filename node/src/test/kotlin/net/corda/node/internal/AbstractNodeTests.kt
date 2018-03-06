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

import com.nhaarman.mockito_kotlin.mock
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.transpose
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.relaxedThoroughness
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.internal.ProcessUtilities.startJavaProcess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.slf4j.Logger
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import kotlin.test.assertEquals

class AbstractNodeTests {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()
    private var nextNodeIndex = 0
    private fun freshURL(): String {
        val baseDir = File(temporaryFolder.root, nextNodeIndex++.toString())
        // Problems originally exposed by driver startNodesInProcess, so do what driver does:
        return "jdbc:h2:file:$baseDir/persistence;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000;WRITE_DELAY=100;AUTO_SERVER_PORT=0"
    }

    @Test
    fun `logVendorString does not leak connection`() {
        // Note this test also covers a transaction that CordaPersistence does while it's instantiating:
        val database = configureDatabase(hikariProperties(freshURL()), DatabaseConfig(), rigorousMock())
        val log = mock<Logger>() // Don't care what happens here.
        // Actually 10 is enough to reproduce old code hang, as pool size is 10 and we leaked 9 connections and 1 is in flight:
        repeat(100) {
            logVendorString(database, log)
        }
    }

    @Test
    fun `H2 fix is applied`() {
        val pool = Executors.newFixedThreadPool(5)
        val runs = if (relaxedThoroughness) 1 else 100
        (0 until runs).map {
            // Four "nodes" seems to be the magic number to reproduce the problem on CI:
            val urls = (0 until 4).map { freshURL() }
            // Haven't been able to reproduce in a warm JVM:
            pool.fork {
                assertEquals(0, startJavaProcess<ColdJVM>(urls).waitFor())
            }
        }.transpose().getOrThrow()
        pool.shutdown()
        // The above will always run all processes, even if the very first fails
        // In theory this can be handled better, but
        // a) we expect to run all the runs, that's how the test passes
        // b) it would require relatively complex handling (futures+threads), which is not worth it
        //    because of a)
    }
}

private fun hikariProperties(url: String) = Properties().apply {
    put("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
    put("dataSource.url", url)
    put("dataSource.user", "sa")
    put("dataSource.password", "")
}

class ColdJVM {
    companion object {
        private val log = contextLogger()
        private val pool = Executors.newCachedThreadPool()
        @JvmStatic
        fun main(urls: Array<String>) {
            val f = urls.map {
                pool.fork { startNode(it) } // Like driver starting in-process nodes in parallel.
            }.transpose()
            try {
                f.getOrThrow()
                System.exit(0) // Kill non-daemon threads.
            } catch (t: Throwable) {
                log.error("H2 fix did not work:", t)
                System.exit(1)
            }
        }

        private fun startNode(url: String) {
            // Go via the API to trigger the fix, but don't allow a HikariPool to interfere:
            val dataSource = DataSourceFactory.createDataSource(hikariProperties(url), false)
            assertEquals("org.h2.jdbcx.JdbcDataSource", dataSource.javaClass.name)
            // Like HikariPool.checkFailFast, immediately after which the Database is removed from Engine.DATABASES:
            dataSource.connection.use { log.info("Foreground connection: {}", it) }
            pool.fork {
                // Like logVendorString, which is done via the connection pool:
                dataSource.connection.use { log.info("Background connection: {}", it) }
            }.getOrThrow()
        }
    }
}
