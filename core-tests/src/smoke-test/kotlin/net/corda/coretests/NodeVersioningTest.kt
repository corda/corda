package net.corda.coretests

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.config.User
import net.corda.smoketesting.NodeConfig
import net.corda.smoketesting.NodeProcess
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarFile
import kotlin.streams.toList

class NodeVersioningTest {
    private companion object {
        val superUser = User("superUser", "test", permissions = setOf("ALL"))
        val port = AtomicInteger(15100)
    }

    private val factory = NodeProcess.Factory()

    private val notaryConfig = NodeConfig(
            legalName = CordaX500Name(organisation = "Notary Service", locality = "Zurich", country = "CH"),
            p2pPort = port.andIncrement,
            rpcPort = port.andIncrement,
            rpcAdminPort = port.andIncrement,
            isNotary = true,
            users = listOf(superUser)
    )

    private val aliceConfig = NodeConfig(
            legalName = CordaX500Name(organisation = "Alice Corp", locality = "Madrid", country = "ES"),
            p2pPort = port.andIncrement,
            rpcPort = port.andIncrement,
            rpcAdminPort = port.andIncrement,
            isNotary = false,
            users = listOf(superUser)
    )

    private lateinit var notary: NodeProcess

    @Before
    fun setUp() {
        notary = factory.create(notaryConfig)
    }

    @After
    fun done() {
        notary.close()
    }

    @Test
    fun `platform version in manifest file`() {
        val manifest = JarFile(factory.cordaJar.toFile()).manifest
        assertThat(manifest.mainAttributes.getValue("Corda-Platform-Version").toInt()).isEqualTo(PLATFORM_VERSION)
    }

    @Test
    fun `platform version from RPC`() {
        val cordappsDir = (factory.baseDirectory(aliceConfig) / NodeProcess.CORDAPPS_DIR_NAME).createDirectories()
        // Find the jar file for the smoke tests of this module
        val selfCordapp = Paths.get("build", "libs").list {
            it.filter { "-smokeTests" in it.toString() }.toList().single()
        }
        selfCordapp.copyToDirectory(cordappsDir)

        factory.create(aliceConfig).use { alice ->
            alice.connect(superUser).use {
                val rpc = it.proxy
                assertThat(rpc.protocolVersion).isEqualTo(PLATFORM_VERSION)
                assertThat(rpc.nodeInfo().platformVersion).isEqualTo(PLATFORM_VERSION)
                assertThat(rpc.startFlow(NodeVersioningTest::GetPlatformVersionFlow).returnValue.getOrThrow()).isEqualTo(PLATFORM_VERSION)
            }
        }
    }

    @StartableByRPC
    class GetPlatformVersionFlow : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int = serviceHub.myInfo.platformVersion
    }
}
