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
import org.junit.Test
import kotlin.test.assertEquals

class AsymmetricCorDappsTests {

    @StartableByRPC
    @InitiatingFlow
    class Ping(private val pongParty: Party, val times: Int) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val pongSession = initiateFlow(pongParty)
            pongSession.sendAndReceive<Unit>(times)
            for (i in 1..times) {
                val j = pongSession.sendAndReceive<Int>(i).unwrap { it }
                assertEquals(i, j)
            }
        }
    }

    @InitiatedBy(Ping::class)
    class Pong(private val pingSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val times = pingSession.sendAndReceive<Int>(Unit).unwrap { it }
            for (i in 1..times) {
                val j = pingSession.sendAndReceive<Int>(i).unwrap { it }
                assertEquals(i, j)
            }
        }
    }

    @Test
    fun `no shared cordapps with asymmetric specific classes`() {
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode(NodeParameters(
                    providedName = ALICE_NAME,
                    additionalCordapps = setOf(cordappForClasses(Ping::class.java, AsymmetricCorDappsTests::class.java))
            )).getOrThrow()
            val nodeB = startNode(NodeParameters(
                    providedName = BOB_NAME,
                    additionalCordapps = setOf(cordappForClasses(Ping::class.java, Pong::class.java, AsymmetricCorDappsTests::class.java))
            )).getOrThrow()
            nodeA.rpc.startFlow(::Ping, nodeB.nodeInfo.singleIdentity(), 1).returnValue.getOrThrow()
        }
    }

    @Test
    fun `shared cordapps with asymmetric specific classes`() {
        val sharedCordapp = cordappForClasses(Ping::class.java, AsymmetricCorDappsTests::class.java)
        val cordappForNodeB = cordappForClasses(Pong::class.java, AsymmetricCorDappsTests::class.java)
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = setOf(sharedCordapp))) {
            val (nodeA, nodeB) = listOf(
                    startNode(NodeParameters(providedName = ALICE_NAME)),
                    startNode(NodeParameters(providedName = BOB_NAME, additionalCordapps = setOf(cordappForNodeB)))
            ).transpose().getOrThrow()
            nodeA.rpc.startFlow(::Ping, nodeB.nodeInfo.singleIdentity(), 1).returnValue.getOrThrow()
        }
    }

    @Test
    fun `shared cordapps with asymmetric specific classes in process`() {
        val sharedCordapp = cordappForClasses(Ping::class.java, AsymmetricCorDappsTests::class.java)
        val cordappForNodeB = cordappForClasses(Pong::class.java, AsymmetricCorDappsTests::class.java)
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = setOf(sharedCordapp))) {
            val (nodeA, nodeB) = listOf(
                    startNode(NodeParameters(providedName = ALICE_NAME)),
                    startNode(NodeParameters(providedName = BOB_NAME, additionalCordapps = setOf(cordappForNodeB)))
            ).transpose().getOrThrow()
            nodeA.rpc.startFlow(::Ping, nodeB.nodeInfo.singleIdentity(), 1).returnValue.getOrThrow()
        }
    }
}