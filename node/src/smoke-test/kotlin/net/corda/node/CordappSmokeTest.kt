package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.getX500Name
import net.corda.nodeapi.User
import net.corda.smoketesting.NodeConfig
import net.corda.smoketesting.NodeProcess
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import kotlin.streams.toList

class CordappSmokeTest {
    private companion object {
        val user = User("user1", "test", permissions = setOf("ALL"))
        val port = AtomicInteger(15100)
    }

    private val factory = NodeProcess.Factory()

    private val aliceConfig = NodeConfig(
            legalName = getX500Name(O = "Alice Corp", L = "Madrid", C = "ES"),
            p2pPort = port.andIncrement,
            rpcPort = port.andIncrement,
            webPort = port.andIncrement,
            extraServices = emptyList(),
            users = listOf(user)
    )

    @Test
    fun `FlowContent appName returns the filename of the CorDapp jar`() {
        val pluginsDir = (factory.baseDirectory(aliceConfig) / "plugins").createDirectories()
        // Find the jar file for the smoke tests of this module
        val selfCorDapp = Paths.get("build", "libs").list {
            it.filter { "-smoke-test" in it.toString() }.toList().single()
        }
        selfCorDapp.copyToDirectory(pluginsDir)

        factory.create(aliceConfig).use { alice ->
            alice.connect().use { connectionToAlice ->
                val aliceIdentity = connectionToAlice.proxy.nodeIdentity().legalIdentity
                val future = connectionToAlice.proxy.startFlow(::DummyInitiatingFlow, aliceIdentity).returnValue
                assertThat(future.getOrThrow().appName).isEqualTo(selfCorDapp.fileName.toString().removeSuffix(".jar"))
            }
        }
    }

    @Test
    fun `empty plugins directory`() {
        (factory.baseDirectory(aliceConfig) / "plugins").createDirectories()
        factory.create(aliceConfig).close()
    }

    @InitiatingFlow
    @StartableByRPC
    class DummyInitiatingFlow(val otherParty: Party) : FlowLogic<FlowContext>() {
        @Suspendable
        override fun call() = getFlowContext(otherParty)
    }

    @Suppress("unused")
    @InitiatedBy(DummyInitiatingFlow::class)
    class DummyInitiatedFlow(val otherParty: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = Unit
    }
}
