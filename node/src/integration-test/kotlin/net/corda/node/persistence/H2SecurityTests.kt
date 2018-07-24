package net.corda.node.persistence

import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.persistence.CouldNotCreateDataSourceException
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.sql.DriverManager
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class H2SecurityTests {
    companion object {
        private fun getFreePort() = ServerSocket(0).localPort
        private const val h2AddressKey = "h2Settings.address"
        private const val dbPasswordKey = "dataSourceProperties.dataSource.password"
    }

    @Test
    fun `h2 server starts when h2Settings are set`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            val port = getFreePort()
            startNode(customOverrides = mapOf(h2AddressKey to "localhost:$port")).getOrThrow()
            DriverManager.getConnection("jdbc:h2:tcp://localhost:$port/node", "sa", "").use {
                assertTrue(it.createStatement().executeQuery("SELECT 1").next())
            }
        }
    }

    @Test
    fun `remote access to h2 server can't run java code`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            val port = getFreePort()
            startNode(customOverrides = mapOf(h2AddressKey to "localhost:$port")).getOrThrow()
            assertFailsWith(org.h2.jdbc.JdbcSQLException::class) {
                DriverManager.getConnection("jdbc:h2:tcp://localhost:$port/node", "sa", "").use {
                    it.createStatement().execute("CREATE ALIAS SET_PROPERTY FOR \"java.lang.System.setProperty\"")
                    it.createStatement().execute("CALL SET_PROPERTY('abc', '1')")
                }
            }
            assertNull(System.getProperty("abc"))
        }
    }

    @Test
    fun `h2 server on the host name requires non-default database password`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            assertFailsWith(CouldNotCreateDataSourceException::class) {
                startNode(customOverrides = mapOf(h2AddressKey to "${InetAddress.getLocalHost().hostName}:${getFreePort()}")).getOrThrow()
            }
        }
    }

    @Test
    fun `h2 server on the external host IP requires non-default database password`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            assertFailsWith(CouldNotCreateDataSourceException::class) {
                startNode(customOverrides = mapOf(h2AddressKey to "${InetAddress.getLocalHost().hostAddress}:${getFreePort()}")).getOrThrow()
            }
        }
    }

    @Test
    fun `h2 server on host name requires non-blank database admin password`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            assertFailsWith(CouldNotCreateDataSourceException::class) {
                startNode(customOverrides = mapOf(h2AddressKey to "${InetAddress.getLocalHost().hostName}:${getFreePort()}",
                        dbPasswordKey to " ")).getOrThrow()
            }
        }
    }

    @Test
    fun `h2 server on external host IP requires non-blank database admin password`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            assertFailsWith(CouldNotCreateDataSourceException::class) {
                startNode(customOverrides = mapOf(h2AddressKey to "${InetAddress.getLocalHost().hostAddress}:${getFreePort()}",
                        dbPasswordKey to " ")).getOrThrow()
            }
        }
    }

    @Test
    fun `h2 server on localhost runs with the default database password`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            startNode(customOverrides = mapOf(h2AddressKey to "localhost:${getFreePort()}")).getOrThrow()
        }
    }

    @Test
    fun `h2 server to loopback IP runs with the default database password`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            startNode(customOverrides = mapOf(h2AddressKey to "127.0.0.1:${getFreePort()}")).getOrThrow()
        }
    }
}