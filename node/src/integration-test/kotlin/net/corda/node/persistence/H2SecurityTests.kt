package net.corda.node.persistence

import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.persistence.CouldNotCreateDataSourceException
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import java.net.InetAddress
import java.sql.DriverManager
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class H2SecurityTests {

    @Test
    fun `node starts h2 server when h2Settings are set`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            val nodeHandle = startNode(customOverrides = mapOf("h2Settings.address" to "localhost:10030")).getOrThrow()
            DriverManager.getConnection("jdbc:h2:tcp://localhost:10030/node", "sa", "").use {
                val result = it.createStatement().executeQuery("SELECT 1")
                assertTrue(result.next())
            }
            nodeHandle.stop()
        }
    }

    @Test
    fun `node doesn't start h2 server by the default`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            val nodeHandle = startNode().getOrThrow()
            assertFailsWith(org.h2.jdbc.JdbcSQLException::class) {
                DriverManager.getConnection("jdbc:h2:tcp://localhost:10030/node", "sa", "").use {
                    it.createStatement().executeQuery("SELECT 1")
                }
            }
            nodeHandle.stop()
        }
    }

    @Test
    fun `remote access to h2 server cant run java code`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            val nodeHandle = startNode(customOverrides = mapOf("h2Settings.address" to "localhost:10030")).getOrThrow()
            assertFailsWith(org.h2.jdbc.JdbcSQLException::class) {
                DriverManager.getConnection("jdbc:h2:tcp://localhost:10030/node", "sa", "").use {
                    it.createStatement().execute("CREATE ALIAS SET_PROPERTY FOR \"java.lang.System.setProperty\"")
                    it.createStatement().execute("CALL SET_PROPERTY('abc', '1')")
                    it.commit()
                }
            }
            assertNull(System.getProperty("abc"))
            nodeHandle.stop()
        }
    }

    @Test
    fun `h2 server not bind to localhost require non-default database admin password`() {

        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            assertFailsWith(CouldNotCreateDataSourceException::class) {
                startNode(customOverrides = mapOf("h2Settings.address" to "${InetAddress.getLocalHost().hostName}:10030")).getOrThrow()
            }
        }
    }

    @Test
    fun `h2 server not bind to localhost require non-blank database admin password`() {

        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            assertFailsWith(CouldNotCreateDataSourceException::class) {
                startNode(customOverrides = mapOf("h2Settings.address" to "${InetAddress.getLocalHost().hostName}:10030",
                        "dataSourceProperties.dataSource.password" to " ")).getOrThrow()
            }
        }
    }

    @Test
    fun `h2 serevr bind to localhost allows default database admin password`() {

        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            startNode(customOverrides = mapOf("h2Settings.address" to "localhost:10030")).getOrThrow()
        }
    }
}