package net.corda.demobench.model

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.*
import org.junit.Test

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
        assertEquals(baseDir.resolve("myname"), config.nodeDir)
    }

    @Test
    fun `test explorer directory`() {
        val config = createConfig(legalName = "My Name")
        assertEquals(baseDir.resolve("myname-explorer"), config.explorerDir)
    }

    @Test
    fun `test plugin directory`() {
        val config = createConfig(legalName = "My Name")
        assertEquals(baseDir.resolve("myname").resolve("plugins"), config.pluginDir)
    }

    @Test
    fun `test nearest city`() {
        val config = createConfig(nearestCity = "Leicester")
        assertEquals("Leicester", config.nearestCity)
    }

    @Test
    fun `test messaging port`() {
        val config = createConfig(messagingPort = 10001)
        assertEquals(10001, config.messagingPort)
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

    @Test
    fun `test config text`() {
        val config = createConfig(
            legalName = "My Name",
            nearestCity = "Stockholm",
            messagingPort = 10001,
            rpcPort = 40002,
            webPort = 20001,
            h2Port = 30001,
            services = listOf("my.service"),
            users = listOf(user("jenny"))
        )
        assertEquals("{"
                +     "\"artemisAddress\":\"localhost:10001\","
                +     "\"extraAdvertisedServiceIds\":[\"my.service\"],"
                +     "\"h2port\":30001,"
                +     "\"myLegalName\":\"MyName\","
                +     "\"nearestCity\":\"Stockholm\","
                +     "\"rpcAddress\":\"localhost:40002\","
                +     "\"rpcUsers\":["
                +         "{\"password\":\"letmein\",\"permissions\":[\"ALL\"],\"user\":\"jenny\"}"
                +     "],"
                +     "\"useTestClock\":true,"
                +     "\"webAddress\":\"localhost:20001\""
                + "}", config.toText().stripWhitespace())
    }

    @Test
    fun `test config text with network map`() {
        val config = createConfig(
            legalName = "My Name",
            nearestCity = "Stockholm",
            messagingPort = 10001,
            rpcPort = 40002,
            webPort = 20001,
            h2Port = 30001,
            services = listOf("my.service"),
            users = listOf(user("jenny"))
        )
        config.networkMap = NetworkMapConfig("Notary", 12345)

        assertEquals("{"
                +     "\"artemisAddress\":\"localhost:10001\","
                +     "\"extraAdvertisedServiceIds\":[\"my.service\"],"
                +     "\"h2port\":30001,"
                +     "\"myLegalName\":\"MyName\","
                +     "\"nearestCity\":\"Stockholm\","
                +     "\"networkMapService\":{\"address\":\"localhost:12345\",\"legalName\":\"Notary\"},"
                +     "\"rpcAddress\":\"localhost:40002\","
                +     "\"rpcUsers\":["
                +         "{\"password\":\"letmein\",\"permissions\":[\"ALL\"],\"user\":\"jenny\"}"
                +     "],"
                +     "\"useTestClock\":true,"
                +     "\"webAddress\":\"localhost:20001\""
                + "}", config.toText().stripWhitespace())
    }

    @Test
    fun `test moving`() {
        val config = createConfig(legalName = "My Name")

        val elsewhere = baseDir.resolve("elsewhere")
        val moved = config.moveTo(elsewhere)
        assertEquals(elsewhere.resolve("myname"), moved.nodeDir)
        assertEquals(elsewhere.resolve("myname-explorer"), moved.explorerDir)
        assertEquals(elsewhere.resolve("myname").resolve("plugins"), moved.pluginDir)
    }

    private fun createConfig(
            legalName: String = "Unknown",
            nearestCity: String = "Nowhere",
            messagingPort: Int = -1,
            rpcPort: Int = -1,
            webPort: Int = -1,
            h2Port: Int = -1,
            services: List<String> = listOf("extra.service"),
            users: List<User> = listOf(user("guest"))
    ) = NodeConfig(
            baseDir,
            legalName = legalName,
            nearestCity = nearestCity,
            messagingPort = messagingPort,
            rpcPort = rpcPort,
            webPort = webPort,
            h2Port = h2Port,
            extraServices = services,
            users = users
    )

}
