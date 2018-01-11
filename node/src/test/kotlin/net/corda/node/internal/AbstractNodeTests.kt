package net.corda.node.internal

import com.nhaarman.mockito_kotlin.mock
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.rigorousMock
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.slf4j.Logger
import java.util.*

class AbstractNodeTests {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `logVendorString does not leak connection`() {
        // Note this test also covers a transaction that CordaPersistence does while it's instantiating:
        val database = configureDatabase(Properties().apply {
            put("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
            // Problem originally exposed by driver startNodesInProcess, so do what driver does:
            put("dataSource.url", "jdbc:h2:file:${temporaryFolder.root}/Alice/persistence;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000;WRITE_DELAY=100;AUTO_SERVER_PORT=0")
            put("dataSource.user", "sa")
            put("dataSource.password", "")
        }, DatabaseConfig(), rigorousMock())
        val log = mock<Logger>() // Don't care what happens here.
        // Actually 10 is enough to reproduce old code hang, as pool size is 10 and we leaked 9 connections and 1 is in flight:
        repeat(100) {
            logVendorString(database, log)
        }
    }
}
