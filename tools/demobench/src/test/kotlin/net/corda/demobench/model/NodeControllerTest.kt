package net.corda.demobench.model

import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.nodeapi.User
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.*

class NodeControllerTest {

    private val baseDir: Path = Paths.get(".").toAbsolutePath()
    private val controller = NodeController({ _, _ -> })

    @Test
    fun `test unique nodes after validate`() {
        val data = NodeData()
        data.legalName.value = "Node 1"
        assertNotNull(controller.validate(data))
        assertNull(controller.validate(data))
    }

    @Test
    fun `test unique key after validate`() {
        val data = NodeData()
        data.legalName.value = "Node 1"

        assertFalse(controller.keyExists("node1"))
        controller.validate(data)
        assertTrue(controller.keyExists("node1"))
    }

    @Test
    fun `test matching name after validate`() {
        val data = NodeData()
        data.legalName.value = "Node 1"

        assertFalse(controller.nameExists("Node 1"))
        assertFalse(controller.nameExists("Node1"))
        assertFalse(controller.nameExists("node 1"))
        controller.validate(data)
        assertTrue(controller.nameExists("Node 1"))
        assertTrue(controller.nameExists("Node1"))
        assertTrue(controller.nameExists("node 1"))
    }

    @Test
    fun `test first validated node becomes network map`() {
        val data = NodeData()
        data.legalName.value = "Node 1"
        data.p2pPort.value = 100000

        assertFalse(controller.hasNetworkMap())
        controller.validate(data)
        assertTrue(controller.hasNetworkMap())
    }

    @Test
    fun `test register unique nodes`() {
        val config = createConfig(legalName = "Node 2")
        assertTrue(controller.register(config))
        assertFalse(controller.register(config))
    }

    @Test
    fun `test unique key after register`() {
        val config = createConfig(legalName = "Node 2")

        assertFalse(controller.keyExists("node2"))
        controller.register(config)
        assertTrue(controller.keyExists("node2"))
    }

    @Test
    fun `test matching name after register`() {
        val config = createConfig(legalName = "Node 2")

        assertFalse(controller.nameExists("Node 2"))
        assertFalse(controller.nameExists("Node2"))
        assertFalse(controller.nameExists("node 2"))
        controller.register(config)
        assertTrue(controller.nameExists("Node 2"))
        assertTrue(controller.nameExists("Node2"))
        assertTrue(controller.nameExists("node 2"))
    }

    @Test
    fun `test register network map node`() {
        val config = createConfig(legalName = "Node is Network Map")
        assertTrue(config.isNetworkMap())

        assertFalse(controller.hasNetworkMap())
        controller.register(config)
        assertTrue(controller.hasNetworkMap())
    }

    @Test
    fun `test register non-network-map node`() {
        val config = createConfig(legalName = "Node is not Network Map")
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
        val config = createConfig(legalName = "MyName")
        controller.register(config)

        assertEquals(NodeState.STARTING, config.state)
        assertTrue(controller.keyExists("myname"))
        controller.dispose(config)
        assertEquals(NodeState.DEAD, config.state)
        assertTrue(controller.keyExists("myname"))
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

}
