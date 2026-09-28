package net.corda.coretests

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.config.User
import net.corda.smoketesting.NodeParams
import net.corda.smoketesting.NodeProcess
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries

class NodeVersioningTest {
    private companion object {
        val superUser = User("superUser", "test", permissions = setOf("ALL"))
        val port = AtomicInteger(15100)
    }

    private val factory = NodeProcess.Factory()

    private lateinit var notary: NodeProcess

    @Before
    fun startNotary() {
        notary = factory.createNotaries(NodeParams(
                legalName = CordaX500Name(organisation = "Notary Service", locality = "Zurich", country = "CH"),
                p2pPort = port.andIncrement,
                rpcPort = port.andIncrement,
                rpcAdminPort = port.andIncrement,
                users = listOf(superUser),
                // Find the jar file for the smoke tests of this module
                cordappJars = Path("build", "libs").listDirectoryEntries("*-smokeTests*")
        ))[0]
    }

    @After
    fun done() {
        factory.close()
    }

    @Test(timeout=300_000)
	fun `platform version in manifest file`() {
        val manifest = JarFile(NodeProcess.Factory.getCordaJar().toFile()).manifest
        assertThat(manifest.mainAttributes.getValue("Corda-Platform-Version").toInt()).isEqualTo(PLATFORM_VERSION)
    }

    @Test(timeout=300_000)
	fun `platform version from RPC`() {
        notary.connect(superUser).use {
            val rpc = it.proxy
            assertThat(rpc.protocolVersion).isEqualTo(PLATFORM_VERSION)
            assertThat(rpc.nodeInfo().platformVersion).isEqualTo(PLATFORM_VERSION)
            assertThat(rpc.startFlow(NodeVersioningTest::GetPlatformVersionFlow).returnValue.getOrThrow()).isEqualTo(PLATFORM_VERSION)
        }
    }

    @StartableByRPC
    class GetPlatformVersionFlow : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int = serviceHub.myInfo.platformVersion
    }
}
