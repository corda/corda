package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
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
        companion object {
            const val PONG = "PONG"
        }

        @Suspendable
        override fun call() {
            pingSession.send(PONG)
        }
    }

    @InitiatedBy(Ping::class)
    class Pong2(private val pingSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            pingSession.send("PONGPONG")
        }
    }

    @InitiatedBy(Ping::class)
    class Pongiest(private val pingSession: FlowSession) : Pong(pingSession) {

        companion object {
            const val GORGONZOLA = "Gorgonzola"
        }

        @Suspendable
        override fun call() {
            pingSession.send(GORGONZOLA)
        }
    }

    private val nodeAClasses = setOf(Ping::class.java, Pong::class.java, Pongiest::class.java)
    private val nodeBClasses = setOf(Ping::class.java, Pong::class.java)

    @Test(timeout = 300_000)
    fun `should use the most specific implementation of a responding flow`() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val (nodeA, nodeB) = listOf(ALICE_NAME, BOB_NAME)
                    .map {
                        NodeParameters(providedName = it,
                                additionalCordapps = setOf(cordappForClasses(*nodeAClasses.toTypedArray())))
                    }
                    .map { startNode(it) }
                    .transpose()
                    .getOrThrow()
            assertThat(nodeB.rpc.startFlow(::Ping, nodeA.nodeInfo.singleIdentity()).returnValue.getOrThrow(), `is`(Pongiest.GORGONZOLA))
        }
    }

    @Test(timeout = 300_000)
    fun `should use the overriden implementation of a responding flow`() {
        val flowOverrides = mapOf(Ping::class.java to Pong::class.java)
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val (nodeA, nodeB) = listOf(ALICE_NAME, BOB_NAME)
                    .map {
                        NodeParameters(providedName = it,
                                flowOverrides = flowOverrides,
                                additionalCordapps = setOf(cordappForClasses(*nodeAClasses.toTypedArray())))
                    }
                    .map { startNode(it) }
                    .transpose()
                    .getOrThrow()
            assertThat(nodeB.rpc.startFlow(::Ping, nodeA.nodeInfo.singleIdentity()).returnValue.getOrThrow(), `is`(Pong.PONG))
        }
    }
}