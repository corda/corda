package net.corda.node.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.nodeapi.internal.persistence.CouldNotCreateDataSourceException
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Test
import java.net.InetAddress
//import java.net.ServerSocket
import java.sql.DriverManager
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class H2SecurityTests {
    companion object {
        private fun getFreePort() = 20001 //ServerSocket(0).localPort
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
    fun `h2 server on host name requires non-blank database password`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            assertFailsWith(CouldNotCreateDataSourceException::class) {
                startNode(customOverrides = mapOf(h2AddressKey to "${InetAddress.getLocalHost().hostName}:${getFreePort()}",
                        dbPasswordKey to " ")).getOrThrow()
            }
        }
    }

    @Test
    fun `h2 server on external host IP requires non-blank database password`() {
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

    @Test
    fun `remote code execution via h2 server is disabled`() {
        assertNull(System.getProperty("abc"), "Expecting abc property to be not set") //sanity check
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
    fun `malicious flow tries to enable remote code execution via h2 server`() {
        assertNull(System.getProperty("abc"), "Expecting abc property to be not set") //sanity check
        val user = User("mark", "dadada", setOf(Permissions.startFlow<MaliciousFlow>()))
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(), notarySpecs = emptyList())) {
            val port = getFreePort()
            val nodeHandle = startNode(rpcUsers = listOf(user), customOverrides = mapOf(h2AddressKey to "localhost:$port")).getOrThrow()
            CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::MaliciousFlow).returnValue.getOrThrow()
            }
            assertFailsWith(org.h2.jdbc.JdbcSQLException::class) {
                DriverManager.getConnection("jdbc:h2:tcp://localhost:$port/node", "sa", "").use {
                    it.createStatement().execute("CREATE ALIAS SET_PROPERTY FOR \"java.lang.System.setProperty\"")
                    it.createStatement().execute("CALL SET_PROPERTY('abc', '1')")
                }
            }
            assertNull(System.getProperty("abc"))
        }
    }

    @StartableByRPC
    class MaliciousFlow : FlowLogic<Boolean>() {
        @Suspendable
        override fun call(): Boolean {
            System.clearProperty("h2.allowedClasses")
            return true
        }
    }
}