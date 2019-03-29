package net.corda.node.internal.subcommands

import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.node.internal.NodeStartup
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.internal.Node
import net.corda.testing.driver.internal.incrementalPortAllocation
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.h2.tools.Server
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.lang.IllegalStateException
import java.nio.file.Files
import java.sql.DriverManager
import java.util.*

class InitialRegistrationCliTest {

    companion object {

        private lateinit var initialRegistration: InitialRegistration

        private val networkTrustRootPassword = "some-password"
        private val nodeStartup = mock(NodeStartup::class.java)

        private val baseDirectory = Files.createTempDirectory("base-dir")!!
        private val networkTrustRootFile = Files.createTempFile("trust-root-store", "jks")

        private lateinit var nodeConfiguration: NodeConfiguration
        private lateinit var datasourceProperties: Properties
        private lateinit var node: Node

        private val h2jdbcUrl = "jdbc:h2:tcp://localhost:<port>/~/node"
        private val h2User = "sa"
        private val h2Password = ""
        private var port = 10009

        private lateinit var server: Server

        @BeforeClass
        @JvmStatic
        fun prepare() {
            nodeConfiguration = mock(NodeConfiguration::class.java)
            datasourceProperties = Properties()
            node = mock(Node::class.java)

            Mockito.`when`(node.configuration).thenReturn(nodeConfiguration)
            Mockito.`when`(nodeConfiguration.dataSourceProperties).thenReturn(datasourceProperties)

            port = incrementalPortAllocation(port).nextPort()
            server = Server.createTcpServer("-tcpPort", port.toString(), "-tcpAllowOthers", "-tcpDaemon").start()
            executeSqlStatement("CREATE TABLE NODE_INFOS(id INT); INSERT INTO NODE_INFOS (id) VALUES (1);")

            initialRegistration = InitialRegistration(baseDirectory, networkTrustRootFile, networkTrustRootPassword, nodeStartup)
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            baseDirectory.deleteRecursively()
            networkTrustRootFile.deleteRecursively()

            executeSqlStatement("DROP TABLE NODE_INFOS;")
            server.shutdown()
        }

        private fun executeSqlStatement(sqlStatement: String) {
            val connection = DriverManager.getConnection(getJdbcUrl(), h2User, h2Password)
            connection.use {
                val statement = connection.createStatement()
                statement.execute(sqlStatement)
            }
        }

        private fun getJdbcUrl(): String {
            return h2jdbcUrl.replace("<port>", port.toString())
        }
    }

    @Test
    fun `registration fails when there is existing artemis folder`() {
        Files.createDirectories(baseDirectory / "artemis" / "a")

        assertThatThrownBy { initialRegistration.registerWithNetwork(node.configuration) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("The node folder contains a non-empty artemis directory")

        Files.deleteIfExists(baseDirectory / "artemis" / "a")
        Files.deleteIfExists(baseDirectory / "artemis")
    }

    @Test
    fun `registration fails when there is existing brokers folder`() {
        Files.createDirectories(baseDirectory / "brokers" / "a")

        assertThatThrownBy { initialRegistration.registerWithNetwork(node.configuration) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("The node folder contains a non-empty brokers directory")

        Files.deleteIfExists(baseDirectory / "brokers" / "a")
        Files.deleteIfExists(baseDirectory / "brokers")
    }

    @Test
    fun `registration fails when node infos table contains entries`() {
        datasourceProperties.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
        datasourceProperties.setProperty("dataSource.url", getJdbcUrl())
        datasourceProperties.setProperty("dataSource.user", h2User)
        datasourceProperties.setProperty("dataSource.password", h2Password)
        Mockito.`when`(nodeConfiguration.dataSourceProperties).thenReturn(datasourceProperties)

        assertThatThrownBy { initialRegistration.registerWithNetwork(node.configuration) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("The node info table contains node infos")
    }
}