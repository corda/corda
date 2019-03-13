package net.corda.node.internal.subcommands

import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.node.internal.NodeStartup
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.internal.Node
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

        private val h2jdbcUrl = "jdbc:h2:tcp://localhost:10009/~/node"
        private val h2User = "sa"
        private val h2Password = ""

        private lateinit var server: Server

        @BeforeClass
        @JvmStatic
        fun prepare() {
            nodeConfiguration = mock(NodeConfiguration::class.java)
            datasourceProperties = Properties()
            node = mock(Node::class.java)

            Mockito.`when`(node.configuration).thenReturn(nodeConfiguration)
            Mockito.`when`(nodeConfiguration.dataSourceProperties).thenReturn(datasourceProperties)

            server = Server.createTcpServer("-tcpPort", "10009", "-tcpAllowOthers", "-tcpDaemon").start()
            executeSqlStatement("CREATE TABLE NODE_ATTACHMENTS(USERNAME VARCHAR(20));")

            initialRegistration = InitialRegistration(baseDirectory, networkTrustRootFile, networkTrustRootPassword, nodeStartup)
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            baseDirectory.deleteRecursively()
            networkTrustRootFile.deleteRecursively()

            executeSqlStatement("DROP TABLE NODE_ATTACHMENTS;")
            server.shutdown()
        }

        private fun executeSqlStatement(sqlStatement: String) {
            val connection = DriverManager.getConnection(h2jdbcUrl, h2User, h2Password)
            val statement = connection.createStatement()
            statement.execute(sqlStatement)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `registration fails when there is existing artemis folder`() {
        Files.createDirectories(baseDirectory / "artemis")

        initialRegistration.registerWithNetwork(node.configuration)
    }

    @Test(expected = IllegalStateException::class)
    fun `registration fails when there is existing brokers folder`() {
        Files.createDirectories(baseDirectory / "brokers")

        initialRegistration.registerWithNetwork(node.configuration)
    }

    @Test(expected = IllegalStateException::class)
    fun `registration fails when database contains tables`() {
        datasourceProperties.setProperty("dataSource.url", h2jdbcUrl)
        datasourceProperties.setProperty("dataSource.user", h2User)
        datasourceProperties.setProperty("dataSource.password", h2Password)
        Mockito.`when`(nodeConfiguration.dataSourceProperties).thenReturn(datasourceProperties)

        initialRegistration.registerWithNetwork(node.configuration)
    }

}