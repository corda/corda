package net.corda.testing.node

import net.corda.core.getOrThrow
import net.corda.node.internal.Node
import net.corda.node.services.User
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.testing.freeLocalHostAndPort
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.*
import kotlin.concurrent.thread

/**
 * Extend this class if you need to run nodes in a test. You could use the driver DSL but it's extremely slow for testing
 * purposes.
 */
abstract class NodeBasedTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val nodes = ArrayList<Node>()
    lateinit var networkMapNode: Node

    @Before
    fun startNetworkMapNode() {
        networkMapNode = startNode("Network Map", emptyMap())
    }

    @After
    fun stopNodes() {
        nodes.forEach(Node::stop)
    }

    fun startNode(legalName: String, rpcUsers: List<User> = emptyList()): Node {
        return startNode(legalName, mapOf(
                "networkMapAddress" to networkMapNode.configuration.artemisAddress.toString(),
                "rpcUsers" to rpcUsers.map { mapOf(
                        "user" to it.username,
                        "password" to it.password,
                        "permissions" to it.permissions)
                }
        ))
    }

    private fun startNode(legalName: String, configOverrides: Map<String, Any>): Node {
        val config = ConfigHelper.loadConfig(
                baseDirectoryPath = tempFolder.newFolder(legalName).toPath(),
                allowMissingConfig = true,
                configOverrides = configOverrides + mapOf(
                        "myLegalName" to legalName,
                        "artemisAddress" to freeLocalHostAndPort().toString(),
                        "extraAdvertisedServiceIds" to ""
                )
        )

        val node = FullNodeConfiguration(config).createNode()
        node.start()
        nodes += node
        thread(name = legalName) {
            node.run()
        }
        node.networkMapRegistrationFuture.getOrThrow()
        return node
    }
}