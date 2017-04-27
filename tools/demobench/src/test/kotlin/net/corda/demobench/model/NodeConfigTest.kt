package net.corda.demobench.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.common.net.HostAndPort
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.core.div
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.node.internal.NetworkMapInfo
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.nodeapi.User
import net.corda.nodeapi.config.parseAs
import net.corda.webserver.WebServerConfig
import org.junit.Test
import java.io.StringWriter
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeConfigTest {

    private val baseDir: Path = Paths.get(".").toAbsolutePath()

    @Test
    fun `test name`() {
        val config = createConfig(legalName = "My Name")
        assertEquals("My Name", config.legalName)
        assertEquals("myname", config.key)
    }

    @Test
    fun `test node directory`() {
        val config = createConfig(legalName = "My Name")
        assertEquals(baseDir / "myname", config.nodeDir)
    }

    @Test
    fun `test explorer directory`() {
        val config = createConfig(legalName = "My Name")
        assertEquals(baseDir / "myname-explorer", config.explorerDir)
    }

    @Test
    fun `test plugin directory`() {
        val config = createConfig(legalName = "My Name")
        assertEquals(baseDir / "myname" / "plugins", config.pluginDir)
    }

    @Test
    fun `test nearest city`() {
        val config = createConfig(nearestCity = "Leicester")
        assertEquals("Leicester", config.nearestCity)
    }

    @Test
    fun `test P2P port`() {
        val config = createConfig(p2pPort = 10001)
        assertEquals(10001, config.p2pPort)
    }

    @Test
    fun `test rpc port`() {
        val config = createConfig(rpcPort = 40002)
        assertEquals(40002, config.rpcPort)
    }

    @Test
    fun `test web port`() {
        val config = createConfig(webPort = 20001)
        assertEquals(20001, config.webPort)
    }

    @Test
    fun `test H2 port`() {
        val config = createConfig(h2Port = 30001)
        assertEquals(30001, config.h2Port)
    }

    @Test
    fun `test services`() {
        val config = createConfig(services = listOf("my.service"))
        assertEquals(listOf("my.service"), config.extraServices)
    }

    @Test
    fun `test users`() {
        val config = createConfig(users = listOf(user("myuser")))
        assertEquals(listOf(user("myuser")), config.users)
    }

    @Test
    fun `test default state`() {
        val config = createConfig()
        assertEquals(NodeState.STARTING, config.state)
    }

    @Test
    fun `test network map`() {
        val config = createConfig()
        assertNull(config.networkMap)
        assertTrue(config.isNetworkMap())
    }

    @Test
    fun `test cash issuer`() {
        val config = createConfig(services = listOf("corda.issuer.GBP"))
        assertTrue(config.isCashIssuer)
    }

    @Test
    fun `test not cash issuer`() {
        val config = createConfig(services = listOf("corda.issuerubbish"))
        assertFalse(config.isCashIssuer)
    }

    /**
     * Reformat JSON via Jackson to ensure a consistent format for comparison purposes.
     */
    private fun prettyPrint(content: String): String {
        val mapper = ObjectMapper()
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
        val sw = StringWriter()
        val parsed = mapper.readTree(content)
        mapper.writeValue(sw, parsed)
        return sw.toString()
    }

    @Test
    fun `test config text`() {
        val config = createConfig(
                legalName = "My Name",
                nearestCity = "Stockholm",
                p2pPort = 10001,
                rpcPort = 40002,
                webPort = 20001,
                h2Port = 30001,
                services = listOf("my.service"),
                users = listOf(user("jenny"))
        )
        assertEquals(prettyPrint("{"
                + "\"extraAdvertisedServiceIds\":[\"my.service\"],"
                + "\"h2port\":30001,"
                + "\"myLegalName\":\"My Name\","
                + "\"nearestCity\":\"Stockholm\","
                + "\"p2pAddress\":\"localhost:10001\","
                + "\"rpcAddress\":\"localhost:40002\","
                + "\"rpcUsers\":["
                + "{\"password\":\"letmein\",\"permissions\":[\"ALL\"],\"username\":\"jenny\"}"
                + "],"
                + "\"useTestClock\":true,"
                + "\"webAddress\":\"localhost:20001\""
                + "}"), prettyPrint(config.toText()))
    }

    @Test
    fun `test config text with network map`() {
        val config = createConfig(
                legalName = "My Name",
                nearestCity = "Stockholm",
                p2pPort = 10001,
                rpcPort = 40002,
                webPort = 20001,
                h2Port = 30001,
                services = listOf("my.service"),
                users = listOf(user("jenny"))
        )
        config.networkMap = NetworkMapConfig(DUMMY_NOTARY.name, 12345)

        assertEquals(prettyPrint("{"
                + "\"extraAdvertisedServiceIds\":[\"my.service\"],"
                + "\"h2port\":30001,"
                + "\"myLegalName\":\"My Name\","
                + "\"nearestCity\":\"Stockholm\","
                + "\"networkMapService\":{\"address\":\"localhost:12345\",\"legalName\":\"CN=Notary Service,O=R3,OU=corda,L=Zurich,C=CH\"},"
                + "\"p2pAddress\":\"localhost:10001\","
                + "\"rpcAddress\":\"localhost:40002\","
                + "\"rpcUsers\":["
                + "{\"password\":\"letmein\",\"permissions\":[\"ALL\"],\"username\":\"jenny\"}"
                + "],"
                + "\"useTestClock\":true,"
                + "\"webAddress\":\"localhost:20001\""
                + "}"), prettyPrint(config.toText()))
    }

    @Test
    fun `reading node configuration`() {
        val config = createConfig(
                legalName = "My Name",
                nearestCity = "Stockholm",
                p2pPort = 10001,
                rpcPort = 40002,
                webPort = 20001,
                h2Port = 30001,
                services = listOf("my.service"),
                users = listOf(user("jenny"))
        )
        config.networkMap = NetworkMapConfig(DUMMY_NOTARY.name, 12345)

        val nodeConfig = config.toFileConfig()
                .withValue("basedir", ConfigValueFactory.fromAnyRef(baseDir.toString()))
                .withFallback(ConfigFactory.parseResources("reference.conf"))
                .resolve()
        val fullConfig = nodeConfig.parseAs<FullNodeConfiguration>()

        assertEquals("My Name", fullConfig.myLegalName)
        assertEquals("Stockholm", fullConfig.nearestCity)
        assertEquals(localPort(40002), fullConfig.rpcAddress)
        assertEquals(localPort(10001), fullConfig.p2pAddress)
        assertEquals(listOf("my.service"), fullConfig.extraAdvertisedServiceIds)
        assertEquals(listOf(user("jenny")), fullConfig.rpcUsers)
        assertEquals(NetworkMapInfo(localPort(12345), DUMMY_NOTARY.name), fullConfig.networkMapService)
        assertTrue((fullConfig.dataSourceProperties["dataSource.url"] as String).contains("AUTO_SERVER_PORT=30001"))
        assertTrue(fullConfig.useTestClock)
    }

    @Test
    fun `reading webserver configuration`() {
        val config = createConfig(
                legalName = "My Name",
                nearestCity = "Stockholm",
                p2pPort = 10001,
                rpcPort = 40002,
                webPort = 20001,
                h2Port = 30001,
                services = listOf("my.service"),
                users = listOf(user("jenny"))
        )
        config.networkMap = NetworkMapConfig(DUMMY_NOTARY.name, 12345)

        val nodeConfig = config.toFileConfig()
                .withValue("basedir", ConfigValueFactory.fromAnyRef(baseDir.toString()))
                .withFallback(ConfigFactory.parseResources("web-reference.conf"))
                .resolve()
        val webConfig = WebServerConfig(baseDir, nodeConfig)

        assertEquals(localPort(20001), webConfig.webAddress)
        assertEquals(localPort(10001), webConfig.p2pAddress)
        assertEquals("trustpass", webConfig.trustStorePassword)
        assertEquals("cordacadevpass", webConfig.keyStorePassword)
    }

    @Test
    fun `test moving`() {
        val config = createConfig(legalName = "My Name")

        val elsewhere = baseDir / "elsewhere"
        val moved = config.moveTo(elsewhere)
        assertEquals(elsewhere / "myname", moved.nodeDir)
        assertEquals(elsewhere / "myname-explorer", moved.explorerDir)
        assertEquals(elsewhere / "myname" / "plugins", moved.pluginDir)
    }

    private fun createConfig(
            legalName: String = "Unknown",
            nearestCity: String = "Nowhere",
            p2pPort: Int = -1,
            rpcPort: Int = -1,
            webPort: Int = -1,
            h2Port: Int = -1,
            services: List<String> = listOf("extra.service"),
            users: List<User> = listOf(user("guest"))
    ) = NodeConfig(
            baseDir,
            legalName = legalName,
            nearestCity = nearestCity,
            p2pPort = p2pPort,
            rpcPort = rpcPort,
            webPort = webPort,
            h2Port = h2Port,
            extraServices = services,
            users = users
    )

    private fun localPort(port: Int) = HostAndPort.fromParts("localhost", port)
}
