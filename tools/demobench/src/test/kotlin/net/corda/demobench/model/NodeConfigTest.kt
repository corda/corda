package net.corda.demobench.model

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.NetworkMapInfo
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.nodeapi.User
import net.corda.nodeapi.config.parseAs
import net.corda.nodeapi.config.toConfig
import net.corda.testing.DUMMY_NOTARY
import net.corda.webserver.WebServerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
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
                webPort = 20001,
                h2port = 30001,
                notary = NotaryService(validating = false),
                networkMap = NetworkMapConfig(DUMMY_NOTARY.name, localPort(12345)),
                users = listOf(user("jenny"))
        )

        val nodeConfig = config.toConfig()
                .withValue("baseDirectory", ConfigValueFactory.fromAnyRef(baseDir.toString()))
                .withFallback(ConfigFactory.parseResources("reference.conf"))
                .resolve()
        val fullConfig = nodeConfig.parseAs<FullNodeConfiguration>()

        assertEquals(myLegalName, fullConfig.myLegalName)
        assertEquals(localPort(40002), fullConfig.rpcAddress)
        assertEquals(localPort(10001), fullConfig.p2pAddress)
        assertEquals(listOf(user("jenny")), fullConfig.rpcUsers)
        assertEquals(NetworkMapInfo(localPort(12345), DUMMY_NOTARY.name), fullConfig.networkMapService)
        assertThat(fullConfig.dataSourceProperties["dataSource.url"] as String).contains("AUTO_SERVER_PORT=30001")
        assertTrue(fullConfig.useTestClock)
        assertFalse(fullConfig.detectPublicIp)
    }

    @Test
    fun `reading webserver configuration`() {
        val config = createConfig(
                legalName = myLegalName,
                p2pPort = 10001,
                rpcPort = 40002,
                webPort = 20001,
                h2port = 30001,
                notary = NotaryService(validating = false),
                networkMap = NetworkMapConfig(DUMMY_NOTARY.name, localPort(12345)),
                users = listOf(user("jenny"))
        )

        val nodeConfig = config.toConfig()
                .withValue("baseDirectory", ConfigValueFactory.fromAnyRef(baseDir.toString()))
                .withFallback(ConfigFactory.parseResources("web-reference.conf"))
                .resolve()
        val webConfig = WebServerConfig(baseDir, nodeConfig)

        assertEquals(localPort(20001), webConfig.webAddress)
        assertEquals(localPort(40002), webConfig.rpcAddress)
        assertEquals("trustpass", webConfig.trustStorePassword)
        assertEquals("cordacadevpass", webConfig.keyStorePassword)
    }

    private fun createConfig(
            legalName: CordaX500Name = CordaX500Name(organisation = "Unknown", locality = "Nowhere", country = "GB"),
            p2pPort: Int = -1,
            rpcPort: Int = -1,
            webPort: Int = -1,
            h2port: Int = -1,
            notary: NotaryService?,
            networkMap: NetworkMapConfig?,
            users: List<User> = listOf(user("guest"))
    ): NodeConfig {
        return NodeConfig(
                myLegalName = legalName,
                p2pAddress = localPort(p2pPort),
                rpcAddress = localPort(rpcPort),
                webAddress = localPort(webPort),
                h2port = h2port,
                notary = notary,
                networkMapService = networkMap,
                rpcUsers = users
        )
    }

    private fun localPort(port: Int) = NetworkHostAndPort("localhost", port)
}
