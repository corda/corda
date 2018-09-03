package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.packageName
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.TestCorDapp
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals

class AsymmetricCorDappsTests : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName(), BOB_NAME.toDatabaseSchemaName(),
                DUMMY_BANK_A_NAME.toDatabaseSchemaName(), DUMMY_NOTARY_NAME.toDatabaseSchemaName())
    }

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
    fun noSharedCorDappsWithAsymmetricSpecificClasses() {

        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = emptySet())) {

            val nodeA = startNode(additionalCordapps = setOf(TestCorDapp.Factory.create("Szymon CorDapp", "1.0", classes = setOf(Ping::class.java)))).getOrThrow()
            val nodeB = startNode(additionalCordapps = setOf(TestCorDapp.Factory.create("Szymon CorDapp", "1.0", classes = setOf(Ping::class.java, Pong::class.java)))).getOrThrow()
            nodeA.rpc.startFlow(::Ping, nodeB.nodeInfo.singleIdentity(), 1).returnValue.getOrThrow()
        }
    }

    @Test
    fun sharedCorDappsWithAsymmetricSpecificClasses() {

        val resourceName = "cordapp.properties"
        val cordappPropertiesResource = this::class.java.getResource(resourceName)
        val sharedCordapp = TestCorDapp.Factory.create("shared", "1.0", classes = setOf(Ping::class.java)).plusResource("${AsymmetricCorDappsTests::class.java.packageName}.$resourceName", cordappPropertiesResource)
        val cordappForNodeB = TestCorDapp.Factory.create("nodeB_only", "1.0", classes = setOf(Pong::class.java))
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = setOf(sharedCordapp))) {

            val (nodeA, nodeB) = listOf(startNode(), startNode(additionalCordapps = setOf(cordappForNodeB))).transpose().getOrThrow()
            nodeA.rpc.startFlow(::Ping, nodeB.nodeInfo.singleIdentity(), 1).returnValue.getOrThrow()
        }
    }

    @Test
    fun sharedCorDappsWithAsymmetricSpecificClassesInProcess() {

        val resourceName = "cordapp.properties"
        val cordappPropertiesResource = this::class.java.getResource(resourceName)
        val sharedCordapp = TestCorDapp.Factory.create("shared", "1.0", classes = setOf(Ping::class.java)).plusResource("${AsymmetricCorDappsTests::class.java.packageName}.$resourceName", cordappPropertiesResource)
        val cordappForNodeB = TestCorDapp.Factory.create("nodeB_only", "1.0", classes = setOf(Pong::class.java))
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = setOf(sharedCordapp))) {

            val (nodeA, nodeB) = listOf(startNode(), startNode(additionalCordapps = setOf(cordappForNodeB))).transpose().getOrThrow()
            nodeA.rpc.startFlow(::Ping, nodeB.nodeInfo.singleIdentity(), 1).returnValue.getOrThrow()
        }
    }
}