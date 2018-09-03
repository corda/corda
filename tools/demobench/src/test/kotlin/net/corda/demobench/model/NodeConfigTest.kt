package net.corda.demobench.model

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.persistence.CordaPersistence.DataSourceConfigTag
import net.corda.webserver.WebServerConfig
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeConfigTest {
    companion object {
        private val baseDir: Path = Paths.get(".").toAbsolutePath()
        private val myLegalName = CordaX500Name(organisation = "My Name", locality = "New York", country = "US")
    }

    @Test
    fun `reading node configuration`() {
        val config = createConfig(
                legalName = myLegalName,
                p2pPort = 10001,
                rpcPort = 40002,
                rpcAdminPort = 40005,
                webPort = 20001,
                h2port = 30001,
                notary = NotaryService(validating = false),
                users = listOf(user("jenny"))
        )

        val nodeConfig = config.nodeConf()
                .withValue("baseDirectory", valueFor(baseDir.toString()))
                .withFallback(ConfigFactory.parseResources("reference.conf"))
                .withFallback(ConfigFactory.parseMap(mapOf("devMode" to true)))
                .resolve()
        val fullConfig = nodeConfig.parseAsNodeConfiguration()

        // No custom configuration is created by default.
        assertFailsWith<ConfigException.Missing> { nodeConfig.getConfig("custom") }

        assertEquals(myLegalName, fullConfig.myLegalName)
        assertEquals(localPort(40002), fullConfig.rpcOptions.address)
        assertEquals(localPort(10001), fullConfig.p2pAddress)
        assertEquals(listOf(user("jenny")), fullConfig.rpcUsers)
        assertTrue(fullConfig.useTestClock)
        assertFalse(fullConfig.detectPublicIp)
    }

    @Test
    fun `reading node configuration with currencies`() {
        val config = createConfig(
                legalName = myLegalName,
                p2pPort = 10001,
                rpcPort = 10002,
                rpcAdminPort = 10003,
                webPort = 10004,
                h2port = 10005,
                notary = NotaryService(validating = false),
                issuableCurrencies = listOf("GBP")
        )

        val nodeConfig = config.nodeConf()
                .withValue("baseDirectory", valueFor(baseDir.toString()))
                .withFallback(ConfigFactory.parseResources("reference.conf"))
                .resolve()
        val custom = nodeConfig.getConfig("custom")
        assertEquals(listOf("GBP"), custom.getAnyRefList("issuableCurrencies"))
    }

    @Test
    fun `reading webserver configuration`() {
        val config = createConfig(
                legalName = myLegalName,
                p2pPort = 10001,
                rpcPort = 40002,
                rpcAdminPort = 40003,
                webPort = 20001,
                h2port = 30001,
                notary = NotaryService(validating = false),
                users = listOf(user("jenny"))
        )

        val nodeConfig = config.webServerConf()
                .withValue("baseDirectory", valueFor(baseDir.toString()))
                .withFallback(ConfigFactory.parseResources("web-reference.conf"))
                .resolve()
        val webConfig = WebServerConfig(baseDir, nodeConfig)

        // No custom configuration is created by default.
        assertFailsWith<ConfigException.Missing> { nodeConfig.getConfig("custom") }

        assertEquals(localPort(20001), webConfig.webAddress)
        assertEquals(localPort(40002), webConfig.rpcAddress)
        assertEquals("trustpass", webConfig.trustStorePassword)
        assertEquals("cordacadevpass", webConfig.keyStorePassword)
    }

    private fun createConfig(
            legalName: CordaX500Name = CordaX500Name(organisation = "Unknown", locality = "Nowhere", country = "GB"),
            p2pPort: Int = -1,
            rpcPort: Int = -1,
            rpcAdminPort: Int = -1,
            webPort: Int = -1,
            h2port: Int = -1,
            notary: NotaryService?,
            users: List<User> = listOf(user("guest")),
            issuableCurrencies: List<String> = emptyList()
    ): NodeConfig {
        return NodeConfig(
                myLegalName = legalName,
                p2pAddress = localPort(p2pPort),
                rpcSettings = NodeRpcSettings(
                        address = localPort(rpcPort),
                        adminAddress = localPort(rpcAdminPort)
                ),
                webAddress = localPort(webPort),
                h2port = h2port,
                notary = notary,
                rpcUsers = users,
                issuableCurrencies = issuableCurrencies
        )
    }

    private fun localPort(port: Int) = NetworkHostAndPort("localhost", port)
}
