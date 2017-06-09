package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.Futures
import net.corda.core.copyToDirectory
import net.corda.core.createDirectories
import net.corda.core.div
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.unwrap
import net.corda.node.driver.driver
import net.corda.node.services.startFlowPermission
import net.corda.nodeapi.User
import net.corda.smoketesting.NodeConfig
import net.corda.smoketesting.NodeProcess
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

class CordappScanningTest {
    private companion object {
        val user = User("user1", "test", permissions = setOf("ALL"))
        val port = AtomicInteger(15100)
    }

    private val factory = NodeProcess.Factory()

    private val aliceConfig = NodeConfig(
            party = ALICE,
            p2pPort = port.andIncrement,
            rpcPort = port.andIncrement,
            webPort = port.andIncrement,
            extraServices = emptyList(),
            users = listOf(user)
    )

    @Test
    fun `CorDapp jar in plugins directory is scanned`() {
        // If the CorDapp jar does't exist then run the smokeTestClasses gradle task
        val cordappJar = Paths.get(javaClass.getResource("/trader-demo.jar").toURI())
        val pluginsDir = (factory.baseDirectory(aliceConfig) / "plugins").createDirectories()
        cordappJar.copyToDirectory(pluginsDir)

        factory.create(aliceConfig).use {
            it.connect().use {
                // If the CorDapp wasn't scanned then SellerFlow won't have been picked up as an RPC flow
                assertThat(it.proxy.registeredFlows()).contains("net.corda.traderdemo.flow.SellerFlow")
            }
        }
    }

    @Test
    fun `empty plugins directory`() {
        (factory.baseDirectory(aliceConfig) / "plugins").createDirectories()
        factory.create(aliceConfig).close()
    }

    @Test
    fun `sub-classed initiated flow pointing to the same initiating flow as its super-class`() {
        val user = User("u", "p", setOf(startFlowPermission<ReceiveFlow>()))
        // We don't use the factory for this test because we want the node to pick up the annotated flows below. The driver
        // will do just that.
        driver {
            val (alice, bob) = Futures.allAsList(
                    startNode(ALICE.name, rpcUsers = listOf(user)),
                    startNode(BOB.name)).getOrThrow()
            val initiatedFlowClass = alice.rpcClientToNode()
                    .start(user.username, user.password)
                    .proxy
                    .startFlow(::ReceiveFlow, bob.nodeInfo.legalIdentity)
                    .returnValue
            assertThat(initiatedFlowClass.getOrThrow()).isEqualTo(SendSubClassFlow::class.java.name)
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class ReceiveFlow(val otherParty: Party) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String = receive<String>(otherParty).unwrap { it }
    }

    @InitiatedBy(ReceiveFlow::class)
    open class SendClassFlow(val otherParty: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = send(otherParty, javaClass.name)
    }

    @InitiatedBy(ReceiveFlow::class)
    class SendSubClassFlow(otherParty: Party) : SendClassFlow(otherParty)
}
