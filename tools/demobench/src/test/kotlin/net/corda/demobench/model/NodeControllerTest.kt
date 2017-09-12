package net.corda.demobench.model

import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.User
import net.corda.testing.DUMMY_NOTARY
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.*

class NodeControllerTest {

    private val baseDir: Path = Paths.get(".").toAbsolutePath()
    private val controller = NodeController({ _, _ -> })
    private val node1Name = "Organisation 1"
    private val organisation2Name = "Organisation 2"

    @Test
    fun `test unique nodes after validate`() {
        val data = NodeData()
        data.legalName.value = node1Name
        assertNotNull(controller.validate(data))
        assertNull(controller.validate(data))
    }

    @Test
    fun `test unique key after validate`() {
        val data = NodeData()
        data.legalName.value = node1Name

        assertFalse(controller.keyExists("organisation1"))
        controller.validate(data)
        assertTrue(controller.keyExists("organisation1"))
    }

    @Test
    fun `test matching name after validate`() {
        val data = NodeData()
        data.legalName.value = node1Name

        assertFalse(controller.nameExists("Organisation 1"))
        assertFalse(controller.nameExists("Organisation1"))
        assertFalse(controller.nameExists("organisation 1"))
        controller.validate(data)
        assertTrue(controller.nameExists("Organisation 1"))
        assertTrue(controller.nameExists("Organisation1"))
        assertTrue(controller.nameExists("organisation 1"))
    }

    @Test
    fun `test first validated node becomes network map`() {
        val data = NodeData()
        data.legalName.value = node1Name
        data.p2pPort.value = 100000

        assertFalse(controller.hasNetworkMap())
        controller.validate(data)
        assertTrue(controller.hasNetworkMap())
    }

    @Test
    fun `test register unique nodes`() {
        val config = createConfig(commonName = organisation2Name)
        assertTrue(controller.register(config))
        assertFalse(controller.register(config))
    }

    @Test
    fun `test unique key after register`() {
        val config = createConfig(commonName = organisation2Name)

        assertFalse(controller.keyExists("organisation2"))
        controller.register(config)
        assertTrue(controller.keyExists("organisation2"))
    }

    @Test
    fun `test matching name after register`() {
        val config = createConfig(commonName = organisation2Name)

        assertFalse(controller.nameExists("Organisation 2"))
        assertFalse(controller.nameExists("Organisation2"))
        assertFalse(controller.nameExists("organisation 2"))
        controller.register(config)
        assertTrue(controller.nameExists("Organisation 2"))
        assertTrue(controller.nameExists("Organisation2"))
        assertTrue(controller.nameExists("organisation 2"))
    }

    @Test
    fun `test register network map node`() {
        val config = createConfig(commonName = "Organisation is Network Map")
        assertTrue(config.isNetworkMap())

        assertFalse(controller.hasNetworkMap())
        controller.register(config)
        assertTrue(controller.hasNetworkMap())
    }

    @Test
    fun `test register non-network-map node`() {
        val config = createConfig(commonName = "Organisation is not Network Map")
        config.networkMap = NetworkMapConfig(DUMMY_NOTARY.name, 10000)
        assertFalse(config.isNetworkMap())

        assertFalse(controller.hasNetworkMap())
        controller.register(config)
        assertFalse(controller.hasNetworkMap())
    }

    @Test
    fun `test valid ports`() {
        assertFalse(controller.isPortValid(NodeController.minPort - 1))
        assertTrue(controller.isPortValid(NodeController.minPort))
        assertTrue(controller.isPortValid(NodeController.maxPort))
        assertFalse(controller.isPortValid(NodeController.maxPort + 1))
    }

    @Test
    fun `test P2P port is max`() {
        val portNumber = NodeController.firstPort + 1234
        val config = createConfig(p2pPort = portNumber)
        assertEquals(NodeController.firstPort, controller.nextPort)
        controller.register(config)
        assertEquals(portNumber + 1, controller.nextPort)
    }

    @Test
    fun `test rpc port is max`() {
        val portNumber = NodeController.firstPort + 7777
        val config = createConfig(rpcPort = portNumber)
        assertEquals(NodeController.firstPort, controller.nextPort)
        controller.register(config)
        assertEquals(portNumber + 1, controller.nextPort)
    }

    @Test
    fun `test web port is max`() {
        val portNumber = NodeController.firstPort + 2356
        val config = createConfig(webPort = portNumber)
        assertEquals(NodeController.firstPort, controller.nextPort)
        controller.register(config)
        assertEquals(portNumber + 1, controller.nextPort)
    }

    @Test
    fun `test H2 port is max`() {
        val portNumber = NodeController.firstPort + 3478
        val config = createConfig(h2Port = portNumber)
        assertEquals(NodeController.firstPort, controller.nextPort)
        controller.register(config)
        assertEquals(portNumber + 1, controller.nextPort)
    }

    @Test
    fun `dispose node`() {
        val config = createConfig(commonName = "MyName")
        controller.register(config)

        assertEquals(NodeState.STARTING, config.state)
        assertTrue(controller.keyExists("myname"))
        controller.dispose(config)
        assertEquals(NodeState.DEAD, config.state)
        assertTrue(controller.keyExists("myname"))
    }

    private fun createConfig(
            commonName: String = "Unknown",
            p2pPort: Int = -1,
            rpcPort: Int = -1,
            webPort: Int = -1,
            h2Port: Int = -1,
            services: MutableList<String> = mutableListOf("extra.service"),
            users: List<User> = listOf(user("guest"))
    ) = NodeConfig(
            baseDir,
            legalName = CordaX500Name(
                    organisation = commonName,
                    locality = "New York",
                    country = "US"
            ),
            p2pPort = p2pPort,
            rpcPort = rpcPort,
            webPort = webPort,
            h2Port = h2Port,
            extraServices = services,
            users = users
    )

}
