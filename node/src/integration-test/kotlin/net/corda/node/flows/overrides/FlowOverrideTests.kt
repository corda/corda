package net.corda.node.flows.overrides

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.cordappForClasses
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertThat
import org.junit.Test

class FlowOverrideTests {

    companion object {
        const val PONG = "PONG"
        const val GORGONZOLA = "Gorgonzola"
    }

    @StartableByRPC
    @InitiatingFlow
    class Ping(private val pongParty: Party) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            val pongSession = initiateFlow(pongParty)
            return pongSession.sendAndReceive<String>("PING").unwrap { it }
        }
    }

    @InitiatedBy(Ping::class)
    open class Pong(private val pingSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            pingSession.send(PONG)
        }
    }

    @InitiatedBy(Ping::class)
    class Pongiest(private val pingSession: FlowSession) : Pong(pingSession) {
        @Suspendable
        override fun call() {
            pingSession.send(GORGONZOLA)
        }
    }

    private val nodeAClasses = setOf(Ping::class.java, Pong::class.java, Pongiest::class.java, FlowOverrideTests::class.java)
    private val nodeBClasses = setOf(Ping::class.java, Pong::class.java, FlowOverrideTests::class.java)

    @Test
    fun `should use the most specific implementation of a responding flow`() {
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = emptyList())) {
            val nodeAFuture = startNode(NodeParameters(
                    providedName = ALICE_NAME,
                    additionalCordapps = setOf(cordappForClasses(*nodeAClasses.toTypedArray()))
            ))
            val nodeBFuture = startNode(NodeParameters(
                    providedName = BOB_NAME,
                    additionalCordapps = setOf(cordappForClasses(*nodeBClasses.toTypedArray()))
            ))
            assertThat(nodeBFuture.get().rpc.startFlow(FlowOverrideTests::Ping, nodeAFuture.get().nodeInfo.singleIdentity()).returnValue.getOrThrow(), `is`(GORGONZOLA))
        }
    }

    @Test
    fun `should use the overriden implementation of a responding flow`() {
        val flowOverrides = mapOf(Ping::class.java to Pong::class.java)
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = emptyList())) {
            val nodeAFuture = startNode(NodeParameters(
                    providedName = ALICE_NAME,
                    additionalCordapps = setOf(cordappForClasses(*nodeAClasses.toTypedArray())),
                    flowOverrides = flowOverrides
            ))
            val nodeBFuture = startNode(NodeParameters(
                    providedName = BOB_NAME,
                    additionalCordapps = setOf(cordappForClasses(*nodeBClasses.toTypedArray()))
            ))
            assertThat(nodeBFuture.get().rpc.startFlow(FlowOverrideTests::Ping, nodeAFuture.get().nodeInfo.singleIdentity()).returnValue.getOrThrow(), `is`(PONG))
        }
    }

}