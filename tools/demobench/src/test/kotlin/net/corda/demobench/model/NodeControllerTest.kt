package net.corda.demobench.model

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.User
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.*

class NodeControllerTest {

    private val baseDir: Path = Paths.get(".").toAbsolutePath()
    private val controller = NodeController { _, _ -> }
    private val node1Name = "Organisation 1"
    private val organisation2Name = "Organisation 2"

    @Test(timeout=300_000)
	fun `test unique nodes after validate`() {
        val data = NodeData()
        data.legalName.value = node1Name
        assertNotNull(controller.validate(data))
        assertNull(controller.validate(data))
    }

    @Test(timeout=300_000)
	fun `register notary`() {
        assertFalse(controller.hasNotary())
        val config = createConfig(organisation = "Name", notary = NotaryService(false))
        controller.register(config)
        assertTrue(controller.hasNotary())
    }

    @Test(timeout=300_000)
	fun `register non notary`() {
        assertFalse(controller.hasNotary())
        val config = createConfig(organisation = "Name")
        controller.register(config)
        assertFalse(controller.hasNotary())
    }

    @Test(timeout=300_000)
	fun `test unique key after validate`() {
        val data = NodeData()
        data.legalName.value = node1Name

        assertFalse(controller.keyExists("organisation1"))
        controller.validate(data)
        assertTrue(controller.keyExists("organisation1"))
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun `test node system properties`() {
        val data = NodeData()
        data.legalName.value = node1Name

        val wrapper = controller.validate(data) ?: fail("No wrapped configuration!")
        val systemProperties = wrapper.nodeConfig.systemProperties
        assertFalse(systemProperties["co.paralleluniverse.fibers.verifyInstrumentation"] as Boolean)
    }

    @Test(timeout=300_000)
	fun `test register unique nodes`() {
        val config = createConfig(organisation = organisation2Name)
        assertTrue(controller.register(config))
        assertFalse(controller.register(config))
    }

    @Test(timeout=300_000)
	fun `test unique key after register`() {
        val config = createConfig(organisation = organisation2Name)
        assertFalse(controller.keyExists("organisation2"))
        controller.register(config)
        assertTrue(controller.keyExists("organisation2"))
    }

    @Test(timeout=300_000)
	fun `test matching name after register`() {
        val config = createConfig(organisation = organisation2Name)
        assertFalse(controller.nameExists("Organisation 2"))
        assertFalse(controller.nameExists("Organisation2"))
        assertFalse(controller.nameExists("organisation 2"))
        controller.register(config)
        assertTrue(controller.nameExists("Organisation 2"))
        assertTrue(controller.nameExists("Organisation2"))
        assertTrue(controller.nameExists("organisation 2"))
    }

    @Test(timeout=300_000)
	fun `test valid ports`() {
        assertFalse(controller.isPortValid(NodeController.minPort - 1))
        assertTrue(controller.isPortValid(NodeController.minPort))
        assertTrue(controller.isPortValid(NodeController.maxPort))
        assertFalse(controller.isPortValid(NodeController.maxPort + 1))
    }

    @Test(timeout=300_000)
	fun `test P2P port is max`() {
        val portNumber = NodeController.firstPort + 1234
        val config = createConfig(p2pPort = portNumber)
        assertEquals(NodeController.firstPort, controller.nextPort)
        controller.register(config)
        assertEquals(portNumber + 1, controller.nextPort)
    }

    @Test(timeout=300_000)
	fun `test rpc port is max`() {
        val portNumber = NodeController.firstPort + 7777
        val config = createConfig(rpcPort = portNumber)
        assertEquals(NodeController.firstPort, controller.nextPort)
        controller.register(config)
        assertEquals(portNumber + 1, controller.nextPort)
    }

    @Test(timeout=300_000)
	fun `test web port is max`() {
        val portNumber = NodeController.firstPort + 2356
        val config = createConfig(webPort = portNumber)
        assertEquals(NodeController.firstPort, controller.nextPort)
        controller.register(config)
        assertEquals(portNumber + 1, controller.nextPort)
    }

    @Test(timeout=300_000)
	fun `test H2 port is max`() {
        val portNumber = NodeController.firstPort + 3478
        val config = createConfig(h2port = portNumber)
        assertEquals(NodeController.firstPort, controller.nextPort)
        controller.register(config)
        assertEquals(portNumber + 1, controller.nextPort)
    }

    @Test(timeout=300_000)
	fun `dispose node`() {
        val config = createConfig(organisation = "MyName")
        controller.register(config)

        assertEquals(NodeState.STARTING, config.state)
        assertTrue(controller.keyExists("myname"))
        controller.dispose(config)
        assertEquals(NodeState.DEAD, config.state)
        assertTrue(controller.keyExists("myname"))
    }

    @Suppress("LongParameterList")
    private fun createConfig(
            organisation: String = "Unknown",
            p2pPort: Int = 0,
            rpcPort: Int = 0,
            rpcAdminPort: Int = 0,
            webPort: Int = 0,
            h2port: Int = 0,
            notary: NotaryService? = null,
            users: List<User> = listOf(user("guest")),
            systemProperties: Map<String, Any?> = emptyMap()
    ): NodeConfigWrapper {
        val nodeConfig = NodeConfig(
                myLegalName = CordaX500Name(
                        organisation = organisation,
                        locality = "New York",
                        country = "US"
                ),
                p2pAddress = localPort(p2pPort),
                rpcSettings = NodeRpcSettings(
                        address = localPort(rpcPort),
                        adminAddress = localPort(rpcAdminPort)
                ),
                webAddress = localPort(webPort),
                h2port = h2port,
                notary = notary,
                rpcUsers = users,
                systemProperties = systemProperties
        )
        return NodeConfigWrapper(baseDir, nodeConfig)
    }

    private fun localPort(port: Int) = NetworkHostAndPort("localhost", port)
}
